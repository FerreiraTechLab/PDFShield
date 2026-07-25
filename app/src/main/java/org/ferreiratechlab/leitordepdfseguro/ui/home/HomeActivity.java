package org.ferreiratechlab.leitordepdfseguro.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.texts.TextsActivity;

/**
 * Tela pós-login que deixa escolher entre o cofre de PDFs e o de Textos salvos. Sempre
 * finaliza a si mesma ao abrir um dos dois, mesma convenção já usada em toda a cadeia de
 * login (Welcome/PinEntry/PinSetup) — voltar nunca deve reexpor uma tela de autenticação.
 */
public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_home);

        findViewById(R.id.open_pdfs_button).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.open_texts_button).setOnClickListener(v -> {
            startActivity(new Intent(this, TextsActivity.class));
            finish();
        });
    }
}
