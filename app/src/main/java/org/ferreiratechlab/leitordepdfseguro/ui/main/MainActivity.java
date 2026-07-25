package org.ferreiratechlab.leitordepdfseguro.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.task.BackupSingleFileTask;
import org.ferreiratechlab.leitordepdfseguro.task.PdfEncryptionTask;
import org.ferreiratechlab.leitordepdfseguro.ui.auth.ReAuthHelper;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDisplayActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDocumentWrapper;
import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfViewModel;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OPEN_DOCUMENT = 0;
    private static final int REQUEST_CODE_PERMISSIONS = 1;

    private ExtendedFloatingActionButton addPdfFab;
    private RecyclerView pdfRecyclerView;
    private View emptyState;
    private PdfAdapter pdfAdapter;
    private TextInputEditText searchEditText;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private AppDatabase db;
    private PdfViewModel pdfViewModel;
    private Toolbar toolbar;
    private NavigationView navigationView;

    private Executor executor;
    private PdfEncryptionTask currentEncryptionTask;
    private ProgressDialog migrationProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        cleanOldTempFiles();

        addPdfFab = findViewById(R.id.add_pdf_fab);
        pdfRecyclerView = findViewById(R.id.pdf_recycler_view);
        emptyState = findViewById(R.id.empty_state);
        searchEditText = findViewById(R.id.search_edit_text);
        toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.nav_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        executor = ContextCompat.getMainExecutor(this);
        
        ensureReadPermission();

        pdfAdapter = new PdfAdapter(new ArrayList<>(), pdfUri -> {
            if (pdfUri != null) {
                decryptPDF(String.valueOf(pdfUri));
            } else {
                Toast.makeText(this, "Erro ao abrir o PDF", Toast.LENGTH_SHORT).show();
            }
        });

        pdfAdapter.setOnPdfMenuClickListener(this::showPdfItemMenu);
        pdfRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfRecyclerView.setAdapter(pdfAdapter);

        addPdfFab.setOnClickListener(v -> ensureReadPermissionAndOpenPdfSelector());

        setupSearch();

        db = AppDatabase.getInstance(this);
        migrateLegacyEncryptedFilesIfNeeded();
        migrateLegacyMetadataIfNeeded();

        pdfViewModel = new ViewModelProvider(this).get(PdfViewModel.class);
        pdfViewModel.init(db);
        pdfViewModel.getPdfs().observe(this, savedPdfs -> {
            AppExecutors.background().execute(() -> {
                List<PdfDocumentWrapper> wrappers = new ArrayList<>();
                for (Pdf pdf : savedPdfs) {
                    wrappers.add(PdfViewModel.toWrapper(pdf));
                }
                runOnUiThread(() -> {
                    pdfAdapter.updatePdfDocuments(wrappers);
                    updateEmptyState();
                });
            });
        });

        setSupportActionBar(toolbar);
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP);
        }

        toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);
            if (id == R.id.nav_item_backup) {
                authenticateAction(this::showBackupConfirmationDialog);
                return true;
            } else if (id == R.id.nav_delete_all) {
                authenticateAction(this::showDeleteAllConfirmationDialog);
                return true;
            } else if (id == R.id.nav_switch_to_texts) {
                startActivity(new Intent(MainActivity.this, org.ferreiratechlab.leitordepdfseguro.ui.texts.TextsActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_exit) {
                finishAffinity();
                return true;
            }
            showGenericInfoDialog(id);
            return true;
        });
    }

    private void showGenericInfoDialog(int itemId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        if (itemId == R.id.nav_item1) builder.setMessage(R.string.about_msg);
        else if (itemId == R.id.nav_item2) builder.setMessage(R.string.tips_msg);
        else if (itemId == R.id.nav_item3) builder.setMessage(R.string.why_encrypt_msg);
        else if (itemId == R.id.nav_item4) builder.setMessage(R.string.privacy_msg);
        else builder.setMessage(getString(R.string.in_development, itemId));
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                pdfAdapter.applyFilter(s.toString());
                updateEmptyState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateEmptyState() {
        if (pdfAdapter == null) return;
        if (pdfAdapter.isEmpty() || pdfAdapter.isFilterEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            pdfRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            pdfRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showPdfItemMenu(PdfDocumentWrapper pdf, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.pdf_item_menu);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_remove) {
                authenticateAction(() -> showRemoveConfirmationDialog(pdf));
                return true;
            } else if (id == R.id.menu_backup) {
                authenticateAction(() -> backupSingleFile(pdf));
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRemoveConfirmationDialog(PdfDocumentWrapper pdf) {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.remove_pdf_title)
                .setMessage(R.string.remove_pdf_msg)
                .setPositiveButton(R.string.remove, (dialog, which) -> removePdfFromDatabase(pdf))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void backupSingleFile(PdfDocumentWrapper pdf) {
        new BackupSingleFileTask(this, pdf.getUri(), pdf.getTitle()).start();
    }

    private void removePdfFromDatabase(PdfDocumentWrapper pdf) {
        new Thread(() -> {
            try {
                File file = new File(pdf.getUri().getPath());
                if (file.exists()) EncryptionUtils.secureDelete(file);
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("removePdf", e);
            }
            runOnUiThread(() -> {
                pdfViewModel.deletePdf(pdf.getId());
                Toast.makeText(this, R.string.pdf_removed_success, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void ensureReadPermissionAndOpenPdfSelector() {
        if (checkReadPermission()) openPdfSelector();
        else requestReadPermission();
    }

    private boolean checkReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openPdfSelector();
        }
    }

    private void openPdfSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, dataReceived(requestCode, resultCode, resultData));
    }

    private Intent dataReceived(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == RESULT_OK && resultData != null) {
            List<PdfEncryptionTask.EncryptionItem> items = new ArrayList<>();
            if (resultData.getClipData() != null) {
                ClipData clip = resultData.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) processSelectedUri(clip.getItemAt(i).getUri(), items);
            } else if (resultData.getData() != null) {
                processSelectedUri(resultData.getData(), items);
            }
            if (!items.isEmpty()) encryptFilesInBackground(items);
        }
        return resultData;
    }

    private void processSelectedUri(Uri uri, List<PdfEncryptionTask.EncryptionItem> list) {
        String filename = getFileNameFromUri(uri);
        try {
            File temp = new File(getCacheDir(), filename);
            copyContentUriToFile(uri, temp);
            list.add(new PdfEncryptionTask.EncryptionItem(temp, uri));
        } catch (IOException e) {
            LoggingUtils.logErrorDebug("selectPdf", e);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        return name != null ? new File(name).getName() : "document.pdf";
    }

    private void copyContentUriToFile(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
        }
    }

    private void encryptFilesInBackground(List<PdfEncryptionTask.EncryptionItem> items) {
        currentEncryptionTask = new PdfEncryptionTask(this, items, db);
        currentEncryptionTask.start();
    }

    private void decryptPDF(String filePath) {
        ProgressDialog progress = new ProgressDialog(this, R.style.CustomDialogTheme);
        progress.setMessage(getString(R.string.decrypting_pdf));
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            File temp = null;
            try {
                File encrypted = new File(filePath.startsWith("file:/") ? filePath.substring(6) : filePath);
                File dir = new File(getCacheDir(), "DecryptedPDFs");
                if (!dir.exists()) dir.mkdirs();
                temp = File.createTempFile("decrypted_", ".pdf", dir);
                EncryptionUtils.decryptFile(this, encrypted, temp);
                File finalTemp = temp;
                runOnUiThread(() -> {
                    progress.dismiss();
                    openPdfFile(finalTemp.getPath());
                });
            } catch (Exception e) {
                if (temp != null) EncryptionUtils.secureDelete(temp);
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(this, R.string.pdf_decryption_error, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openPdfFile(String path) {
        Intent intent = new Intent(this, PdfDisplayActivity.class);
        intent.putExtra("pdfPath", path);
        startActivity(intent);
    }

    private void authenticateAction(Runnable onAuthed) {
        ReAuthHelper.authenticateAction(this, executor, onAuthed);
    }

    private void showBackupConfirmationDialog() {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.security_warning_title)
                .setMessage(R.string.backup_warning_msg)
                .setPositiveButton(R.string.ok, (d, w) -> performBackup())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performBackup() {
        ProgressDialog progress = new ProgressDialog(this, R.style.CustomDialogTheme);
        progress.setMessage(getString(R.string.backup_progress));
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.show();

        AppExecutors.background().execute(() -> {
            List<Pdf> pdfs = db.pdfDao().getAllSync();
            File shareDir = new File(getCacheDir(), "ShareBackups");
            if (!shareDir.exists()) shareDir.mkdirs();
            runOnUiThread(() -> progress.setMax(Math.max(pdfs.size(), 1)));
            ArrayList<Uri> uris = new ArrayList<>();
            for (int i = 0; i < pdfs.size(); i++) {
                Pdf pdf = pdfs.get(i);
                String title = PdfViewModel.toWrapper(pdf).getTitle();
                File dest = new File(shareDir, title.toLowerCase().endsWith(".pdf") ? title : title + ".pdf");
                try {
                    EncryptionUtils.decryptFile(this, new File(pdf.uri), dest);
                    uris.add(FileProvider.getUriForFile(this, getPackageName() + ".provider", dest));
                } catch (Exception e) { LoggingUtils.logErrorDebug("backup", e); }
                int finalI = i + 1;
                runOnUiThread(() -> progress.setProgress(finalI));
            }
            runOnUiThread(() -> {
                progress.dismiss();
                if (uris.isEmpty()) Toast.makeText(this, R.string.backup_error, Toast.LENGTH_SHORT).show();
                else {
                    Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                    intent.setType("application/pdf");
                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, getString(R.string.backup_success_single)));
                }
            });
        });
    }

    private void showDeleteAllConfirmationDialog() {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.delete_all_title)
                .setMessage(R.string.delete_all_msg)
                .setPositiveButton(R.string.remove, (d, w) -> deleteAllPdfs())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteAllPdfs() {
        new Thread(() -> {
            List<Pdf> pdfs = db.pdfDao().getAllSync();
            for (Pdf p : pdfs) EncryptionUtils.secureDelete(new File(p.uri));
            db.pdfDao().deleteAll();
            runOnUiThread(() -> Toast.makeText(this, R.string.all_pdfs_removed_success, Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void cleanOldTempFiles() {
        deleteFilesInDir(new File(getCacheDir(), "DecryptedPDFs"));
        deleteFilesInDir(new File(getCacheDir(), "ShareBackups"));
    }

    private void deleteFilesInDir(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) EncryptionUtils.secureDelete(f);
        }
    }

    private void migrateLegacyEncryptedFilesIfNeeded() {
        AppExecutors.background().execute(() -> {
            int count = db.pdfDao().countLegacyKeyVersionPdfs();
            if (count == 0) return;
            runOnUiThread(() -> {
                addPdfFab.setEnabled(false);
                migrationProgressDialog = new ProgressDialog(this, R.style.CustomDialogTheme);
                migrationProgressDialog.setMessage(getString(R.string.migrating_encryption));
                migrationProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                migrationProgressDialog.setMax(count);
                migrationProgressDialog.setCancelable(false);
                migrationProgressDialog.show();
            });
            List<Pdf> legacy = db.pdfDao().getLegacyKeyVersionPdfs();
            for (int i = 0; i < legacy.size(); i++) {
                try { migrateSingleFile(legacy.get(i)); } catch (Exception e) { LoggingUtils.logErrorDebug("Migration", e); }
                int finalI = i + 1;
                runOnUiThread(() -> { if (migrationProgressDialog != null) migrationProgressDialog.setProgress(finalI); });
            }
            runOnUiThread(() -> {
                if (migrationProgressDialog != null && migrationProgressDialog.isShowing()) migrationProgressDialog.dismiss();
                addPdfFab.setEnabled(true);
            });
        });
    }

    private void migrateSingleFile(Pdf pdf) throws Exception {
        File enc = new File(pdf.uri);
        File plain = File.createTempFile("migrate_", ".pdf", getCacheDir());
        File newEnc = new File(enc.getParentFile(), enc.getName() + ".new");
        try {
            EncryptionUtils.decryptFileWithKey(KeyManagerUtils.getLegacyKey(), enc, plain);
            EncryptionUtils.encryptFileWithKey(SessionKeyHolder.require(), plain, newEnc);
            EncryptionUtils.replaceFileAtomically(newEnc, enc);
            pdf.keyVersion = 2;
            db.pdfDao().update(pdf);
        } finally {
            EncryptionUtils.secureDelete(plain);
            if (newEnc.exists()) EncryptionUtils.secureDelete(newEnc);
        }
    }

    private void migrateLegacyMetadataIfNeeded() {
        AppExecutors.background().execute(() -> {
            int count = db.pdfDao().countLegacyMetadataVersionPdfs();
            if (count == 0) return;
            List<Pdf> legacy = db.pdfDao().getLegacyMetadataVersionPdfs();
            for (Pdf p : legacy) {
                try {
                    File file = new File(p.uri);
                    File renamed = new File(file.getParentFile(), java.util.UUID.randomUUID() + ".enc");
                    EncryptionUtils.replaceFileAtomically(file, renamed);
                    p.title = EncryptionUtils.encryptString(SessionKeyHolder.require(), p.title);
                    p.uri = renamed.getAbsolutePath();
                    p.metadataVersion = 2;
                    db.pdfDao().update(p);
                } catch (Exception e) { LoggingUtils.logErrorDebug("MetadataMigration", e); }
            }
        });
    }

    private void ensureReadPermission() {
        if (!checkReadPermission()) requestReadPermission();
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

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (currentEncryptionTask != null) currentEncryptionTask.cancelDueToLifecycle();
        super.onDestroy();
    }
}
