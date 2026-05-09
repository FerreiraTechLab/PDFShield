package org.ferreiratechlab.leitordepdfseguro.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils;

import java.util.ArrayList;
import java.util.List;

public class PinSetupActivity extends AppCompatActivity {

    private List<String> pin = new ArrayList<>();
    private List<String> firstPin = new ArrayList<>();
    private boolean isConfirming = false;
    private View[] dots = new View[6];
    private TextView title;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_setup);

        title = findViewById(R.id.setup_title);
        subtitle = findViewById(R.id.setup_subtitle);

        View dotContainer = findViewById(R.id.dot_container);
        for (int i = 0; i < 6; i++) {
            dots[i] = ((android.view.ViewGroup) dotContainer).getChildAt(i);
        }

        setupNumpad();
    }

    private void setupNumpad() {
        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : buttonIds) {
            findViewById(id).setOnClickListener(v -> addDigit(((TextView) v).getText().toString()));
        }

        findViewById(R.id.btn_delete).setOnClickListener(v -> removeDigit());
    }

    private void addDigit(String digit) {
        if (pin.size() < 6) {
            pin.add(digit);
            updateDots();

            if (pin.size() == 6) {
                if (!isConfirming) {
                    firstPin = new ArrayList<>(pin);
                    pin.clear();
                    isConfirming = true;
                    updateUIForConfirmation();
                } else {
                    if (pin.equals(firstPin)) {
                        savePin(String.join("", pin));
                    } else {
                        Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                        pin.clear();
                        updateDots();
                    }
                }
            }
        }
    }

    private void removeDigit() {
        if (!pin.isEmpty()) {
            pin.remove(pin.size() - 1);
            updateDots();
        }
    }

    private void updateDots() {
        for (int i = 0; i < 6; i++) {
            dots[i].setBackgroundResource(i < pin.size() ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
        }
    }

    private void updateUIForConfirmation() {
        title.setText(R.string.confirm_pin);
        updateDots();
    }

    private void savePin(String pinStr) {
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        PinSecurityUtils.savePin(prefs, pinStr);

        // Pergunta se deseja vincular biometria
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.link_biometrics);
        builder.setMessage(R.string.link_biometrics_msg);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            prefs.edit().putBoolean("UseBiometrics", true).apply();
            goToMain();
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            prefs.edit().putBoolean("UseBiometrics", false).apply();
            goToMain();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void goToMain() {
        Toast.makeText(this, R.string.pin_setup_success, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
