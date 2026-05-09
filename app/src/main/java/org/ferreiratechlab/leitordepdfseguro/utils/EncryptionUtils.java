package org.ferreiratechlab.leitordepdfseguro.utils;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.os.Build;

public class EncryptionUtils {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // IV recomendado para GCM é 12 bytes
    private static final int TAG_BIT_LENGTH = 128;

    public static void encryptFile(Context context, File inputFile, File outputFile) throws Exception {
        SecretKey secretKey = KeyManagerUtils.getOrCreateKey();
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

        try (InputStream inputStream = new FileInputStream(inputFile);
             OutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            
            // Grava o IV nos primeiros bytes do arquivo
            fileOutputStream.write(iv);

            try (CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    cipherOutputStream.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    public static void decryptFile(Context context, File inputFile, File outputFile) throws Exception {
        SecretKey secretKey = KeyManagerUtils.getOrCreateKey();

        try (InputStream inputStream = new FileInputStream(inputFile)) {
            // Lê o IV do início do arquivo
            byte[] iv = new byte[IV_SIZE];
            int ivRead = inputStream.read(iv);
            if (ivRead != IV_SIZE) {
                throw new Exception("Falha ao ler o IV do arquivo criptografado.");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            try (OutputStream outputStream = new FileOutputStream(outputFile);
                 CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = cipherInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /**
     * Sobrescreve o arquivo com zeros antes de deletá-lo para impedir recuperação de dados.
     */
    public static void secureDelete(File file) {
        if (file == null || !file.exists()) return;

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw")) {
            long length = raf.length();
            byte[] zeros = new byte[8192];
            long pos = 0;
            while (pos < length) {
                int toWrite = (int) Math.min(zeros.length, length - pos);
                raf.write(zeros, 0, toWrite);
                pos += toWrite;
            }
            raf.getFD().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            boolean deleted = file.delete();
            if (!deleted) {
                System.out.println("Falha ao deletar arquivo após sobrescrever: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Tenta sobrescrever o conteúdo de um Uri com zeros e depois deletá-lo.
     * Útil para arquivos selecionados via Storage Access Framework.
     */
    public static void secureDelete(Context context, android.net.Uri uri) {
        if (uri == null) return;

        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "rw")) {
            if (pfd != null) {
                long length = pfd.getStatSize();
                if (length > 0) {
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor())) {
                        byte[] zeros = new byte[8192];
                        long pos = 0;
                        while (pos < length) {
                            int toWrite = (int) Math.min(zeros.length, length - pos);
                            fos.write(zeros, 0, toWrite);
                            pos += toWrite;
                        }
                        pfd.getFileDescriptor().sync();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Tenta deletar o documento via DocumentsContract
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                DocumentsContract.deleteDocument(context.getContentResolver(), uri);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
