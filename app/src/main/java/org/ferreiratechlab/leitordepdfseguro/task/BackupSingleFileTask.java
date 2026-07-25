package org.ferreiratechlab.leitordepdfseguro.task;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Descriptografa um único PDF em background (usando {@link AppExecutors}) para a
 * pasta de cache privada do app e entrega o resultado via Share Sheet do Android,
 * em vez de deixar uma cópia em texto puro permanente em armazenamento público.
 * A pasta de cache é limpa a cada abertura do app (ver MainActivity#cleanOldTempFiles).
 */
public class BackupSingleFileTask {
    private final Context appContext;
    private final WeakReference<Activity> activityRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Uri pdfUri;
    private final String displayTitle;
    private ProgressDialog progressDialog;
    private File decryptedFile;

    public BackupSingleFileTask(Activity activity, Uri pdfUri, String displayTitle) {
        this.appContext = activity.getApplicationContext();
        this.activityRef = new WeakReference<>(activity);
        this.pdfUri = pdfUri;
        this.displayTitle = displayTitle;
    }

    private boolean isActivityAlive(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    public void start() {
        onPreExecute();
        AppExecutors.background().execute(() -> {
            boolean result;
            try {
                result = doInBackground();
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("BackupSingleFile", e);
                result = false;
            }
            boolean finalResult = result;
            mainHandler.post(() -> onPostExecute(finalResult));
        });
    }

    private void onPreExecute() {
        Activity activity = activityRef.get();
        if (!isActivityAlive(activity)) {
            return;
        }
        progressDialog = new ProgressDialog(activity, R.style.CustomDialogTheme);
        progressDialog.setMessage(appContext.getString(R.string.backup_progress));
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private boolean doInBackground() {
        try {
            String filePath = pdfUri.getPath();
            if (filePath != null) {
                File encryptedFile = new File(filePath);
                File shareDir = new File(appContext.getCacheDir(), "ShareBackups");
                if (!shareDir.exists()) {
                    shareDir.mkdirs();
                }
                // Nome no disco é opaco (UUID); o nome de exibição já decifrado é quem
                // nomeia o arquivo exportado. O título já inclui ".pdf" (nome original do
                // arquivo importado), então não adiciona de novo.
                String exportFileName = displayTitle.toLowerCase().endsWith(".pdf") ? displayTitle : displayTitle + ".pdf";
                decryptedFile = new File(shareDir, exportFileName);
                EncryptionUtils.decryptFile(appContext, encryptedFile, decryptedFile);
                return true;
            }
        } catch (Exception e) {
            LoggingUtils.logErrorDebug("BackupSingleFile", e);
        }
        return false;
    }

    private void onPostExecute(boolean result) {
        Activity activity = activityRef.get();
        if (!isActivityAlive(activity)) {
            return;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (result && decryptedFile != null) {
            shareDecryptedFile(activity, decryptedFile);
        } else {
            Toast.makeText(activity, R.string.backup_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareDecryptedFile(Activity activity, File file) {
        Uri contentUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.backup_success_single)));
    }
}
