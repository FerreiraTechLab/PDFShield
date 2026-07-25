package org.ferreiratechlab.leitordepdfseguro.ui.texts;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;
import org.ferreiratechlab.leitordepdfseguro.ui.auth.ReAuthHelper;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;

import java.util.concurrent.Executor;

public class TextNoteActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "extra_note_id";
    private static final long CLIPBOARD_CLEAR_DELAY_MS = 45_000L;

    private EditText noteEditText;
    private TextsViewModel viewModel;
    private int noteId = -1;
    private Executor executor;
    private final Handler clipboardClearHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_text_note);

        executor = ContextCompat.getMainExecutor(this);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        noteEditText = findViewById(R.id.note_edit_text);
        viewModel = new ViewModelProvider(this).get(TextsViewModel.class);
        viewModel.init(AppDatabase.getInstance(this));

        noteId = getIntent().getIntExtra(EXTRA_NOTE_ID, -1);
        if (noteId != -1) {
            setTitle(R.string.edit_note);
            loadNote();
        } else {
            setTitle(R.string.new_long_note);
        }
    }

    private void loadNote() {
        AppExecutors.background().execute(() -> {
            SavedText note = viewModel.getNoteById(noteId);
            if (note != null) {
                String plainText = TextsViewModel.toDisplayString(note);
                runOnUiThread(() -> noteEditText.setText(plainText));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.text_note_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_save) {
            saveNote();
            return true;
        } else if (id == R.id.action_copy) {
            copyToClipboard();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveNote() {
        String content = noteEditText.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.text_empty_error, Toast.LENGTH_SHORT).show();
            return;
        }

        ReAuthHelper.authenticateAction(this, executor, () -> {
            AppExecutors.background().execute(() -> {
                try {
                    if (noteId != -1) {
                        viewModel.updateText(noteId, content);
                    } else {
                        viewModel.saveText(content);
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.note_saved_success, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("TextNoteActivity", e);
                    runOnUiThread(() -> Toast.makeText(this, R.string.pin_setup_error, Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

    private void copyToClipboard() {
        String content = noteEditText.getText().toString();
        if (content.isEmpty()) return;

        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", content));
        Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show();
        scheduleClipboardClear(content);
    }

    private void scheduleClipboardClear(String copiedContent) {
        clipboardClearHandler.removeCallbacksAndMessages(null);
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
}
