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
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.ui.auth.ReAuthHelper;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.task.BackupSingleFileTask;
import org.ferreiratechlab.leitordepdfseguro.task.PdfEncryptionTask;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDisplayActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDocumentWrapper;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfViewModel;
import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

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
    private Executor executor;
    private PdfEncryptionTask currentEncryptionTask;
    private ProgressDialog migrationProgressDialog;

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
        executor = ContextCompat.getMainExecutor(this);
        ensureReadPermission();

        pdfAdapter = new PdfAdapter(pdfDocuments, pdfUri -> {
            if (pdfUri != null) {
                decryptPDF(String.valueOf(pdfUri));
            } else {
                Toast.makeText(this, "Erro ao abrir o PDF", Toast.LENGTH_SHORT).show();
            }
        });

        pdfRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfRecyclerView.setAdapter(pdfAdapter);

        addPdfFab.setOnClickListener(v -> ensureReadPermissionAndOpenPdfSelector());

        pdfAdapter.setOnPdfLongClickListener(new PdfAdapter.OnPdfLongClickListener() {
            @Override
            public void onPdfLongClick(int position) {
                showPdfLongClickOptions(position);
            }

        });

        db = AppDatabase.getInstance(this);
        migrateLegacyEncryptedFilesIfNeeded();
        migrateLegacyMetadataIfNeeded();

        pdfViewModel = new ViewModelProvider(this).get(PdfViewModel.class);

        // Roda no mesmo executor único das migrações (ver migrateLegacyEncryptedFilesIfNeeded /
        // migrateLegacyMetadataIfNeeded), para nunca ler uma linha entre o rename físico do
        // arquivo no disco e o commit do novo caminho no banco.
        AppExecutors.background().execute(() -> {
            pdfViewModel.init(db);
            runOnUiThread(() -> {
                pdfViewModel.getPdfs().observe(MainActivity.this, savedPdfs -> {
                    pdfDocuments.clear();
                    for (Pdf pdf : savedPdfs) {
                        pdfDocuments.add(PdfViewModel.toWrapper(pdf));
                    }
                    pdfAdapter.notifyDataSetChanged();
                    updateEmptyState();
                });
            });
        });

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
                } else if (id == R.id.nav_switch_to_texts) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                    startActivity(new Intent(MainActivity.this, org.ferreiratechlab.leitordepdfseguro.ui.texts.TextsActivity.class));
                    finish();
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

    private boolean requiresLegacyReadPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU;
    }

    private boolean checkReadPermission() {
        if (!requiresLegacyReadPermission()) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadPermission() {
        if (requiresLegacyReadPermission() && !checkReadPermission()) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void ensureReadPermission() {
        if (!checkReadPermission()) {
            requestReadPermission();
        }
    }

    private void ensureReadPermissionAndOpenPdfSelector() {
        if (checkReadPermission()) {
            openPdfSelector();
            return;
        }

        requestReadPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
        PdfDocumentWrapper pdfDocumentWrapper = pdfDocuments.get(position);
        new BackupSingleFileTask(this, pdfDocumentWrapper.getUri(), pdfDocumentWrapper.getTitle()).start();
    }
    private void removePdfFromListAndDatabase(int position) {
        PdfDocumentWrapper pdfDocumentWrapper = pdfDocuments.get(position);
        String pdfUri = pdfDocumentWrapper.getUri().toString();
        
        // Tentar apagar o arquivo de forma segura antes de remover do banco
        new Thread(() -> {
            try {
                File encryptedFile = new File(pdfUri);
                if (encryptedFile.exists()) {
                    EncryptionUtils.secureDelete(encryptedFile);
                }
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("removePdf", e);
            }
            
            // Remover da lista e do banco de dados
            runOnUiThread(() -> {
                pdfDocuments.remove(position);
                pdfAdapter.notifyItemRemoved(position);
                pdfViewModel.deletePdf(pdfDocumentWrapper.getId());
                updateEmptyState();
                Toast.makeText(MainActivity.this, "PDF removido com sucesso", Toast.LENGTH_SHORT).show();
            });
        }).start();
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
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == RESULT_OK) {
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
                            LoggingUtils.logErrorDebug("selectPdf", e);
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
                        LoggingUtils.logErrorDebug("selectPdf", e);
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

        AppExecutors.background().execute(() -> {
            List<Pdf> pdfs = db.pdfDao().getAll(); // Obter todos os PDFs do banco de dados
            // Descriptografa para a pasta privada de cache do app (não é acessível por outros
            // apps); a entrega final acontece via Share Sheet, sem deixar cópia em texto puro
            // permanente. cleanOldTempFiles() varre esta pasta a cada abertura do app.
            File shareDir = new File(getCacheDir(), "ShareBackups");
            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }

            runOnUiThread(() -> progressDialog.setMax(Math.max(pdfs.size(), 1)));

            ArrayList<Uri> shareUris = new ArrayList<>();
            int progress = 0;
            for (Pdf pdf : pdfs) {
                File encryptedFile = new File(pdf.getUri());
                String displayTitle = PdfViewModel.toWrapper(pdf).getTitle();
                // O título já inclui ".pdf" (nome original do arquivo importado).
                String exportFileName = displayTitle.toLowerCase().endsWith(".pdf") ? displayTitle : displayTitle + ".pdf";
                File decryptedFile = new File(shareDir, exportFileName);

                try {
                    EncryptionUtils.decryptFile(MainActivity.this, encryptedFile, decryptedFile);
                    shareUris.add(FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".provider", decryptedFile));
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("backup", e);
                }

                // Atualizar progresso na UI thread
                final int currentProgress = ++progress;
                runOnUiThread(() -> progressDialog.setProgress(currentProgress));
            }

            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (shareUris.isEmpty()) {
                    Toast.makeText(MainActivity.this, R.string.backup_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                shareIntent.setType("application/pdf");
                shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.backup_success_single)));
            });
        });
    }

    private void encryptFilesInBackground(List<PdfEncryptionTask.EncryptionItem> itemsToEncrypt) {
        currentEncryptionTask = new PdfEncryptionTask(this, itemsToEncrypt, db);
        currentEncryptionTask.start();
    }

    @Override
    protected void onDestroy() {
        if (currentEncryptionTask != null) {
            // Libera a thread de background caso ela esteja bloqueada aguardando
            // resposta do diálogo de sobrescrita, evitando vazar a Activity/thread.
            currentEncryptionTask.cancelDueToLifecycle();
        }
        super.onDestroy();
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
            File tempFile = null;
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

                tempFile = File.createTempFile("decrypted_", ".pdf", directory);

                EncryptionUtils.decryptFile(this, encryptedFile, tempFile);
                File finalTempFile = tempFile;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (finalTempFile.exists()) {
                        Toast.makeText(this, R.string.pdf_decrypted_success, Toast.LENGTH_SHORT).show();
                        openPdfFile(finalTempFile.getPath());
                    } else {
                        Toast.makeText(this, R.string.pdf_decryption_error, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Throwable throwable) {
                if (tempFile != null && tempFile.exists()) {
                    EncryptionUtils.secureDelete(tempFile);
                }
                LoggingUtils.logError("decryptPdf", throwable.getClass().getSimpleName());
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, R.string.pdf_decryption_error, Toast.LENGTH_SHORT).show();
                });
            }

        }).start();
    }

    private void openPdfFile(String filePath) {
        Intent intent = new Intent(MainActivity.this, PdfDisplayActivity.class);
        intent.putExtra("pdfPath", filePath);
        startActivity(intent);
    }

    private void cleanOldTempFiles() {
        deleteFilesInDir(new File(getCacheDir(), "DecryptedPDFs"));
        // Cópias descriptografadas geradas para a Share Sheet do backup (ver performBackup()
        // e BackupSingleFileTask); ficam em cache privado do app até o próximo start.
        deleteFilesInDir(new File(getCacheDir(), "ShareBackups"));
    }

    private void deleteFilesInDir(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    EncryptionUtils.secureDelete(file);
                }
            }
        }
    }

    /**
     * Re-criptografa em background todo PDF ainda protegido pela chave legada do Keystore
     * (Pdf#keyVersion == 1, sem gate de autenticação) usando a DEK amarrada ao PIN/biometria
     * desta sessão (já carregada em SessionKeyHolder por PinEntryActivity/WelcomeActivity
     * antes de chegarmos aqui). Resumível por linha: se o app for encerrado no meio, arquivos
     * já migrados (keyVersion=2) não são reprocessados na próxima abertura.
     */
    private void migrateLegacyEncryptedFilesIfNeeded() {
        AppExecutors.background().execute(() -> {
            int legacyCount;
            try {
                legacyCount = db.pdfDao().countLegacyKeyVersionPdfs();
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("Migration", e);
                return;
            }
            if (legacyCount == 0) {
                return;
            }

            runOnUiThread(() -> {
                addPdfFab.setEnabled(false);
                migrationProgressDialog = new ProgressDialog(this, R.style.CustomDialogTheme);
                migrationProgressDialog.setMessage(getString(R.string.migrating_encryption));
                migrationProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                migrationProgressDialog.setMax(legacyCount);
                migrationProgressDialog.setCancelable(false);
                migrationProgressDialog.show();
            });

            List<Pdf> legacyPdfs = db.pdfDao().getLegacyKeyVersionPdfs();
            int migrated = 0;
            for (Pdf pdf : legacyPdfs) {
                try {
                    migrateSingleFile(pdf);
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("Migration", e);
                    // keyVersion permanece 1: será retomado na próxima abertura do app.
                }
                final int progress = ++migrated;
                runOnUiThread(() -> {
                    if (migrationProgressDialog != null) {
                        migrationProgressDialog.setProgress(progress);
                    }
                });
            }

            runOnUiThread(() -> {
                if (migrationProgressDialog != null && migrationProgressDialog.isShowing()) {
                    migrationProgressDialog.dismiss();
                }
                addPdfFab.setEnabled(true);
            });
        });
    }

    private void migrateSingleFile(Pdf pdf) throws Exception {
        File encryptedFile = new File(pdf.getUri());
        File tempPlain = File.createTempFile("migrate_", ".pdf", getCacheDir());
        File newEncrypted = new File(encryptedFile.getParentFile(), encryptedFile.getName() + ".new");
        try {
            EncryptionUtils.decryptFileWithKey(KeyManagerUtils.getLegacyKey(), encryptedFile, tempPlain);
            EncryptionUtils.encryptFileWithKey(SessionKeyHolder.require(), tempPlain, newEncrypted);

            // Verifica que a nova cifra decifra corretamente com a DEK nova antes de trocar.
            File verifyFile = File.createTempFile("verify_", ".pdf", getCacheDir());
            try {
                EncryptionUtils.decryptFileWithKey(SessionKeyHolder.require(), newEncrypted, verifyFile);
            } finally {
                EncryptionUtils.secureDelete(verifyFile);
            }

            EncryptionUtils.replaceFileAtomically(newEncrypted, encryptedFile);

            pdf.keyVersion = 2;
            db.pdfDao().update(pdf);
        } finally {
            EncryptionUtils.secureDelete(tempPlain);
            if (newEncrypted.exists()) {
                EncryptionUtils.secureDelete(newEncrypted);
            }
        }
    }

    /**
     * Re-criptografa em background todo título de PDF ainda em texto puro
     * (Pdf#metadataVersion == 1) e renomeia o arquivo no disco (nomeado hoje a partir do
     * nome original) para um UUID opaco, para que nem o banco nem o nome do arquivo revelem
     * o nome do PDF. Mesmo padrão resumível por linha de migrateLegacyEncryptedFilesIfNeeded,
     * enfileirada logo depois no mesmo AppExecutors.background() de thread única.
     */
    private void migrateLegacyMetadataIfNeeded() {
        AppExecutors.background().execute(() -> {
            int legacyCount;
            try {
                legacyCount = db.pdfDao().countLegacyMetadataVersionPdfs();
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("MetadataMigration", e);
                return;
            }
            if (legacyCount == 0) {
                return;
            }

            List<Pdf> legacyPdfs = db.pdfDao().getLegacyMetadataVersionPdfs();
            for (Pdf pdf : legacyPdfs) {
                try {
                    migrateSingleMetadata(pdf);
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("MetadataMigration", e);
                    // metadataVersion permanece 1: será retomado na próxima abertura do app.
                }
            }
        });
    }

    private void migrateSingleMetadata(Pdf pdf) throws Exception {
        File existingFile = new File(pdf.getUri());
        File renamedFile = new File(existingFile.getParentFile(), java.util.UUID.randomUUID() + ".enc");
        EncryptionUtils.replaceFileAtomically(existingFile, renamedFile);

        pdf.title = EncryptionUtils.encryptString(SessionKeyHolder.require(), pdf.title);
        pdf.uri = renamedFile.getAbsolutePath();
        pdf.metadataVersion = 2;
        db.pdfDao().update(pdf);
    }

    private void authenticateAction(Runnable onAuthenticated) {
        ReAuthHelper.authenticateAction(this, executor, onAuthenticated);
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
