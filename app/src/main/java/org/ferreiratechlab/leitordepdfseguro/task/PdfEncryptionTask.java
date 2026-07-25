package org.ferreiratechlab.leitordepdfseguro.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

/**
 * Criptografa uma lista de PDFs em background usando um Executor compartilhado
 * (ver {@link AppExecutors}), postando as atualizações de progresso e o
 * resultado final na main thread via {@link Handler}.
 */
public class PdfEncryptionTask {

    public static class EncryptionItem {
        public final File tempFile;
        public final Uri originalUri;

        public EncryptionItem(File tempFile, Uri originalUri) {
            this.tempFile = tempFile;
            this.originalUri = originalUri;
        }
    }

    private final Context appContext;
    private final WeakReference<MainActivity> activityRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<EncryptionItem> itemsToEncrypt;
    private final AppDatabase db;
    private ProgressDialog progressDialog;
    private final Object dialogLock = new Object();
    private Boolean overwriteAnswer = null;
    private volatile boolean lifecycleCancelled = false;
    private String fileToOverwrite;

    public PdfEncryptionTask(MainActivity activity, List<EncryptionItem> itemsToEncrypt, AppDatabase db) {
        this.appContext = activity.getApplicationContext();
        this.activityRef = new WeakReference<>(activity);
        this.itemsToEncrypt = itemsToEncrypt;
        this.db = db;
    }

    /**
     * Chamado pela Activity quando ela está sendo destruída, para liberar a thread de
     * background caso ela esteja bloqueada aguardando resposta do diálogo de sobrescrita.
     */
    public void cancelDueToLifecycle() {
        synchronized (dialogLock) {
            lifecycleCancelled = true;
            dialogLock.notifyAll();
        }
    }

    private boolean isActivityAlive(MainActivity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    public void start() {
        onPreExecute();
        AppExecutors.background().execute(() -> {
            boolean result;
            try {
                result = doInBackground();
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("PdfEncryption", e);
                result = false;
            }
            boolean finalResult = result;
            mainHandler.post(() -> onPostExecute(finalResult));
        });
    }

    private void onPreExecute() {
        MainActivity activity = activityRef.get();
        if (!isActivityAlive(activity)) {
            return;
        }
        progressDialog = new ProgressDialog(activity, R.style.CustomDialogTheme);
        progressDialog.setMessage(appContext.getString(R.string.encrypting_pdfs));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(itemsToEncrypt.size());
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private boolean doInBackground() {
        try {
            for (int i = 0; i < itemsToEncrypt.size(); i++) {
                EncryptionItem item = itemsToEncrypt.get(i);
                File file = item.tempFile;
                String displayName = file.getName();

                // Verificar se o PDF já existe no banco de dados. O título é criptografado
                // (Pdf#metadataVersion == 2) ou ainda texto puro (== 1, pré-migração) — não dá
                // mais para comparar via SQL (IV aleatório nunca bate), então decifra e compara
                // em memória. Feito uma vez por arquivo do lote (não cacheado antes do loop),
                // para detectar corretamente dois arquivos de mesmo nome no mesmo lote.
                Pdf existingPdf = findExistingByTitle(displayName);
                if (existingPdf != null) {
                    // Se já existe, armazenar o nome do arquivo para sobrescrever
                    fileToOverwrite = displayName;
                    overwriteAnswer = null;
                    postProgress(-1); // Informar que precisamos exibir o diálogo de sobrescrever
                    synchronized (dialogLock) {
                        while (overwriteAnswer == null && !lifecycleCancelled) {
                            try {
                                dialogLock.wait(); // Aguardar até que o usuário responda ao diálogo
                            } catch (InterruptedException e) {
                                LoggingUtils.logErrorDebug("PdfEncryption", e);
                                Thread.currentThread().interrupt();
                                lifecycleCancelled = true;
                            }
                        }
                    }
                    if (lifecycleCancelled) {
                        // Activity foi destruída enquanto aguardávamos resposta: abortar a tarefa inteira.
                        return false;
                    }
                    if (!Boolean.TRUE.equals(overwriteAnswer)) {
                        continue; // Ignorar este arquivo e continuar com o próximo
                    }
                }

                // Diretório onde os arquivos criptografados serão salvos
                File directory = new File(appContext.getExternalFilesDir(null), "EncryptedPDFs");
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                SecretKey dek = SessionKeyHolder.require();
                String encryptedTitle = EncryptionUtils.encryptString(dek, displayName);

                if (existingPdf != null) {
                    // Sobrescrever: criptografa num arquivo temporário e troca pelo arquivo
                    // existente de forma atômica, nunca escrevendo direto em cima do caminho ao
                    // vivo (que pode estar sendo lido nesse instante por MainActivity#decryptPDF).
                    File existingFile = new File(existingPdf.uri);
                    File tempEncrypted = File.createTempFile("overwrite_", ".enc", directory);
                    EncryptionUtils.encryptFileWithKey(dek, file, tempEncrypted);
                    EncryptionUtils.replaceFileAtomically(tempEncrypted, existingFile);

                    existingPdf.title = encryptedTitle;
                    existingPdf.keyVersion = 2;
                    existingPdf.metadataVersion = 2;
                    db.pdfDao().update(existingPdf);
                } else {
                    // Nome opaco no disco: não revela o nome original do PDF.
                    File encryptedFile = new File(directory, UUID.randomUUID() + ".enc");
                    EncryptionUtils.encryptFileWithKey(dek, file, encryptedFile);

                    Pdf pdf = new Pdf(encryptedFile.getAbsolutePath(), encryptedTitle);
                    pdf.keyVersion = 2;
                    pdf.metadataVersion = 2;
                    db.pdfDao().insertAll(pdf);
                }
                LoggingUtils.logPdfEncryptionCompleted(displayName);

                // Apagar o arquivo original e o temporário de forma segura
                try {
                    EncryptionUtils.secureDelete(appContext, item.originalUri);
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("PdfEncryption", e);
                    // Fallback: se falhar zero-fill, avisar usuário mas continuar
                    LoggingUtils.logOriginalFileZeroFillFailed(e.getClass().getSimpleName());
                }
                try {
                    EncryptionUtils.secureDelete(item.tempFile);
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("PdfEncryption", e);
                }

                postProgress(i + 1);
            }
            return true;
        } catch (Exception e) {
            LoggingUtils.logErrorDebug("PdfEncryption", e);
            return false;
        }
    }

    /**
     * Procura um PDF já salvo com o mesmo nome de exibição, decifrando o título das linhas já
     * migradas (metadataVersion == 2) para comparar. Linhas metadataVersion == 1 ainda estão em
     * texto puro (pré-migração). Roda a cada arquivo do lote para pegar duplicatas dentro do
     * próprio lote sendo importado.
     */
    private Pdf findExistingByTitle(String displayName) {
        for (Pdf candidate : db.pdfDao().getAll()) {
            String decryptedTitle = candidate.title;
            if (candidate.metadataVersion == 2) {
                try {
                    decryptedTitle = EncryptionUtils.decryptString(SessionKeyHolder.require(), candidate.title);
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("PdfEncryption", e);
                    continue;
                }
            }
            if (displayName.equals(decryptedTitle)) {
                return candidate;
            }
        }
        return null;
    }

    private void postProgress(int value) {
        mainHandler.post(() -> onProgressUpdate(value));
    }

    private void onProgressUpdate(int value) {
        MainActivity activity = activityRef.get();
        if (!isActivityAlive(activity)) {
            // Activity sumiu antes de podermos exibir o diálogo: libera a espera em doInBackground.
            cancelDueToLifecycle();
            return;
        }
        if (value == -1) {
            showOverwriteDialog(activity, fileToOverwrite);
        } else if (progressDialog != null) {
            progressDialog.setProgress(value);
        }
    }

    private void showOverwriteDialog(MainActivity activity, String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomDialogTheme);
        builder.setTitle(R.string.existing_file_title);
        builder.setMessage(activity.getString(R.string.overwrite_msg, fileName));
        builder.setPositiveButton(R.string.overwrite, (dialog, which) -> {
            synchronized (dialogLock) {
                overwriteAnswer = true;
                dialogLock.notifyAll(); // Notificar o doInBackground que o usuário concordou
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            synchronized (dialogLock) {
                overwriteAnswer = false;
                dialogLock.notifyAll(); // Notificar o doInBackground que o usuário cancelou
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void onPostExecute(boolean result) {
        MainActivity activity = activityRef.get();
        if (!isActivityAlive(activity)) {
            return;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (result) {
            Toast.makeText(activity, R.string.pdf_encrypted_success, Toast.LENGTH_SHORT).show();
            activity.updatePdfListFromDatabase();
        } else {
            Toast.makeText(activity, R.string.pdf_encryption_error, Toast.LENGTH_SHORT).show();
        }
    }
}
