package org.ferreiratechlab.leitordepdfseguro.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;

import java.io.File;
import java.util.List;

public class PdfEncryptionTask extends AsyncTask<Void, Integer, Boolean> {
    
    public static class EncryptionItem {
        public final File tempFile;
        public final Uri originalUri;

        public EncryptionItem(File tempFile, Uri originalUri) {
            this.tempFile = tempFile;
            this.originalUri = originalUri;
        }
    }

    private Context context;
    private List<EncryptionItem> itemsToEncrypt;
    private AppDatabase db;
    private ProgressDialog progressDialog;
    private boolean overwriteConfirmed = false;
    private String fileToOverwrite;
    private int currentIndex = -1;

    public PdfEncryptionTask(Context context, List<EncryptionItem> itemsToEncrypt, AppDatabase db) {
        this.context = context;
        this.itemsToEncrypt = itemsToEncrypt;
        this.db = db;
    }

    @Override
    protected void onPreExecute() {
        progressDialog = new ProgressDialog(context, org.ferreiratechlab.leitordepdfseguro.R.style.CustomDialogTheme);
        progressDialog.setMessage(context.getString(R.string.encrypting_pdfs));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(itemsToEncrypt.size());
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            for (int i = 0; i < itemsToEncrypt.size(); i++) {
                currentIndex = i;
                EncryptionItem item = itemsToEncrypt.get(i);
                File file = item.tempFile;

                // Verificar se o PDF já existe no banco de dados
                Pdf existingPdf = db.pdfDao().getPdfByFilename(file.getName());
                if (existingPdf != null) {
                    // Se já existe, armazenar o nome do arquivo para sobrescrever
                    fileToOverwrite = file.getName();
                    publishProgress(-1); // Informar que precisamos exibir o diálogo de sobrescrever
                    synchronized (this) {
                        try {
                            this.wait(); // Aguardar até que o usuário responda ao diálogo
                        } catch (InterruptedException e) {
                            org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils.logErrorDebug("PdfEncryption", e);
                        }
                    }
                    if (!overwriteConfirmed) {
                        continue; // Ignorar este arquivo e continuar com o próximo
                    }
                }

                // Diretório onde os arquivos criptografados serão salvos
                File directory = new File(context.getExternalFilesDir(null), "EncryptedPDFs");
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                // Criar um novo arquivo para o PDF criptografado
                File encryptedFile = new File(directory, file.getName() + ".enc");

                // Criptografar o arquivo temporário e salvar no arquivo criptografado
                EncryptionUtils.encryptFile(context, file, encryptedFile);
                org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils.logPdfEncryptionCompleted(file.getName());

                // Apagar o arquivo original e o temporário de forma segura
                try {
                    EncryptionUtils.secureDelete(context, item.originalUri);
                } catch (Exception e) {
                    org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils.logErrorDebug("PdfEncryption", e);
                    // Fallback: se falhar zero-fill, avisar usuário mas continuar
                    org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils.logOriginalFileZeroFillFailed(e.getClass().getSimpleName());
                }
                try {
                    EncryptionUtils.secureDelete(item.tempFile);
                } catch (Exception e) {
                    org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils.logErrorDebug("PdfEncryption", e);
                }

                // Verificar novamente se o PDF já existe no banco de dados para evitar duplicação
                existingPdf = db.pdfDao().getPdfByFilename(file.getName());
                if (existingPdf == null){
                    // Salvar no banco de dados
                    Pdf pdf = new Pdf(encryptedFile.getAbsolutePath(), file.getName());
                    db.pdfDao().insertAll(pdf);
                }



                publishProgress(i + 1);
            }
            return true;
        } catch (Exception e) {
            org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils.logErrorDebug("PdfEncryption", e);
            return false;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (values.length > 0 && values[0] == -1) {
            showOverwriteDialog(fileToOverwrite);
        } else {
            progressDialog.setProgress(values[0]);
        }
    }

    private void showOverwriteDialog(String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, org.ferreiratechlab.leitordepdfseguro.R.style.CustomDialogTheme);
        builder.setTitle(R.string.existing_file_title);
        builder.setMessage(context.getString(R.string.overwrite_msg, fileName));
        builder.setPositiveButton(R.string.overwrite, (dialog, which) -> {
            overwriteConfirmed = true;
            synchronized (PdfEncryptionTask.this) {
                PdfEncryptionTask.this.notify(); // Notificar o doInBackground que o usuário concordou
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            overwriteConfirmed = false;
            synchronized (PdfEncryptionTask.this) {
                PdfEncryptionTask.this.notify(); // Notificar o doInBackground que o usuário cancelou
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        progressDialog.dismiss();
        if (result) {
            Toast.makeText(context, R.string.pdf_encrypted_success, Toast.LENGTH_SHORT).show();
            ((MainActivity) context).updatePdfListFromDatabase();
        } else {
            Toast.makeText(context, R.string.pdf_encryption_error, Toast.LENGTH_SHORT).show();
        }
    }
}
