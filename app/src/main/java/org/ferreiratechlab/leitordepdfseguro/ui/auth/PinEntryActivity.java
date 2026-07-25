package org.ferreiratechlab.leitordepdfseguro.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

import javax.crypto.Cipher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class PinEntryActivity extends AppCompatActivity {

    private List<String> pin = new ArrayList<>();
    private View[] dots = new View[6];
    private int[] numpadButtonIds;
    private TextView lockoutMessage;
    private final Handler lockoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable lockoutTick = this::updateLockoutState;
    private SharedPreferences prefs;
    private Executor executor;
    private BiometricPrompt biometricPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_entry);

        prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        PinSecurityUtils.migrateLegacyPinIfNeeded(prefs);

        View dotContainer = findViewById(R.id.dot_container);
        for (int i = 0; i < 6; i++) {
            dots[i] = ((android.view.ViewGroup) dotContainer).getChildAt(i);
        }
        lockoutMessage = findViewById(R.id.lockout_message);

        setupNumpad();
        setupBiometrics();
        updateLockoutState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lockoutHandler.removeCallbacks(lockoutTick);
    }

    private void setupNumpad() {
        numpadButtonIds = new int[]{
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : numpadButtonIds) {
            findViewById(id).setOnClickListener(v -> addDigit(((TextView) v).getText().toString()));
        }

        findViewById(R.id.btn_delete).setOnClickListener(v -> removeDigit());
    }

    /**
     * Verifica se o PIN está temporariamente bloqueado por excesso de tentativas
     * erradas e ajusta a UI (numpad desabilitado + contagem regressiva).
     */
    private void updateLockoutState() {
        lockoutHandler.removeCallbacks(lockoutTick);
        long remainingMs = PinSecurityUtils.getLockoutRemainingMillis(prefs);
        boolean lockedOut = remainingMs > 0;

        for (int id : numpadButtonIds) {
            findViewById(id).setEnabled(!lockedOut);
        }
        findViewById(R.id.btn_delete).setEnabled(!lockedOut);

        if (lockedOut) {
            long totalSeconds = (remainingMs + 999) / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            lockoutMessage.setText(getString(R.string.pin_locked_out,
                    String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)));
            lockoutMessage.setVisibility(View.VISIBLE);
            lockoutHandler.postDelayed(lockoutTick, 1000);
        } else {
            lockoutMessage.setVisibility(View.GONE);
        }
    }

    private void setupBiometrics() {
        boolean useBiometrics = prefs.getBoolean("UseBiometrics", false);
        if (!useBiometrics) {
            return;
        }

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
                    try {
                        KeyManagerUtils.WrappedBytes wrapped = PinSecurityUtils.getBiometricWrappedDek(prefs);
                        Cipher authedCipher = result.getCryptoObject().getCipher();
                        byte[] dek = authedCipher.doFinal(wrapped.ciphertext);
                        SessionKeyHolder.set(dek);
                        onAuthSuccess();
                    } catch (Exception e) {
                        LoggingUtils.logErrorDebug("PinEntry", e);
                        Toast.makeText(PinEntryActivity.this, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // Inicia biometria automaticamente
            authenticateBiometrically();
        }
    }

    private void authenticateBiometrically() {
        KeyManagerUtils.WrappedBytes wrapped = PinSecurityUtils.getBiometricWrappedDek(prefs);
        if (wrapped == null) {
            // Biometria ligada mas sem DEK embrulhada (estado inconsistente) — cai para o PIN.
            return;
        }

        final Cipher cipher;
        try {
            cipher = KeyManagerUtils.getBiometricDecryptCipherOrThrow(wrapped.iv);
        } catch (Exception e) {
            if (KeyManagerUtils.isBiometricEnrollmentInvalidated(e)) {
                prefs.edit().putBoolean("UseBiometrics", false).apply();
                PinSecurityUtils.clearBiometricWrappedDek(prefs);
                Toast.makeText(this, R.string.biometric_invalidated, Toast.LENGTH_LONG).show();
            }
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.enter_pin))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();
        biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }

    private void addDigit(String digit) {
        if (PinSecurityUtils.isLockedOut(prefs)) {
            updateLockoutState();
            return;
        }
        if (pin.size() < 6) {
            pin.add(digit);
            updateDots();

            if (pin.size() == 6) {
                String enteredPin = String.join("", pin);
                if (PinSecurityUtils.verifyAndMigratePin(prefs, enteredPin)) {
                    try {
                        byte[] dek = PinSecurityUtils.hasPinWrappedDek(prefs)
                                ? PinSecurityUtils.unwrapDekForPin(prefs, enteredPin)
                                // PIN configurado antes desta DEK existir (instalação anterior a
                                // esta atualização) — cria a DEK agora, no primeiro login que a
                                // encontra ausente. Os PDFs antigos são migrados a seguir por
                                // MainActivity#migrateLegacyEncryptedFilesIfNeeded.
                                : PinSecurityUtils.createAndWrapDekForPin(prefs, enteredPin);
                        SessionKeyHolder.set(dek);
                        onAuthSuccess();
                    } catch (Exception e) {
                        LoggingUtils.logErrorDebug("PinEntry", e);
                        Toast.makeText(this, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
                        pin.clear();
                        updateDots();
                    }
                } else {
                    PinSecurityUtils.registerFailedAttempt(prefs);
                    Toast.makeText(this, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
                    pin.clear();
                    updateDots();
                    updateLockoutState();
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
        PinSecurityUtils.resetFailedAttempts(prefs);
        startActivity(new Intent(this, org.ferreiratechlab.leitordepdfseguro.ui.home.HomeActivity.class));
        finish();
    }
}
