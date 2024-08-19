package org.ferreiratechlab.leitordepdfseguro.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;

import java.io.File;
import java.util.List;

public class PdfEncryptionTask extends AsyncTask<Void, Integer, Boolean> {
    private Context context;
    private List<File> filesToEncrypt;
    private AppDatabase db;
    private ProgressDialog progressDialog;
    private boolean overwriteConfirmed = false;
    private String fileToOverwrite;
    private int currentIndex = -1;

    public PdfEncryptionTask(Context context, List<File> filesToEncrypt, AppDatabase db) {
        this.context = context;
        this.filesToEncrypt = filesToEncrypt;
        this.db = db;
    }

    @Override
    protected void onPreExecute() {
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Criptografando PDFs...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(filesToEncrypt.size());
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            for (int i = 0; i < filesToEncrypt.size(); i++) {
                currentIndex = i;
                File file = filesToEncrypt.get(i);

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
                            e.printStackTrace();
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

                // Apagar o arquivo original
                String realPath = getRealPathFromURI(Uri.fromFile(file));
                if (realPath != null) {
                    File originalFile = new File(realPath);
                    if (originalFile.exists()) {
                        boolean deleted = originalFile.delete();
                        if (!deleted) {
                            System.out.println("Falha ao deletar arquivo: " + originalFile.getAbsolutePath());
                        }
                    }
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
            e.printStackTrace();
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

    private String getRealPathFromURI(Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return uri.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            return cursor.getString(idx);
        }
    }

    private void showOverwriteDialog(String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Arquivo Existente");
        builder.setMessage("O arquivo '" + fileName + "' já existe. Deseja sobrescrevê-lo?");
        builder.setPositiveButton("Sobrescrever", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                overwriteConfirmed = true;
                synchronized (PdfEncryptionTask.this) {
                    PdfEncryptionTask.this.notify(); // Notificar o doInBackground que o usuário concordou
                }
            }
        });
        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                overwriteConfirmed = false;
                synchronized (PdfEncryptionTask.this) {
                    PdfEncryptionTask.this.notify(); // Notificar o doInBackground que o usuário cancelou
                }
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        progressDialog.dismiss();
        if (result) {
            Toast.makeText(context, "PDFs criptografados com sucesso", Toast.LENGTH_SHORT).show();
            ((MainActivity) context).updatePdfListFromDatabase();
        } else {
            Toast.makeText(context, "Erro ao criptografar PDFs", Toast.LENGTH_SHORT).show();
        }
    }
}
