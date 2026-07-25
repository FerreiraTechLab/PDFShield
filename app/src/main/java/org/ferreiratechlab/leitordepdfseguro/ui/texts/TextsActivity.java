package org.ferreiratechlab.leitordepdfseguro.ui.texts;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;
import org.ferreiratechlab.leitordepdfseguro.ui.auth.ReAuthHelper;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public class TextsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT_TEXTS = 10;
    private static final int REQUEST_CODE_EXPORT_TEXTS = 11;

    /**
     * Separador de entradas no .txt de import/export. Não usa mais vírgula porque o usuário
     * pode ter uma vírgula legítima dentro do próprio texto salvo.
     */
    private static final String ENTRY_TERMINATOR = "***/**/***";

    /** Tempo até limpar sozinho o clipboard depois de copiar um item salvo. */
    private static final long CLIPBOARD_CLEAR_DELAY_MS = 45_000L;

    /**
     * Um link não deveria ter quebra de linha; um texto/nota comum pode. Usado só para decidir
     * se uma quebra de linha é rejeitada (link malformado) ou aceita (nota de várias linhas).
     */
    private static final Pattern LINK_PATTERN = Pattern.compile("^(?:https?://|ftp://|www\\.)\\S+$", Pattern.CASE_INSENSITIVE);

    private static boolean looksLikeLink(String candidate) {
        return LINK_PATTERN.matcher(candidate).matches();
    }

    /** Só rejeita quebra de linha quando o conteúdo parece um link — notas comuns podem ter. */
    private static boolean hasUnsupportedLineBreak(String candidate) {
        return candidate.contains("\n") && looksLikeLink(candidate);
    }

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private RecyclerView recyclerView;
    private View emptyState;
    private EditText searchEditText;
    private TextsAdapter adapter;
    private Executor executor;

    private AppDatabase db;
    private TextsViewModel textsViewModel;

    private final List<TextsAdapter.TextEntry> allEntries = new ArrayList<>();
    private final Handler clipboardClearHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_texts);

        executor = ContextCompat.getMainExecutor(this);
        db = AppDatabase.getInstance(this);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        recyclerView = findViewById(R.id.text_recycler_view);
        emptyState = findViewById(R.id.empty_state);
        searchEditText = findViewById(R.id.search_edit_text);
        Toolbar toolbar = findViewById(R.id.toolbar);
        FloatingActionButton addTextFab = findViewById(R.id.add_text_fab);
        FloatingActionButton clearAllFab = findViewById(R.id.clear_all_texts_fab);

        setSupportActionBar(toolbar);
        toolbar.getNavigationIcon().setColorFilter(getResources().getColor(R.color.white), android.graphics.PorterDuff.Mode.SRC_ATOP);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        adapter = new TextsAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        adapter.setOnTextClickListener(this::copyToClipboard);
        adapter.setOnTextLongClickListener(this::confirmDeleteText);

        addTextFab.setOnClickListener(v -> showAddTextDialog());
        clearAllFab.setOnClickListener(v -> confirmClearAll());

        setupSearch();
        setupDrawer();

        textsViewModel = new ViewModelProvider(this).get(TextsViewModel.class);
        AppExecutors.background().execute(() -> {
            textsViewModel.init(db);
            runOnUiThread(() -> textsViewModel.getTexts().observe(this, savedTexts -> {
                AppExecutors.background().execute(() -> {
                    List<TextsAdapter.TextEntry> entries = new ArrayList<>();
                    for (SavedText savedText : savedTexts) {
                        entries.add(new TextsAdapter.TextEntry(savedText.id, TextsViewModel.toDisplayString(savedText)));
                    }
                    runOnUiThread(() -> {
                        allEntries.clear();
                        allEntries.addAll(entries);
                        applySearchFilter(searchEditText.getText().toString());
                    });
                });
            }));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        checkClipboardForNewText();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void setupDrawer() {
        navigationView.setNavigationItemSelectedListener((MenuItem item) -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);
            if (id == R.id.nav_switch_to_pdfs) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_import_texts) {
                openImportPicker();
                return true;
            } else if (id == R.id.nav_export_texts) {
                ReAuthHelper.authenticateAction(this, executor, this::showExportWarningDialog);
                return true;
            } else if (id == R.id.nav_check_clipboard) {
                checkClipboardForNewText();
                return true;
            }
            return false;
        });
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearchFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void applySearchFilter(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        List<TextsAdapter.TextEntry> filtered = new ArrayList<>();
        for (TextsAdapter.TextEntry entry : allEntries) {
            if (normalizedQuery.isEmpty() || entry.content.toLowerCase().contains(normalizedQuery)) {
                filtered.add(entry);
            }
        }
        adapter.updateEntries(filtered);
        updateEmptyState();
    }

    private void updateEmptyState() {
        emptyState.setVisibility(allEntries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void copyToClipboard(TextsAdapter.TextEntry entry) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", entry.content));
        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
        scheduleClipboardClear(entry.content);
    }

    /**
     * Limpa o clipboard sozinho um tempo depois de copiar, pra um conteúdo sensível não ficar
     * disponível pra qualquer app indefinidamente. Só limpa se o clipboard ainda tiver
     * exatamente o que copiamos — se o usuário copiou outra coisa nesse meio tempo, não mexe.
     */
    private void scheduleClipboardClear(String copiedContent) {
        clipboardClearHandler.postDelayed(() -> {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData currentClip = clipboardManager.getPrimaryClip();
            if (currentClip != null && currentClip.getItemCount() > 0) {
                CharSequence currentText = currentClip.getItemAt(0).getText();
                if (currentText != null && currentText.toString().equals(copiedContent)) {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            }
        }, CLIPBOARD_CLEAR_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clipboardClearHandler.removeCallbacksAndMessages(null);
    }

    private void confirmDeleteText(TextsAdapter.TextEntry entry) {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.delete_text_title)
                .setMessage(R.string.delete_text_msg)
                .setPositiveButton(R.string.remove, (dialog, which) -> ReAuthHelper.authenticateAction(this, executor, () ->
                        AppExecutors.background().execute(() -> textsViewModel.deleteText(entry.id))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.clear_all_texts_title)
                .setMessage(R.string.clear_all_texts_msg)
                .setPositiveButton(R.string.remove, (dialog, which) -> ReAuthHelper.authenticateAction(this, executor, () ->
                        AppExecutors.background().execute(() -> textsViewModel.deleteAllTexts())))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAddTextDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_text, null);
        EditText input = dialogView.findViewById(R.id.newTextEditText);
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (dialog, which) -> trySaveManualText(input.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void trySaveManualText(String rawText) {
        String candidate = rawText == null ? "" : rawText.trim();
        if (candidate.isEmpty()) {
            Toast.makeText(this, R.string.text_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasUnsupportedLineBreak(candidate)) {
            Toast.makeText(this, R.string.multiline_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        AppExecutors.background().execute(() -> {
            if (TextsViewModel.alreadyExists(db.textDao(), candidate)) {
                runOnUiThread(() -> Toast.makeText(this, R.string.text_already_saved, Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                textsViewModel.saveText(candidate);
                clearClipboard();
                runOnUiThread(() -> Toast.makeText(this, R.string.text_saved, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("TextsActivity", e);
            }
        });
    }

    /**
     * Verifica a área de transferência ao abrir/retomar a tela (req. do usuário: "verifica se
     * há alguma coisa na área de transferência"). Leitura + decrypt-compare em background
     * (ClipData.Item#coerceToText pode fazer I/O síncrono para clips não-textuais).
     */
    private void checkClipboardForNewText() {
        AppExecutors.background().execute(() -> {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clipData = clipboardManager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                return;
            }
            if (clipData.getDescription() == null || !clipData.getDescription().hasMimeType("text/*")) {
                return;
            }
            CharSequence coerced = clipData.getItemAt(0).coerceToText(this);
            String candidate = coerced == null ? "" : coerced.toString().trim();
            if (candidate.isEmpty() || hasUnsupportedLineBreak(candidate)) {
                return;
            }
            if (TextsViewModel.alreadyExists(db.textDao(), candidate)) {
                return;
            }
            runOnUiThread(() -> showClipboardSaveDialog(candidate));
        });
    }

    private void showClipboardSaveDialog(String candidate) {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.clipboard_save_title)
                .setMessage(getString(R.string.clipboard_save_msg, candidate))
                .setPositiveButton(R.string.yes, (dialog, which) -> AppExecutors.background().execute(() -> {
                    try {
                        textsViewModel.saveText(candidate);
                        clearClipboard();
                        runOnUiThread(() -> Toast.makeText(this, R.string.text_saved, Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        LoggingUtils.logErrorDebug("TextsActivity", e);
                    }
                }))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void clearClipboard() {
        runOnUiThread(() -> {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
        });
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_TEXTS);
    }

    private void showExportWarningDialog() {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.security_warning_title)
                .setMessage(R.string.export_texts_warning_msg)
                .setPositiveButton(R.string.ok, (dialog, which) -> openExportPicker())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openExportPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "textos_pdfshield.txt");
        startActivityForResult(intent, REQUEST_CODE_EXPORT_TEXTS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_CODE_IMPORT_TEXTS) {
            importTextsFrom(uri);
        } else if (requestCode == REQUEST_CODE_EXPORT_TEXTS) {
            exportTextsTo(uri);
        }
    }

    /**
     * Lê o arquivo inteiro como uma única string. As entradas não são mais separadas por
     * quebra de linha (uma nota salva pode ter \n de verdade) — só pelo ENTRY_TERMINATOR.
     */
    private static String readAll(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private void importTextsFrom(Uri uri) {
        AppExecutors.background().execute(() -> {
            int imported = 0;
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                String wholeFile = readAll(inputStream);
                String[] rawEntries = wholeFile.split(Pattern.quote(ENTRY_TERMINATOR));
                for (String rawEntry : rawEntries) {
                    String trimmed = rawEntry.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    // \n literal (duas letras) no arquivo vira quebra de linha real na nota.
                    String candidate = trimmed.replace("\\n", "\n");
                    if (!TextsViewModel.alreadyExists(db.textDao(), candidate)) {
                        textsViewModel.saveText(candidate);
                        imported++;
                    }
                }
                int finalImported = imported;
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.import_texts_success, finalImported), Toast.LENGTH_SHORT).show();
                    showDeleteImportSourceDialog(uri);
                });
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("TextsActivity", e);
                runOnUiThread(() -> Toast.makeText(this, R.string.import_export_error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * O .txt importado continua existindo em texto puro onde o usuário o escolheu — oferece
     * apagar com segurança agora que o conteúdo já está criptografado no banco.
     */
    private void showDeleteImportSourceDialog(Uri uri) {
        new AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.delete_import_source_title)
                .setMessage(R.string.delete_import_source_msg)
                .setPositiveButton(R.string.yes, (dialog, which) -> AppExecutors.background().execute(() -> EncryptionUtils.secureDelete(this, uri)))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void exportTextsTo(Uri uri) {
        AppExecutors.background().execute(() -> {
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                for (SavedText savedText : db.textDao().getAll()) {
                    String content = TextsViewModel.toDisplayString(savedText);
                    // Quebra de linha real vira \n literal, para não se confundir com o
                    // terminador de entrada nem com quebras de linha só de formatação do arquivo.
                    writer.write(content.replace("\n", "\\n"));
                    writer.write(ENTRY_TERMINATOR);
                    writer.write("\n");
                }
                runOnUiThread(() -> Toast.makeText(this, R.string.export_texts_success, Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                LoggingUtils.logErrorDebug("TextsActivity", e);
                runOnUiThread(() -> Toast.makeText(this, R.string.import_export_error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
