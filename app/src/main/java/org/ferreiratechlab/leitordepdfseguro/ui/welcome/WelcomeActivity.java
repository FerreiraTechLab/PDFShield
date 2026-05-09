package org.ferreiratechlab.leitordepdfseguro.ui.welcome;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.ferreiratechlab.leitordepdfseguro.R;
import org.ferreiratechlab.leitordepdfseguro.ui.main.MainActivity;

import java.util.concurrent.Executor;

public class WelcomeActivity extends AppCompatActivity {

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private Executor executor;
    private static final int REQUEST_CODE_DEVICE_CREDENTIAL = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_welcome);

        Button authenticateButton = findViewById(R.id.authenticate_button);
        authenticateButton.setOnClickListener(v -> {
            initAuthentication();
        });



        executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, authenticationCallback);
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_auth_title))
                .setSubtitle(getString(R.string.biometric_auth_subtitle))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();
        initAuthentication();
    }

    private void initAuthentication() {
        if (isBiometricAvailable()) {
            biometricPrompt.authenticate(promptInfo);
        } else {
            authenticateWithDeviceCredentials();
        }
    }

    private boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void authenticateWithDeviceCredentials() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager.isKeyguardSecure()) {
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                    getString(R.string.biometric_auth_title),
                    getString(R.string.biometric_auth_subtitle));
            if (intent != null) {
                startActivityForResult(intent, REQUEST_CODE_DEVICE_CREDENTIAL);
            }
        } else {
            Toast.makeText(this, R.string.device_security_required, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_DEVICE_CREDENTIAL) {
            if (resultCode == RESULT_OK) {
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, R.string.auth_failed_device, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {
        @Override
        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);
            startActivity(new Intent(WelcomeActivity.this, MainActivity.class));
            finish();
        }

        @Override
        public void onAuthenticationError(int errorCode, CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);
            Toast.makeText(WelcomeActivity.this, R.string.auth_failed_biometric, Toast.LENGTH_SHORT).show();
        }
    };
}
