package org.ferreiratechlab.leitordepdfseguro.ui.main;



import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.service.EncryptionService;
import org.ferreiratechlab.leitordepdfseguro.task.BackupSingleFileTask;
import org.ferreiratechlab.leitordepdfseguro.task.PdfEncryptionTask;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDisplayActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDocumentWrapper;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfViewModel;
import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 0;

    private static final int REQUEST_CODE_PERMISSIONS = 1;
    private static final int REQUEST_CODE_MANAGE_EXTERNAL_STORAGE = 2;
    private static final int YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123;

    ExtendedFloatingActionButton addPdfFab;
    RecyclerView pdfRecyclerView;
    View emptyState;
    PdfAdapter pdfAdapter;
    List<PdfDocumentWrapper> pdfDocuments = new ArrayList<>();
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    AppDatabase db;
    PdfViewModel pdfViewModel;
    Toolbar toolbar;
    NavigationView navigationView;



    // No início da classe MainActivity
    private EncryptionService encryptionService;
    private Executor executor;

    MenuItem menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        // Limpar arquivos temporários que podem ter sobrado de uma sessão anterior (ex: crash)
        cleanOldTempFiles();

        addPdfFab = findViewById(R.id.add_pdf_fab);
        pdfRecyclerView = findViewById(R.id.pdf_recycler_view);
        emptyState = findViewById(R.id.empty_state);
        toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.nav_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        encryptionService = new EncryptionService(this);
        executor = ContextCompat.getMainExecutor(this);
        if(!checkPermissions()){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        YOUR_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            }
        }

        pdfAdapter = new PdfAdapter(pdfDocuments, pdfUri -> {
            if (pdfUri != null) {
                decryptPDF(String.valueOf(pdfUri));
            } else {
                Toast.makeText(this, "Erro ao abrir o PDF", Toast.LENGTH_SHORT).show();
            }
        });

        pdfRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfRecyclerView.setAdapter(pdfAdapter);

        addPdfFab.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
        });

        pdfAdapter.setOnPdfLongClickListener(new PdfAdapter.OnPdfLongClickListener() {
            @Override
            public void onPdfLongClick(int position) {
                showPdfLongClickOptions(position);
            }

        });

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "database-name")
                .build();

        pdfViewModel = new ViewModelProvider(this).get(PdfViewModel.class);

        new Thread(() -> {
            pdfViewModel.init(db);
            runOnUiThread(() -> {
                pdfViewModel.getPdfs().observe(MainActivity.this, savedPdfs -> {
                    pdfDocuments.clear();
                    for (Pdf pdf : savedPdfs) {
                        pdfDocuments.add(new PdfDocumentWrapper(Uri.parse(pdf.uri), pdf.title));
                    }
                    pdfAdapter.notifyDataSetChanged();
                    updateEmptyState();
                });
            });
        }).start();

        setSupportActionBar(toolbar);
        // Mudar a cor do ícone de navegação
        toolbar.getNavigationIcon().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP);

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_item_backup){
                    authenticateAction(MainActivity.this::showBackupConfirmationDialog);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                } else if (id == R.id.nav_delete_all){
                    authenticateAction(MainActivity.this::showDeleteAllConfirmationDialog);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                }

                // Criar um construtor de AlertDialog para os outros itens
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.CustomDialogTheme);
                if (id == R.id.nav_item1){
                    // Configurar a mensagem para o item "Sobre"
                    builder.setMessage(R.string.about_msg);
                }
                else if (id == R.id.nav_item2) {
                    builder.setMessage(R.string.tips_msg);

                }else if(id == R.id.nav_item3){
                    builder.setMessage(R.string.why_encrypt_msg);

                }else if(id == R.id.nav_item4){
                    // Configurar a mensagem para o item "Diretórios Ocultos"
                    builder.setMessage(R.string.privacy_msg);
                }else{
                    // Configurar uma mensagem padrão
                    builder.setMessage(getString(R.string.in_development, item.getItemId()));
                }

                // Criar e mostrar o AlertDialog
                builder.setPositiveButton(R.string.ok, null);
                builder.show();

                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });

    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true; // Permissões são automaticamente concedidas em versões mais antigas
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_MANAGE_EXTERNAL_STORAGE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_CODE_MANAGE_EXTERNAL_STORAGE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                openPdfSelector();
            } else {
                Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void showRemoveConfirmationDialog(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.remove_pdf_title);
        builder.setMessage(R.string.remove_pdf_msg);
        builder.setPositiveButton(R.string.remove, (dialog, which) -> removePdfFromListAndDatabase(position));
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    private void showPdfLongClickOptions(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.pdf_options);
        String[] options = {getString(R.string.remove), getString(R.string.backup)};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                authenticateAction(() -> showRemoveConfirmationDialog(position));
            } else if (which == 1) {
                authenticateAction(() -> backupSingleFile(position));
            }
        });
        builder.show();
    }

    private void backupSingleFile(int position) {
        new BackupSingleFileTask(this, pdfDocuments.get(position).getUri()).execute();
    }
    private void removePdfFromListAndDatabase(int position) {
        PdfDocumentWrapper pdfDocumentWrapper = pdfDocuments.get(position);
        pdfDocuments.remove(position);
        pdfAdapter.notifyItemRemoved(position);
        // Remover o PDF do banco de dados
        pdfViewModel.deletePdf(pdfDocumentWrapper.getTitle());
        updateEmptyState();
        Toast.makeText(MainActivity.this, "PDF removido com sucesso", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_CODE_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    openPdfSelector();
                } else {
                    Toast.makeText(this, "Permissão de gerenciamento de armazenamento necessária", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == RESULT_OK) {
            if (resultData != null) {
                List<PdfEncryptionTask.EncryptionItem> itemsToEncrypt = new ArrayList<>();
                if (resultData.getClipData() != null) {
                    // Múltiplos arquivos selecionados
                    ClipData clipData = resultData.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        String filename = getFileNameFromUri(uri);

                        try {
                            File tempFile = new File(getCacheDir(), filename);
                            copyContentUriToFile(uri, tempFile);
                            itemsToEncrypt.add(new PdfEncryptionTask.EncryptionItem(tempFile, uri));
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Erro ao copiar o arquivo: " + filename, Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if (resultData.getData() != null) {
                    // Um único arquivo selecionado
                    Uri uri = resultData.getData();
                    String filename = getFileNameFromUri(uri);

                    try {
                        File tempFile = new File(getCacheDir(), filename);
                        copyContentUriToFile(uri, tempFile);
                        itemsToEncrypt.add(new PdfEncryptionTask.EncryptionItem(tempFile, uri));
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Erro ao copiar o arquivo: " + filename, Toast.LENGTH_SHORT).show();
                    }
                }

                if (!itemsToEncrypt.isEmpty()) {
                    encryptFilesInBackground(itemsToEncrypt);
                }
            }
        }
    }
    private void showBackupConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.security_warning_title);
        builder.setMessage(R.string.backup_warning_msg);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            performBackup();
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void performBackup() {
        ProgressDialog progressDialog = new ProgressDialog(this, R.style.CustomDialogTheme);
        progressDialog.setMessage(getString(R.string.backup_progress));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);
        progressDialog.show();

        new Thread(() -> {
            List<Pdf> pdfs = db.pdfDao().getAll(); // Obter todos os PDFs do banco de dados
            File backupDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BackupPDFs");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            int progress = 0;
            for (Pdf pdf : pdfs) {
                File encryptedFile = new File(pdf.getUri());
                File decryptedFile = new File(backupDir, pdf.getTitle() + ".pdf");

                try {
                    EncryptionUtils.decryptFile(MainActivity.this, encryptedFile, decryptedFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Atualizar progresso na UI thread
                final int currentProgress = ++progress;
                runOnUiThread(() -> progressDialog.setProgress(currentProgress));
            }

            // Dismiss progressDialog na UI thread após o backup
            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, getString(R.string.backup_success, backupDir.getAbsolutePath()), Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void encryptFilesInBackground(List<PdfEncryptionTask.EncryptionItem> itemsToEncrypt) {
        PdfEncryptionTask task = new PdfEncryptionTask(this, itemsToEncrypt, db);
        task.execute();
    }

    public void updatePdfListFromDatabase() {
        new Thread(() -> {
            List<Pdf> savedPdfs = db.pdfDao().getAll();
            List<PdfDocumentWrapper> newPdfDocuments = new ArrayList<>();
            for (Pdf pdf : savedPdfs) {
                newPdfDocuments.add(new PdfDocumentWrapper(Uri.parse(pdf.uri), pdf.title));
            }

            runOnUiThread(() -> {
                pdfAdapter.updatePdfDocuments(newPdfDocuments);
                updateEmptyState();
            });
        }).start();
    }

    private void updateEmptyState() {
        if (pdfDocuments.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            pdfRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            pdfRecyclerView.setVisibility(View.VISIBLE);
        }
    }




    private void openPdfSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
    }

    private Uri getOriginalFileUri(Uri uri) {
        String[] projection = { MediaStore.MediaColumns._ID };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            long fileId = cursor.getLong(columnIndex);
            cursor.close();

            Uri fileUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), String.valueOf(fileId));
            return fileUri;
        }
        return null;
    }

    private void copyContentUriToFile(Uri uri, File destFile) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        OutputStream outputStream = new FileOutputStream(destFile);

        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        outputStream.close();
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        Cursor cursor = null;
        try {
            String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                fileName = cursor.getString(columnIndex);
                // Obtenha apenas o nome do arquivo removendo o caminho completo
                if (fileName != null) {
                    fileName = new File(fileName).getName();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fileName;
    }
    private void decryptPDF(String filePath) {
        ProgressDialog progressDialog = new ProgressDialog(this, R.style.CustomDialogTheme);
        progressDialog.setMessage(getString(R.string.decrypting_pdf));
        progressDialog.setCancelable(false);
        progressDialog.show();
        new Thread(() -> {
            try {
                String finalFilePath = filePath.startsWith("file:/") ? filePath.substring(6) : filePath;

                File directory = new File(getCacheDir(), "DecryptedPDFs");
                if (!directory.exists()) {
                    directory.mkdirs();
                }

                File encryptedFile = new File(finalFilePath);
                if (!encryptedFile.exists()) {
                    throw new IOException(getString(R.string.file_not_found, encryptedFile.getAbsolutePath()));
                }

                File tempFile = new File(directory.getPath(), "temp.pdf");

                EncryptionUtils.decryptFile(this, encryptedFile, tempFile);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (tempFile.exists()) {
                        Toast.makeText(this, R.string.pdf_decrypted_success, Toast.LENGTH_SHORT).show();
                        openPdfFile(tempFile.getPath());
                    } else {
                        Toast.makeText(this, R.string.pdf_decryption_error, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, R.string.pdf_decryption_error, Toast.LENGTH_SHORT).show();
                });
            }

        }).start();
    }

    private void openPdfFile(String filePath) {
        Intent intent = new Intent(MainActivity.this, PdfDisplayActivity.class);
        startActivity(intent);
    }

    private void cleanOldTempFiles() {
        File tempDir = new File(getCacheDir(), "DecryptedPDFs");
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    EncryptionUtils.secureDelete(file);
                }
            }
        }
    }

    private void authenticateAction(Runnable onAuthenticated) {
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        boolean useBiometrics = prefs.getBoolean("UseBiometrics", false);
        String savedPin = prefs.getString("AppPin", null);

        if (useBiometrics) {
            BiometricManager biometricManager = BiometricManager.from(this);
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        runOnUiThread(onAuthenticated);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // Se falhar biometria, tenta o PIN interno
                        showPinVerificationDialog(savedPin, onAuthenticated);
                    }
                });

                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.biometric_auth_title))
                        .setSubtitle(getString(R.string.auth_required_action))
                        .setNegativeButtonText(getString(R.string.enter_pin))
                        .build();

                biometricPrompt.authenticate(promptInfo);
                return;
            }
        }
        
        // Se não usar biometria ou sensor não disponível, pede PIN
        showPinVerificationDialog(savedPin, onAuthenticated);
    }

    private void showPinVerificationDialog(String savedPin, Runnable onSuccess) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_verify, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        List<String> inputPin = new ArrayList<>();
        View[] dots = new View[6];
        LinearLayout dotContainer = dialogView.findViewById(R.id.dialog_dot_container);
        for (int i = 0; i < 6; i++) {
            dots[i] = dotContainer.getChildAt(i);
        }

        // Setup Numpad no diálogo
        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : buttonIds) {
            dialogView.findViewById(id).setOnClickListener(v -> {
                if (inputPin.size() < 6) {
                    inputPin.add(((TextView) v).getText().toString());
                    for (int i = 0; i < 6; i++) {
                        dots[i].setBackgroundResource(i < inputPin.size() ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
                    }
                    if (inputPin.size() == 6) {
                        if (String.join("", inputPin).equals(savedPin)) {
                            dialog.dismiss();
                            onSuccess.run();
                        } else {
                            Toast.makeText(this, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
                            inputPin.clear();
                            for (int i = 0; i < 6; i++) {
                                dots[i].setBackgroundResource(R.drawable.pin_dot_empty);
                            }
                        }
                    }
                }
            });
        }

        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            if (!inputPin.isEmpty()) {
                inputPin.remove(inputPin.size() - 1);
                for (int i = 0; i < 6; i++) {
                    dots[i].setBackgroundResource(i < inputPin.size() ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
                }
            }
        });

        dialog.show();
    }

    private void showDeleteAllConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.delete_all_title);
        builder.setMessage(R.string.delete_all_msg);
        builder.setPositiveButton(R.string.remove, (dialog, which) -> deleteAllPdfs());
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void deleteAllPdfs() {
        new Thread(() -> {
            List<Pdf> pdfs = db.pdfDao().getAll();
            for (Pdf pdf : pdfs) {
                File file = new File(pdf.getUri());
                EncryptionUtils.secureDelete(file);
            }
            db.pdfDao().deleteAll();
            runOnUiThread(() -> {
                pdfDocuments.clear();
                pdfAdapter.notifyDataSetChanged();
                updateEmptyState();
                Toast.makeText(MainActivity.this, R.string.all_pdfs_removed_success, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
}
