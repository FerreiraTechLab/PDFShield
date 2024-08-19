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
                .setTitle("Autenticação Biométrica")
                .setSubtitle("Autentique-se para acessar os PDFs")
                .setNegativeButtonText("Cancelar")
                .build();
        initAuthentication();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Fecha a atividade quando ela não está mais visível
        finish();
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
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent("Autenticação Requerida", "Autentique-se para acessar os PDFs");
            if (intent != null) {
                startActivityForResult(intent, REQUEST_CODE_DEVICE_CREDENTIAL);
            }
        } else {
            Toast.makeText(this, "Configuração de segurança do dispositivo necessária. Vá para Configurações.", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "Falha na autenticação com credenciais do dispositivo", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(WelcomeActivity.this, "Falha na autenticação biométrica", Toast.LENGTH_SHORT).show();
        }
    };
}
