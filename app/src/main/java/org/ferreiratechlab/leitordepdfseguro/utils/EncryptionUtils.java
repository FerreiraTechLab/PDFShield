package org.ferreiratechlab.leitordepdfseguro.utils;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.os.Build;

public class EncryptionUtils {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12; // IV recomendado para GCM é 12 bytes
    private static final int TAG_BIT_LENGTH = 128;
    private static final int STREAM_BUFFER_SIZE = 262144;

    /**
     * Criptografa com a DEK da sessão atual (ver {@link SessionKeyHolder}), carregada só
     * depois de uma autenticação (PIN ou biometria) bem-sucedida. Lança
     * IllegalStateException (fail-closed) se nenhuma DEK foi carregada ainda — não deve
     * haver caminho de código legítimo que chegue aqui antes do login.
     */
    public static void encryptFile(Context context, File inputFile, File outputFile) throws Exception {
        encryptFileWithKey(SessionKeyHolder.require(), inputFile, outputFile);
    }

    /**
     * Descriptografa com a DEK da sessão atual — ver {@link #encryptFile}. Alguns
     * chamadores (abrir um PDF da lista, backup) não sabem se o arquivo em questão já foi
     * migrado (Pdf#keyVersion) da chave legada do Keystore para a DEK nova — ao invés de
     * espalhar essa checagem por cada chamador, tentamos a DEK nova primeiro e, se a tag de
     * autenticação do GCM não bater (prova criptográfica de que a chave está errada, não uma
     * falha genérica), tentamos a chave legada. Isso cobre exatamente a janela entre o app
     * abrir e MainActivity#migrateLegacyEncryptedFilesIfNeeded terminar de migrar esse arquivo.
     */
    public static void decryptFile(Context context, File inputFile, File outputFile) throws Exception {
        try {
            decryptFileWithKey(SessionKeyHolder.require(), inputFile, outputFile);
        } catch (BadPaddingException e) {
            decryptFileWithKey(KeyManagerUtils.getLegacyKey(), inputFile, outputFile);
        }
    }

    /**
     * Variante que recebe a chave explicitamente, usada pela migração de arquivos
     * legados (ver MainActivity#migrateLegacyEncryptedFiles), que precisa decifrar com
     * {@link KeyManagerUtils#getLegacyKey()} e recifrar com a nova DEK.
     */
    public static void encryptFileWithKey(SecretKey key, File inputFile, File outputFile) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(inputFile), STREAM_BUFFER_SIZE);
             OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile), STREAM_BUFFER_SIZE)) {

            // Grava o IV nos primeiros bytes do arquivo
            fileOutputStream.write(iv);
            processCipher(inputStream, fileOutputStream, cipher);
            fileOutputStream.flush();
        }
    }

    /** Variante que recebe a chave explicitamente — ver {@link #encryptFileWithKey}. */
    public static void decryptFileWithKey(SecretKey key, File inputFile, File outputFile) throws Exception {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(inputFile), STREAM_BUFFER_SIZE)) {
            // Lê o IV do início do arquivo
            byte[] iv = new byte[IV_SIZE];
            int ivRead = inputStream.read(iv);
            if (ivRead != IV_SIZE) {
                throw new Exception("Falha ao ler o IV do arquivo criptografado.");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            try (OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile), STREAM_BUFFER_SIZE)) {
                processCipher(inputStream, fileOutputStream, cipher);
                fileOutputStream.flush();
            }
        }
    }

    /**
     * Criptografa uma string curta (ex.: título de PDF) com a DEK da sessão atual,
     * retornando Base64(IV || ciphertext) numa única string. Usado para os metadados do
     * banco (ver Pdf#title), que não têm conceito de "chave legada" — um valor com
     * Pdf#metadataVersion == 1 está simplesmente em texto puro ainda.
     */
    public static String encryptString(SecretKey key, String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    /** Descriptografa uma string produzida por {@link #encryptString}. */
    public static String decryptString(SecretKey key, String encoded) throws Exception {
        byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(combined, 0, iv, 0, IV_SIZE);
        byte[] ciphertext = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, IV_SIZE, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    /**
     * Move {@code source} para {@code destination}, substituindo um arquivo existente ali se
     * houver, tentando {@link File#renameTo} primeiro (atômico na prática dentro do
     * armazenamento privado do app) e caindo para copiar+fsync+apagar se o rename falhar
     * (ex.: caminhos em volumes diferentes). Usado pelas migrações e pela sobrescrita de PDF
     * duplicado, para nunca deixar {@code destination} num estado parcialmente escrito.
     */
    public static void replaceFileAtomically(File source, File destination) throws Exception {
        if (source.renameTo(destination)) {
            return;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(source), STREAM_BUFFER_SIZE);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            out.getFD().sync();
        }
        secureDelete(source);
    }

    private static void processCipher(InputStream inputStream, OutputStream outputStream, Cipher cipher) throws Exception {
        byte[] inputBuffer = new byte[STREAM_BUFFER_SIZE];
        byte[] outputBuffer = new byte[cipher.getOutputSize(STREAM_BUFFER_SIZE)];
        int bytesRead;
        while ((bytesRead = inputStream.read(inputBuffer)) != -1) {
            int outputBytes = cipher.update(inputBuffer, 0, bytesRead, outputBuffer, 0);
            if (outputBytes > 0) {
                outputStream.write(outputBuffer, 0, outputBytes);
            }
        }

        byte[] finalBuffer = new byte[cipher.getOutputSize(0)];
        int finalBytes = cipher.doFinal(finalBuffer, 0);
        if (finalBytes > 0) {
            outputStream.write(finalBuffer, 0, finalBytes);
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
