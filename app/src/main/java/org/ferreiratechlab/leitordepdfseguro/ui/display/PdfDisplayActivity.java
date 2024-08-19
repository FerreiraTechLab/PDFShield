package org.ferreiratechlab.leitordepdfseguro.ui.display;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.WindowManager;
import android.widget.Toast;
import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfPasswordException;
import com.shockwave.pdfium.PdfiumCore;
import android.Manifest;


import org.ferreiratechlab.leitordepdfseguro.R;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class PdfDisplayActivity extends AppCompatActivity {

    private static final int YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123;
    private PDFView pdfView;
    private TextView pageIndicator;
    private SeekBar seekBar;
    private String pdfPath;
    private String password;
    private SharedPreferences sharedPreferences;

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_pdf_display);

        pdfView = findViewById(R.id.pdfView);
        pageIndicator = findViewById(R.id.pageIndicator);
        seekBar = findViewById(R.id.seekBar);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.MANAGE_EXTERNAL_STORAGE},
                    YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
        File open = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DecryptedPDFs");
        pdfPath = open+"/temp.pdf";
        sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    pdfView.jumpTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        if (pdfPath != null) {
            if (isPdfProtected()) {
                showPasswordDialog();
            } else {
                openPdf();
            }
        } else {
            // Tratar erro
        }
    }

    @Override
    protected  void onDestroy(){
        super.onDestroy();
        deleteTemporaryFiles();
        finish();
    }
    @Override
    protected void onStop() {
        super.onStop();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        deleteTemporaryFiles();
        finish();
         // 100 ms de atraso, ajuste conforme necessário
    }

    private boolean isPdfProtected() {
        PdfiumCore pdfiumCore = new PdfiumCore(this);
        try {
            Uri pdfUri = FileProvider.getUriForFile(this,
                    "org.ferreiratechlab.leitordepdfseguro.provider", new File(pdfPath));
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(pdfUri, "r");
            PdfDocument pdfDocument = pdfiumCore.newDocument(fd);
            pdfiumCore.closeDocument(pdfDocument);
            return false;
        } catch (PdfPasswordException e) {
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Digite a senha do PDF");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                password = input.getText().toString();
                openPdfWithPassword(password);
            }
        });

        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });

        builder.show();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida.
                // Continue com a leitura do PDF como planejado.
            } else {
                // Permissão negada.
                // Mostre uma explicação ao usuário e termine a atividade se necessário.
                //Toast.makeText(this, "Permissão de leitura do armazenamento externo necessária", Toast.LENGTH_LONG).show();
                // Solicitar permissão novamente
            }
        }
    }


    private void deleteTemporaryFiles() {
        File cacheDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "DecryptedPDFs");
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.exists() && file.getName().startsWith("temp")) {
                    file.delete();
                }
            }
        }
    }
    private void openPdfWithPassword(String password) {
        try {
            PdfiumCore pdfiumCore = new PdfiumCore(this);
            Uri pdfUri = FileProvider.getUriForFile(this,
                    "org.ferreiratechlab.leitordepdfseguro.provider", new File(pdfPath));
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(pdfUri, "r");
            PdfDocument pdfDocument = pdfiumCore.newDocument(fd, password);

            int lastPageRead = sharedPreferences.getInt(pdfPath, 0);

            pdfView.fromUri(pdfUri)
                    .password(password)
                    .defaultPage(0)
                    .spacing(10) // in dp
                    .swipeHorizontal(true)
                    .pageSnap(true)
                    .onPageChange(new OnPageChangeListener() {
                        @Override
                        public void onPageChanged(int page, int pageCount) {
                            // Atualizar TextView
                            pageIndicator.setText(String.format(Locale.getDefault(), "%d/%d", page + 1, pageCount));

                            // Atualizar SeekBar
                            seekBar.setMax(pageCount - 1);
                            seekBar.setProgress(page);

                            // Salvar a última página lida
                            SharedPreferences.Editor myEdit = sharedPreferences.edit();
                            myEdit.putInt(pdfPath, page);
                            myEdit.apply();
                        }
                    })
                    .load();

            pdfiumCore.closeDocument(pdfDocument);  // Não se esqueça de fechar o documento!

        } catch (IOException e) {
            Toast.makeText(this, "Senha incorreta", Toast.LENGTH_SHORT).show();
            showPasswordDialog();
        }
    }

    private void openPdf() {
        try {
            PdfiumCore pdfiumCore = new PdfiumCore(this);
            Uri pdfUri = FileProvider.getUriForFile(this,
                    "org.ferreiratechlab.leitordepdfseguro.provider", new File(pdfPath));
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(pdfUri, "r");
            PdfDocument pdfDocument = pdfiumCore.newDocument(fd);

            int lastPageRead = sharedPreferences.getInt(pdfPath, 0);

            pdfView.fromUri(pdfUri)
                    .defaultPage(0)
                    .spacing(10) // in dp
                    .swipeHorizontal(true)
                    .pageSnap(true)
                    .onPageChange(new OnPageChangeListener() {
                        @Override
                        public void onPageChanged(int page, int pageCount) {
                            // Atualizar TextView
                            pageIndicator.setText(String.format(Locale.getDefault(), "%d/%d", page + 1, pageCount));

                            // Atualizar SeekBar
                            seekBar.setMax(pageCount - 1);
                            seekBar.setProgress(page);

                            // Salvar a última página lida
                            SharedPreferences.Editor myEdit = sharedPreferences.edit();
                            myEdit.putInt(pdfPath, page);
                            myEdit.apply();
                        }
                    })
                    .load();

            pdfiumCore.closeDocument(pdfDocument);  // Não se esqueça de fechar o documento!

        } catch (IOException e) {
            Toast.makeText(this, "Erro ao abrir PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace(); // Aqui você pode logar o erro para análise posterior
        }
    }




}
