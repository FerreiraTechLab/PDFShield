package org.ferreiratechlab.leitordepdfseguro.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

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

        // Gera a DEK de criptografia de arquivos e a embrulha com uma chave derivada
        // deste PIN. Sem isso, MainActivity não conseguiria descriptografar nada
        // (EncryptionUtils exige uma DEK carregada em SessionKeyHolder).
        byte[] dek;
        try {
            dek = PinSecurityUtils.createAndWrapDekForPin(prefs, pinStr);
            SessionKeyHolder.set(dek);
        } catch (Exception e) {
            LoggingUtils.logErrorDebug("PinSetup", e);
            Toast.makeText(this, R.string.pin_setup_error, Toast.LENGTH_LONG).show();
            return;
        }

        // Pergunta se deseja vincular biometria
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.link_biometrics);
        builder.setMessage(R.string.link_biometrics_msg);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> enrollBiometricsAndFinish(prefs, dek));
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            prefs.edit().putBoolean("UseBiometrics", false).apply();
            goToMain();
        });
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Dispara um BiometricPrompt real (ENCRYPT_MODE) para embrulhar a DEK com a chave
     * biométrica do Keystore, para que o desbloqueio por biometria (WelcomeActivity /
     * PinEntryActivity) consiga destravar os arquivos sem nunca ter o PIN em texto.
     */
    private void enrollBiometricsAndFinish(SharedPreferences prefs, byte[] dek) {
        Cipher cipher;
        try {
            cipher = KeyManagerUtils.getBiometricEncryptCipherOrThrow();
        } catch (Exception e) {
            LoggingUtils.logErrorDebug("PinSetup", e);
            prefs.edit().putBoolean("UseBiometrics", false).apply();
            goToMain();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                try {
                    Cipher authedCipher = result.getCryptoObject().getCipher();
                    byte[] ciphertext = authedCipher.doFinal(dek);
                    byte[] iv = authedCipher.getIV();
                    PinSecurityUtils.saveBiometricWrappedDek(prefs, new KeyManagerUtils.WrappedBytes(ciphertext, iv));
                    prefs.edit().putBoolean("UseBiometrics", true).apply();
                } catch (Exception e) {
                    LoggingUtils.logErrorDebug("PinSetup", e);
                    prefs.edit().putBoolean("UseBiometrics", false).apply();
                }
                goToMain();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                prefs.edit().putBoolean("UseBiometrics", false).apply();
                goToMain();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.link_biometrics))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();
        biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }

    private void goToMain() {
        Toast.makeText(this, R.string.pin_setup_success, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, org.ferreiratechlab.leitordepdfseguro.ui.home.HomeActivity.class));
        finish();
    }
}
