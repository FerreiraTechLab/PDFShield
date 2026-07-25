package org.ferreiratechlab.leitordepdfseguro.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.texts.TextsActivity;

/**
 * Tela pós-login que deixa escolher entre o cofre de PDFs e o de Textos salvos. Sempre
 * finaliza a si mesma ao abrir um dos dois, mesma convenção já usada em toda a cadeia de
 * login (Welcome/PinEntry/PinSetup) — voltar nunca deve reexpor uma tela de autenticação.
 */
public class HomeActivity extends AppCompatActivity {

    private TextView countPdfsText;
    private TextView countTextsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_home);

        countPdfsText = findViewById(R.id.count_pdfs);
        countTextsText = findViewById(R.id.count_texts);

        findViewById(R.id.card_pdfs).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.card_texts).setOnClickListener(v -> {
            startActivity(new Intent(this, TextsActivity.class));
            finish();
        });

        // Animações de entrada para um toque mais profissional
        findViewById(R.id.card_pdfs).startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        findViewById(R.id.card_texts).startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));

        observeCounts();
    }

    private void observeCounts() {
        AppDatabase db = AppDatabase.getInstance(this);
        
        db.pdfDao().getCount().observe(this, count -> {
            countPdfsText.setText(getString(R.string.home_summary_pdfs, count));
        });

        db.textDao().getCount().observe(this, count -> {
            countTextsText.setText(getString(R.string.home_summary_texts, count));
        });
    }
}
