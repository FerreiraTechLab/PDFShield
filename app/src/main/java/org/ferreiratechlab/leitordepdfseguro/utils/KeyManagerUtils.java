package org.ferreiratechlab.leitordepdfseguro.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeyManagerUtils {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "PDFShieldEncryptionKey_V2";
    private static final String BIOMETRIC_KEY_ALIAS = "PDFShieldBiometricKey";

    /**
     * Obtém ou cria uma chave AES no Android Keystore.
     * A chave é armazenada de forma segura em hardware (TEE/SE) quando disponível.
     */
    public static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return secretKeyEntry.getSecretKey();
        } else {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false) // Permite fornecer o IV manualmente
                    .build();

            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        }
    }

    /**
     * Cria uma chave vinculada à biometria que é INVALIDADA se novas digitais forem cadastradas.
     * Usada para detectar mudanças na segurança do dispositivo.
     */
    public static SecretKey getOrCreateBiometricKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(BIOMETRIC_KEY_ALIAS, null);
            return secretKeyEntry.getSecretKey();
        } else {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    BIOMETRIC_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    // Esta é a chave da proteção: invalida se cadastrar novo dedo
                    .setInvalidatedByBiometricEnrollment(true);

            keyGenerator.init(builder.build());
            return keyGenerator.generateKey();
        }
    }
}
