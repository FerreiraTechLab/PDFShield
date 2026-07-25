package org.ferreiratechlab.leitordepdfseguro.utils;

import android.content.SharedPreferences;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinSecurityUtils {

    public static final String PREF_PIN_LEGACY = "AppPin";
    public static final String PREF_PIN_HASH = "AppPinHash";
    public static final String PREF_PIN_SALT = "AppPinSalt";
    public static final String PREF_PIN_ITERATIONS = "AppPinIterations";
    public static final String PREF_PIN_VERSION = "AppPinVersion";
    public static final String PREF_FAILED_ATTEMPTS = "PinFailedAttempts";
    public static final String PREF_LOCKOUT_UNTIL = "PinLockoutUntil";
    public static final String PREF_KEY_SALT = "AppKeySalt";
    public static final String PREF_KEY_ITERATIONS = "AppKeyIterations";
    public static final String PREF_DEK_WRAPPED_PIN = "DekWrappedPin";
    public static final String PREF_DEK_IV_PIN = "DekIvPin";
    public static final String PREF_DEK_WRAPPED_BIO = "DekWrappedBio";
    public static final String PREF_DEK_IV_BIO = "DekIvBio";

    private static final int DEFAULT_ITERATIONS = 120000;
    private static final int SALT_SIZE_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    /** Tentativas erradas permitidas antes do primeiro bloqueio temporário. */
    private static final int MAX_ATTEMPTS_BEFORE_LOCKOUT = 5;
    /** Duração de cada bloqueio sucessivo, escalando a cada nova tentativa errada. */
    private static final long[] LOCKOUT_DURATIONS_MS = {
            30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L
    };

    private PinSecurityUtils() {
    }

    public static boolean hasConfiguredPin(SharedPreferences prefs) {
        String hash = prefs.getString(PREF_PIN_HASH, null);
        String salt = prefs.getString(PREF_PIN_SALT, null);
        String legacy = prefs.getString(PREF_PIN_LEGACY, null);
        return (hash != null && salt != null) || legacy != null;
    }

    public static boolean migrateLegacyPinIfNeeded(SharedPreferences prefs) {
        String storedHashB64 = prefs.getString(PREF_PIN_HASH, null);
        String storedSaltB64 = prefs.getString(PREF_PIN_SALT, null);
        String legacyPin = prefs.getString(PREF_PIN_LEGACY, null);

        if (legacyPin == null) {
            return false;
        }

        if (storedHashB64 != null && storedSaltB64 != null) {
            prefs.edit().remove(PREF_PIN_LEGACY).apply();
            return true;
        }

        savePin(prefs, legacyPin);
        return true;
    }

    public static void savePin(SharedPreferences prefs, String pin) {
        byte[] salt = new byte[SALT_SIZE_BYTES];
        new SecureRandom().nextBytes(salt);

        byte[] hash = derivePinHash(pin, salt, DEFAULT_ITERATIONS);
        String hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP);
        String saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP);

        prefs.edit()
                .putString(PREF_PIN_HASH, hashB64)
                .putString(PREF_PIN_SALT, saltB64)
                .putInt(PREF_PIN_ITERATIONS, DEFAULT_ITERATIONS)
                .putInt(PREF_PIN_VERSION, 1)
                .remove(PREF_PIN_LEGACY)
                .apply();
    }

    public static boolean verifyAndMigratePin(SharedPreferences prefs, String inputPin) {
        migrateLegacyPinIfNeeded(prefs);

        String storedHashB64 = prefs.getString(PREF_PIN_HASH, null);
        String storedSaltB64 = prefs.getString(PREF_PIN_SALT, null);

        if (storedHashB64 != null && storedSaltB64 != null) {
            int iterations = prefs.getInt(PREF_PIN_ITERATIONS, DEFAULT_ITERATIONS);
            byte[] storedHash = Base64.decode(storedHashB64, Base64.DEFAULT);
            byte[] salt = Base64.decode(storedSaltB64, Base64.DEFAULT);
            byte[] inputHash = derivePinHash(inputPin, salt, iterations);
            return MessageDigest.isEqual(storedHash, inputHash);
        }
        return false;
    }

    /**
     * Milissegundos restantes de bloqueio (0 se não estiver bloqueado).
     */
    public static long getLockoutRemainingMillis(SharedPreferences prefs) {
        long lockoutUntil = prefs.getLong(PREF_LOCKOUT_UNTIL, 0L);
        return Math.max(0L, lockoutUntil - System.currentTimeMillis());
    }

    public static boolean isLockedOut(SharedPreferences prefs) {
        return getLockoutRemainingMillis(prefs) > 0;
    }

    /**
     * Registra uma tentativa de PIN incorreta. Após {@link #MAX_ATTEMPTS_BEFORE_LOCKOUT}
     * tentativas, aplica um bloqueio temporário que escala a cada nova tentativa errada,
     * para dificultar força bruta via automação de UI.
     */
    public static void registerFailedAttempt(SharedPreferences prefs) {
        int attempts = prefs.getInt(PREF_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = prefs.edit().putInt(PREF_FAILED_ATTEMPTS, attempts);
        if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            int tier = Math.min(attempts - MAX_ATTEMPTS_BEFORE_LOCKOUT, LOCKOUT_DURATIONS_MS.length - 1);
            editor.putLong(PREF_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATIONS_MS[tier]);
        }
        editor.apply();
    }

    public static void resetFailedAttempts(SharedPreferences prefs) {
        prefs.edit()
                .remove(PREF_FAILED_ATTEMPTS)
                .remove(PREF_LOCKOUT_UNTIL)
                .apply();
    }

    /**
     * Gera uma nova DEK (ver {@link KeyManagerUtils#generateDek()}), embrulha com uma
     * KEK derivada do PIN recém-definido e salva o resultado em AuthPrefs. Chamado uma
     * única vez, na criação do PIN (PinSetupActivity). Retorna a DEK em claro para ser
     * carregada imediatamente em {@link SessionKeyHolder}.
     */
    public static byte[] createAndWrapDekForPin(SharedPreferences prefs, String pin) throws Exception {
        byte[] dek = KeyManagerUtils.generateDek();
        wrapDekForPin(prefs, pin, dek);
        return dek;
    }

    /**
     * Embrulha uma DEK já existente com uma KEK derivada do PIN. Usa um salt
     * (PREF_KEY_SALT) separado do salt de verificação do PIN (PREF_PIN_SALT), para que
     * a KEK seja um valor independente do hash usado no login, mesmo derivando do
     * mesmo PIN.
     */
    public static void wrapDekForPin(SharedPreferences prefs, String pin, byte[] dek) throws Exception {
        byte[] salt = new byte[SALT_SIZE_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] kek = derivePinHash(pin, salt, DEFAULT_ITERATIONS);
        KeyManagerUtils.WrappedBytes wrapped = KeyManagerUtils.wrapDekWithPinKek(dek, kek);
        prefs.edit()
                .putString(PREF_KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putInt(PREF_KEY_ITERATIONS, DEFAULT_ITERATIONS)
                .putString(PREF_DEK_WRAPPED_PIN, Base64.encodeToString(wrapped.ciphertext, Base64.NO_WRAP))
                .putString(PREF_DEK_IV_PIN, Base64.encodeToString(wrapped.iv, Base64.NO_WRAP))
                .apply();
    }

    /** Desembrulha a DEK a partir do PIN digitado com sucesso no login. */
    public static byte[] unwrapDekForPin(SharedPreferences prefs, String pin) throws Exception {
        String saltB64 = prefs.getString(PREF_KEY_SALT, null);
        String wrappedB64 = prefs.getString(PREF_DEK_WRAPPED_PIN, null);
        String ivB64 = prefs.getString(PREF_DEK_IV_PIN, null);
        if (saltB64 == null || wrappedB64 == null || ivB64 == null) {
            throw new IllegalStateException("Nenhuma DEK embrulhada para PIN encontrada.");
        }
        int iterations = prefs.getInt(PREF_KEY_ITERATIONS, DEFAULT_ITERATIONS);
        byte[] salt = Base64.decode(saltB64, Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(wrappedB64, Base64.DEFAULT);
        byte[] iv = Base64.decode(ivB64, Base64.DEFAULT);
        byte[] kek = derivePinHash(pin, salt, iterations);
        return KeyManagerUtils.unwrapDekWithPinKek(ciphertext, iv, kek);
    }

    public static boolean hasPinWrappedDek(SharedPreferences prefs) {
        return prefs.getString(PREF_DEK_WRAPPED_PIN, null) != null;
    }

    public static void saveBiometricWrappedDek(SharedPreferences prefs, KeyManagerUtils.WrappedBytes wrapped) {
        prefs.edit()
                .putString(PREF_DEK_WRAPPED_BIO, Base64.encodeToString(wrapped.ciphertext, Base64.NO_WRAP))
                .putString(PREF_DEK_IV_BIO, Base64.encodeToString(wrapped.iv, Base64.NO_WRAP))
                .apply();
    }

    /** @return o blob embrulhado, ou null se a biometria nunca foi vinculada a uma DEK. */
    public static KeyManagerUtils.WrappedBytes getBiometricWrappedDek(SharedPreferences prefs) {
        String ctB64 = prefs.getString(PREF_DEK_WRAPPED_BIO, null);
        String ivB64 = prefs.getString(PREF_DEK_IV_BIO, null);
        if (ctB64 == null || ivB64 == null) {
            return null;
        }
        return new KeyManagerUtils.WrappedBytes(Base64.decode(ctB64, Base64.DEFAULT), Base64.decode(ivB64, Base64.DEFAULT));
    }

    public static void clearBiometricWrappedDek(SharedPreferences prefs) {
        prefs.edit().remove(PREF_DEK_WRAPPED_BIO).remove(PREF_DEK_IV_BIO).apply();
    }

    private static byte[] derivePinHash(String pin, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory;
            try {
                factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            } catch (NoSuchAlgorithmException ignored) {
                factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            }
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao derivar hash do PIN.", e);
        } finally {
            spec.clearPassword();
        }
    }
}