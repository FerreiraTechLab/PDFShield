package org.ferreiratechlab.leitordepdfseguro.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class PinEntryActivity extends AppCompatActivity {

    private List<String> pin = new ArrayList<>();
    private View[] dots = new View[6];
    private String savedPin;
    private Executor executor;
    private BiometricPrompt biometricPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_entry);

        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        savedPin = prefs.getString("AppPin", null);

        View dotContainer = findViewById(R.id.dot_container);
        for (int i = 0; i < 6; i++) {
            dots[i] = ((android.view.ViewGroup) dotContainer).getChildAt(i);
        }

        setupNumpad();
        setupBiometrics();
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

    private void setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this);
        BiometricManager biometricManager = BiometricManager.from(this);
        
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            View btnAction = findViewById(R.id.btn_action);
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setOnClickListener(v -> authenticateBiometrically());

            biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    onAuthSuccess();
                }
            });
            
            // Inicia biometria automaticamente
            authenticateBiometrically();
        }
    }

    private void authenticateBiometrically() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.enter_pin))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();
        biometricPrompt.authenticate(promptInfo);
    }

    private void addDigit(String digit) {
        if (pin.size() < 6) {
            pin.add(digit);
            updateDots();

            if (pin.size() == 6) {
                if (String.join("", pin).equals(savedPin)) {
                    onAuthSuccess();
                } else {
                    Toast.makeText(this, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
                    pin.clear();
                    updateDots();
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

    private void onAuthSuccess() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
