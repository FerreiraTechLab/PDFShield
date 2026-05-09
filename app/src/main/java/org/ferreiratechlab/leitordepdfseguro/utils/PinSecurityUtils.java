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

    private static final int DEFAULT_ITERATIONS = 120000;
    private static final int SALT_SIZE_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

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