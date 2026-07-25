package org.ferreiratechlab.leitordepdfseguro.utils;

import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyManagerUtils {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "PDFShieldEncryptionKey_V2";
    private static final String BIOMETRIC_KEY_ALIAS = "PDFShieldBiometricKey";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    public static final int DEK_SIZE_BYTES = 32; // 256 bits

    /**
     * Chave legada do Android Keystore, sem gate de autenticação. Mantida
     * apenas para decifrar arquivos .enc criados antes desta versão
     * (Pdf#keyVersion == 1) durante a migração para a DEK amarrada ao
     * PIN/biometria — ver MainActivity#migrateLegacyEncryptedFiles. Não usar
     * para arquivos novos.
     */
    public static SecretKey getLegacyKey() throws Exception {
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
     * Usada para detectar mudanças na segurança do dispositivo, e para embrulhar/desembrulhar
     * a DEK de arquivos quando o usuário desbloqueia via biometria.
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

    /**
     * Cipher de ENCRYPT pronto para uso com um BiometricPrompt.CryptoObject.
     * Gera um IV aleatório (recuperável depois via cipher.getIV(), já dentro do
     * callback de sucesso do prompt) — use para embrulhar a DEK com a chave
     * biométrica no momento em que a biometria é ativada.
     */
    public static Cipher getBiometricEncryptCipherOrThrow() throws Exception {
        SecretKey bioKey = getOrCreateBiometricKey();
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, bioKey);
        return cipher;
    }

    /** @deprecated use {@link #getBiometricEncryptCipherOrThrow()}; mantido para não quebrar
     * chamadores existentes que só usam o cipher como gate de "a biometria bateu". */
    @Deprecated
    public static Cipher getBiometricCipherOrThrow() throws Exception {
        return getBiometricEncryptCipherOrThrow();
    }

    /**
     * Cipher de DECRYPT pronto para uso com um BiometricPrompt.CryptoObject, já
     * inicializado com o IV exato salvo no momento em que a DEK foi embrulhada
     * com a chave biométrica (GCM exige o IV antes do authenticate(), diferente
     * do modo ENCRYPT que pode gerar um sozinho).
     */
    public static Cipher getBiometricDecryptCipherOrThrow(byte[] iv) throws Exception {
        SecretKey bioKey = getOrCreateBiometricKey();
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, bioKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    public static boolean isBiometricEnrollmentInvalidated(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof KeyPermanentlyInvalidatedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Gera uma nova DEK (Data Encryption Key) aleatória de 256 bits, fora do Keystore. */
    public static byte[] generateDek() {
        byte[] dek = new byte[DEK_SIZE_BYTES];
        new SecureRandom().nextBytes(dek);
        return dek;
    }

    public static final class WrappedBytes {
        public final byte[] ciphertext;
        public final byte[] iv;

        public WrappedBytes(byte[] ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }
    }

    /** Embrulha a DEK com uma KEK derivada do PIN (bytes brutos, não gerenciada pelo Keystore). */
    public static WrappedBytes wrapDekWithPinKek(byte[] dek, byte[] kekBytes) throws Exception {
        SecretKeySpec kek = new SecretKeySpec(kekBytes, "AES");
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new WrappedBytes(cipher.doFinal(dek), iv);
    }

    /** Desembrulha a DEK com uma KEK derivada do PIN digitado no login. */
    public static byte[] unwrapDekWithPinKek(byte[] ciphertext, byte[] iv, byte[] kekBytes) throws Exception {
        SecretKeySpec kek = new SecretKeySpec(kekBytes, "AES");
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }
}
