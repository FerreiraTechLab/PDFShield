package org.ferreiratechlab.leitordepdfseguro.utils;

import android.content.Context;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    public static void encryptFile(Context context, File inputFile, File outputFile) throws Exception {
        SecretKey secretKey = KeyManagerUtils.getOrCreateKey(context);
        doCrypto(Cipher.ENCRYPT_MODE, secretKey, inputFile, outputFile);
    }

    public static void decryptFile(Context context, File inputFile, File outputFile) throws Exception {
        SecretKey secretKey = KeyManagerUtils.getKeyFromSharedPreferences(context);
        doCrypto(Cipher.DECRYPT_MODE, secretKey, inputFile, outputFile);
    }

    private static void doCrypto(int cipherMode, SecretKey secretKey, File inputFile, File outputFile) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(cipherMode, secretKey);

        FileInputStream inputStream = new FileInputStream(inputFile);
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);

        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            cipherOutputStream.write(buffer, 0, bytesRead);
        }

        inputStream.close();
        cipherOutputStream.close();
    }
}
