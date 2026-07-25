package org.ferreiratechlab.leitordepdfseguro.ui.auth;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;

/**
 * Re-autenticação (PIN ou biometria) exigida antes de ações destrutivas/sensíveis dentro do
 * app já desbloqueado (excluir, backup, limpar tudo). Extraído de MainActivity para ser
 * reaproveitado por qualquer tela do app (PDFs, Textos) sem duplicar o fluxo.
 */
public final class ReAuthHelper {

    private ReAuthHelper() {
    }

    public static void authenticateAction(AppCompatActivity activity, Executor executor, Runnable onAuthenticated) {
        SharedPreferences prefs = activity.getSharedPreferences("AuthPrefs", AppCompatActivity.MODE_PRIVATE);
        boolean useBiometrics = prefs.getBoolean("UseBiometrics", false);

        if (useBiometrics) {
            BiometricManager biometricManager = BiometricManager.from(activity);
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                final Cipher cipher;
                try {
                    cipher = KeyManagerUtils.getBiometricCipherOrThrow();
                } catch (Exception e) {
                    if (KeyManagerUtils.isBiometricEnrollmentInvalidated(e)) {
                        prefs.edit().putBoolean("UseBiometrics", false).apply();
                        Toast.makeText(activity, R.string.biometric_invalidated, Toast.LENGTH_LONG).show();
                    }
                    showPinVerificationDialog(activity, onAuthenticated);
                    return;
                }

                BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        activity.runOnUiThread(onAuthenticated);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // Se falhar biometria, tenta o PIN interno
                        showPinVerificationDialog(activity, onAuthenticated);
                    }
                });

                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(activity.getString(R.string.biometric_auth_title))
                        .setSubtitle(activity.getString(R.string.auth_required_action))
                        .setNegativeButtonText(activity.getString(R.string.enter_pin))
                        .build();

                biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
                return;
            }
        }

        // Se não usar biometria ou sensor não disponível, pede PIN
        showPinVerificationDialog(activity, onAuthenticated);
    }

    private static void showPinVerificationDialog(AppCompatActivity activity, Runnable onSuccess) {
        SharedPreferences prefs = activity.getSharedPreferences("AuthPrefs", AppCompatActivity.MODE_PRIVATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomDialogTheme);
        View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_pin_verify, null);
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
                            Toast.makeText(activity, R.string.invalid_pin, Toast.LENGTH_SHORT).show();
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

        dialog.show();
    }
}
