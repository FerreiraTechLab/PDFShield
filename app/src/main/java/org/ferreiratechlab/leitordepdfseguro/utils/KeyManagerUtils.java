package org.ferreiratechlab.leitordepdfseguro.utils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import android.util.Base64;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

public class KeyManagerUtils {

    private static final String SHARED_PREF_NAME = "CryptoKeyPrefs";
    private static final String KEY_NAME = "CryptoKey";

    public static SecretKey getOrCreateKey(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String encryptedKeyString = sharedPreferences.getString(KEY_NAME, null);

        SecretKey secretKey;
        if (encryptedKeyString == null) {
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(256);
                secretKey = keyGenerator.generateKey();
                String encodedKey = Base64.encodeToString(secretKey.getEncoded(), Base64.DEFAULT);

                // Save the key in SharedPreferences
                sharedPreferences.edit().putString(KEY_NAME, encodedKey).apply();
                //Toast.makeText(context, "Chave de segurança gerada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            // Decode the existing key
            byte[] decodedKey = Base64.decode(encryptedKeyString, Base64.DEFAULT);
            secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
            //Toast.makeText(context, "Chave de segurança resgatada", Toast.LENGTH_SHORT).show();
        }
        return secretKey;
    }

    public static SecretKey getKeyFromSharedPreferences(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        String encryptedKeyString = sharedPreferences.getString(KEY_NAME, null);

        SecretKey secretKey;
        if (encryptedKeyString == null) {
            //Toast.makeText(context, "Chave de segurança inexistente", Toast.LENGTH_SHORT).show();
            return null; // Key doesn't exist
        } else {
            // Decode the existing key
            byte[] decodedKey = Base64.decode(encryptedKeyString, Base64.DEFAULT);
            secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
        }
        return secretKey;
    }
}
