package org.ferreiratechlab.leitordepdfseguro.ui.display;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnTapListener;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfPasswordException;
import com.shockwave.pdfium.PdfiumCore;
import android.Manifest;
import android.view.MotionEvent;


import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class PdfDisplayActivity extends AppCompatActivity {

    private static final int YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123;
    private PDFView pdfView;
    private TextView pageIndicator;
    private SeekBar seekBar;
    private View controlsContainer;
    private Toolbar toolbar;
    private String pdfPath;
    private String password;
    private SharedPreferences sharedPreferences;
    
    private boolean isNightMode = false;
    private boolean isUiVisible = true;
    private final Handler hideHandler = new Handler();
    private final Runnable hideRunnable = () -> toggleUi(false);

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_pdf_display);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.reader_title);
        }

        pdfView = findViewById(R.id.pdfView);
        pageIndicator = findViewById(R.id.pageIndicator);
        seekBar = findViewById(R.id.seekBar);
        controlsContainer = findViewById(R.id.controlsContainer);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.MANAGE_EXTERNAL_STORAGE},
                    YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
        
        File open = new File(getCacheDir(), "DecryptedPDFs");
        pdfPath = new File(open, "temp.pdf").getAbsolutePath();
        sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser) {
                    pdfView.jumpTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hideHandler.removeCallbacks(hideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                startAutoHideTimer();
            }
        });

        if (pdfPath != null) {
            if (isPdfProtected()) {
                showPasswordDialog();
            } else {
                openPdf();
            }
        }
        
        startAutoHideTimer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.pdf_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_night_mode) {
            isNightMode = !isNightMode;
            reloadPdf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reloadPdf() {
        if (password != null) {
            openPdfWithPassword(password);
        } else {
            openPdf();
        }
    }

    private void toggleUi(boolean show) {
        isUiVisible = show;
        if (show) {
            toolbar.animate().translationY(0).setDuration(200).start();
            controlsContainer.animate().translationY(0).setDuration(200).start();
            startAutoHideTimer();
        } else {
            toolbar.animate().translationY(-toolbar.getHeight()).setDuration(200).start();
            controlsContainer.animate().translationY(controlsContainer.getHeight()).setDuration(200).start();
        }
    }

    private void startAutoHideTimer() {
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteTemporaryFiles();
        hideHandler.removeCallbacks(hideRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        deleteTemporaryFiles();
        finish();
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
            return false;
        }
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        builder.setView(dialogView);

        EditText input = dialogView.findViewById(R.id.passwordEditText);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            password = input.getText().toString();
            openPdfWithPassword(password);
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> finish());
        builder.setOnCancelListener(dialog -> finish());
        builder.show();
    }

    private void deleteTemporaryFiles() {
        File cacheDir = new File(getCacheDir(), "DecryptedPDFs");
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                EncryptionUtils.secureDelete(file);
            }
        }
    }

    private final OnTapListener tapListener = motionEvent -> {
        toggleUi(!isUiVisible);
        return true;
    };

    private void openPdfWithPassword(String password) {
        Uri pdfUri = FileProvider.getUriForFile(this,
                "org.ferreiratechlab.leitordepdfseguro.provider", new File(pdfPath));
        
        pdfView.fromUri(pdfUri)
                .password(password)
                .defaultPage(pdfView.getCurrentPage())
                .enableAntialiasing(true)
                .enableAnnotationRendering(true)
                .nightMode(isNightMode)
                .spacing(10)
                .swipeHorizontal(true)
                .pageSnap(true)
                .onTap(tapListener)
                .onPageChange((page, pageCount) -> {
                    pageIndicator.setText(String.format(Locale.getDefault(), "%d/%d", page + 1, pageCount));
                    seekBar.setMax(pageCount - 1);
                    seekBar.setProgress(page);
                    
                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt(pdfPath, page);
                    myEdit.apply();
                })
                .onError(t -> {
                    if (t instanceof PdfPasswordException) {
                        Toast.makeText(this, "Senha incorreta", Toast.LENGTH_SHORT).show();
                        showPasswordDialog();
                    } else {
                        Toast.makeText(this, "Erro ao abrir PDF", Toast.LENGTH_SHORT).show();
                    }
                })
                .load();
    }

    private void openPdf() {
        Uri pdfUri = FileProvider.getUriForFile(this,
                "org.ferreiratechlab.leitordepdfseguro.provider", new File(pdfPath));

        pdfView.fromUri(pdfUri)
                .defaultPage(pdfView.getCurrentPage())
                .enableAntialiasing(true)
                .enableAnnotationRendering(true)
                .nightMode(isNightMode)
                .spacing(10)
                .swipeHorizontal(true)
                .pageSnap(true)
                .onTap(tapListener)
                .onPageChange((page, pageCount) -> {
                    pageIndicator.setText(String.format(Locale.getDefault(), "%d/%d", page + 1, pageCount));
                    seekBar.setMax(pageCount - 1);
                    seekBar.setProgress(page);

                    SharedPreferences.Editor myEdit = sharedPreferences.edit();
                    myEdit.putInt(pdfPath, page);
                    myEdit.apply();
                })
                .onError(t -> Toast.makeText(this, "Erro ao abrir PDF", Toast.LENGTH_SHORT).show())
                .load();
    }
}
