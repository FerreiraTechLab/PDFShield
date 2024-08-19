package org.ferreiratechlab.leitordepdfseguro.ui.display;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.IOException;
import java.util.Locale;

public class PdfDisplayFragment extends AppCompatActivity {

    private static final int YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123;
    private PDFView pdfView;
    private TextView pageIndicator;
    private SeekBar seekBar;
    private String pdfPath;
    private String password;
    private SharedPreferences sharedPreferences;

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
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }

        pdfPath = getIntent().getStringExtra("pdfPath");
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
                openPdfWithPassword("");
            }
        } else {
            // Tratar erro
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        // Fecha a atividade quando ela não está mais visível
        finish();
    }

    private boolean isPdfProtected() {
        PdfiumCore pdfiumCore = new PdfiumCore(this);
        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(Uri.parse(pdfPath), "r");
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
                Toast.makeText(this, "Permissão de leitura do armazenamento externo necessária", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }



    //    private void openPdfWithPassword(String password) {
//        try {
//            int lastPageRead = sharedPreferences.getInt(pdfPath, 0);
//
//            pdfView.fromUri(Uri.parse(pdfPath))
//                    .password(password)
//                    .defaultPage(lastPageRead)
//                    .spacing(10) // in dp
//                    .swipeHorizontal(true)
//                    .pageSnap(true)
//                    .onPageChange(new OnPageChangeListener() {
//                        @Override
//                        public void onPageChanged(int page, int pageCount) {
//                            // Update TextView
//                            pageIndicator.setText(String.format(Locale.getDefault(), "%d/%d", page + 1, pageCount));
//
//                            // Update SeekBar
//                            seekBar.setMax(pageCount - 1);
//                            seekBar.setProgress(page);
//
//                            // Save the last read page
//                            SharedPreferences.Editor myEdit = sharedPreferences.edit();
//                            myEdit.putInt(pdfPath, page);
//                            myEdit.apply();
//                        }
//                    })
//                    .load();
//
//        } catch (PdfPasswordException e) {
//            Toast.makeText(this, "Senha incorreta", Toast.LENGTH_SHORT).show();
//            showPasswordDialog();
//        }
//    }
    private void openPdfWithPassword(String password) {
        try {
            PdfiumCore pdfiumCore = new PdfiumCore(this);
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(Uri.parse(pdfPath), "r");
            PdfDocument pdfDocument = pdfiumCore.newDocument(fd, password);

            int lastPageRead = sharedPreferences.getInt(pdfPath, 0);

            pdfView.fromUri(Uri.parse(pdfPath))
                    .password(password)
                    .defaultPage(lastPageRead)
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



}
