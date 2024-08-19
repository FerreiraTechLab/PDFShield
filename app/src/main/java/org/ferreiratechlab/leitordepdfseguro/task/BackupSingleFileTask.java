package org.ferreiratechlab.leitordepdfseguro.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.widget.Toast;

import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;

import java.io.File;

public class BackupSingleFileTask extends AsyncTask<Void, Void, Boolean> {
    private Context context;
    private Uri pdfUri;
    private ProgressDialog progressDialog;

    public BackupSingleFileTask(Context context, Uri pdfUri) {
        this.context = context;
        this.pdfUri = pdfUri;
    }

    @Override
    protected void onPreExecute() {
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Realizando backup do PDF...");
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            String filePath = pdfUri.getPath();
            if (filePath != null) {
                File encryptedFile = new File(filePath);
                String backupDirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath() + "/BackupPDFs";
                File backupDir = new File(backupDirPath);
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }
                // Remover a extensão .enc se estiver presente e adicionar .pdf
                String decryptedFileName = encryptedFile.getName();
                if (decryptedFileName.endsWith(".enc")) {
                    decryptedFileName = decryptedFileName.substring(0, decryptedFileName.length() - 4);
                }
                decryptedFileName += ".pdf";
                File decryptedFile = new File(backupDir, decryptedFileName);
                EncryptionUtils.decryptFile(context, encryptedFile, decryptedFile);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        progressDialog.dismiss();
        if (result) {
            Toast.makeText(context, "Backup realizado com sucesso", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Erro ao realizar o backup", Toast.LENGTH_SHORT).show();
        }
    }
}
