package org.ferreiratechlab.leitordepdfseguro.ui.welcome;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.ui.auth.PinEntryActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.auth.PinSetupActivity;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
public class WelcomeActivity extends AppCompatActivity {

    private Button authButton;
    private View loadingBar;
    private View biometricContainer;
    private SwitchCompat biometricSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_welcome);

        authButton = findViewById(R.id.authenticate_button);
        loadingBar = findViewById(R.id.loading_bar);
        biometricContainer = findViewById(R.id.biometric_toggle_container);
        biometricSwitch = findViewById(R.id.biometric_switch);

        checkAuthStatus();
    }

    private void checkAuthStatus() {
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        PinSecurityUtils.migrateLegacyPinIfNeeded(prefs);

        if (!PinSecurityUtils.hasConfiguredPin(prefs)) {
            new Handler().postDelayed(() -> {
                startActivity(new Intent(WelcomeActivity.this, PinSetupActivity.class));
                finish();
            }, 2000);
        } else {
            new Handler().postDelayed(() -> {
                loadingBar.setVisibility(View.GONE);
                authButton.setVisibility(View.VISIBLE);
                
                setupBiometricToggle(prefs);
                
                authButton.setOnClickListener(v -> handleLogin(prefs));
            }, 1500);
        }
    }

    private void setupBiometricToggle(SharedPreferences prefs) {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS || 
            canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            
            biometricContainer.setVisibility(View.VISIBLE);
            boolean isCurrentlyEnabled = prefs.getBoolean("UseBiometrics", false);

            if (isCurrentlyEnabled && !isBiometricKeyAvailable()) {
                prefs.edit().putBoolean("UseBiometrics", false).apply();
                isCurrentlyEnabled = false;
                Toast.makeText(this, R.string.biometric_invalidated, Toast.LENGTH_LONG).show();
            }
            
            biometricSwitch.setOnCheckedChangeListener(null);
            biometricSwitch.setChecked(isCurrentlyEnabled);
            
            biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    showPinVerificationDialog(() -> {
                        try {
                            // Cria a chave vinculada à biometria para monitorar mudanças
                            KeyManagerUtils.getOrCreateBiometricKey();
                            prefs.edit().putBoolean("UseBiometrics", true).apply();
                            Toast.makeText(this, R.string.link_biometrics, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }, () -> {
                        biometricSwitch.setOnCheckedChangeListener(null);
                        biometricSwitch.setChecked(false);
                        setupBiometricToggle(prefs);
                    });
                } else {
                    prefs.edit().putBoolean("UseBiometrics", false).apply();
                }
            });
        }
    }

    private void showPinVerificationDialog(Runnable onSuccess, Runnable onCancel) {
        SharedPreferences prefs = getSharedPreferences("AuthPrefs", MODE_PRIVATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pin_verify, null);
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        List<String> inputPin = new ArrayList<>();
        View[] dots = new View[6];
        LinearLayout dotContainer = dialogView.findViewById(R.id.dialog_dot_container);
        for (int i = 0; i < 6; i++) {
            dots[i] = dotContainer.getChildAt(i);
        }

        int[] buttonIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : buttonIds) {
            dialogView.findViewById(id).setOnClickListener(v -> {
                if (inputPin.size() < 6) {
                    inputPin.add(((TextView) v).getText().toString());
                    for (int i = 0; i < 6; i++) {
                        dots[i].setBackgroundResource(i < inputPin.size() ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
                    }
                    if (inputPin.size() == 6) {
                        if (PinSecurityUtils.verifyAndMigratePin(prefs, String.join("", inputPin))) {
                            dialog.dismiss();
                            onSuccess.run();
                        } else {
                            Toast.makeText(this, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
                            inputPin.clear();
                            for (int i = 0; i < 6; i++) {
                                dots[i].setBackgroundResource(R.drawable.pin_dot_empty);
                            }
                        }
                    }
                }
            });
        }

        dialogView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            if (!inputPin.isEmpty()) {
                inputPin.remove(inputPin.size() - 1);
                for (int i = 0; i < 6; i++) {
                    dots[i].setBackgroundResource(i < inputPin.size() ? R.drawable.pin_dot_filled : R.drawable.pin_dot_empty);
                }
            }
        });

        dialog.setOnCancelListener(d -> onCancel.run());
        dialog.show();
    }

    private void handleLogin(SharedPreferences prefs) {
        boolean useBiometrics = prefs.getBoolean("UseBiometrics", false);
        
        if (useBiometrics) {
            try {
                Cipher cipher = KeyManagerUtils.getBiometricCipherOrThrow();
                
                triggerBiometrics(cipher);
            } catch (Exception e) {
                if (KeyManagerUtils.isBiometricEnrollmentInvalidated(e)) {
                    prefs.edit().putBoolean("UseBiometrics", false).apply();
                    Toast.makeText(this, R.string.biometric_invalidated, Toast.LENGTH_LONG).show();
                    
                    biometricSwitch.setOnCheckedChangeListener(null);
                    biometricSwitch.setChecked(false);
                    setupBiometricToggle(prefs);
                }

                startActivity(new Intent(this, PinEntryActivity.class));
                finish();
            }
        } else {
            startActivity(new Intent(this, PinEntryActivity.class));
            finish();
        }
    }

    private void triggerBiometrics(Cipher cipher) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                startActivity(new Intent(WelcomeActivity.this, PinEntryActivity.class));
                finish();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.biometric_auth_subtitle))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        // Passa o Cipher vinculado à chave biométrica
        biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
    }

    private boolean isBiometricKeyAvailable() {
        try {
            KeyManagerUtils.getBiometricCipherOrThrow();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
