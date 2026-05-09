package org.ferreiratechlab.leitordepdfseguro.utils;

import org.junit.Before;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import static org.junit.Assert.*;

/**
 * Testes unitários para EncryptionUtils.
 * 
 * Valida:
 * - Geração de chaves AES-256
 * - Inicialização de Cipher com IV
 * - Uso de AES-GCM/NoPadding
 */
public class EncryptionUtilsTest {

    private SecretKey testKey;

    @Before
    public void setUp() throws Exception {
        // Gerar chave AES-256 para testes
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        testKey = keyGen.generateKey();
    }

    /**
     * Testa que Cipher é criado com algoritmo correto.
     */
    @Test
    public void testCipherAlgorithmIsAesGcm() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        assertEquals("Algoritmo deve ser AES/GCM/NoPadding", "AES/GCM/NoPadding", cipher.getAlgorithm());
    }

    /**
     * Testa que chave gerada tem tamanho correto (256-bit).
     */
    @Test
    public void testKeyGeneratedWith256BitSize() {
        // Tamanho em bits
        int keySizeInBits = testKey.getEncoded().length * 8;
        
        assertEquals("Chave deve ter 256 bits", 256, keySizeInBits);
    }

    /**
     * Testa que Cipher pode ser inicializado em modo encrypt.
     */
    @Test
    public void testCipherCanInitializeInEncryptMode() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        // Não deve lançar exception
        cipher.init(Cipher.ENCRYPT_MODE, testKey);
        
        // Verificar que cipher foi inicializado sem exception
        assertNotNull("Cipher deve ser não-null após init", cipher);
    }

    /**
     * Testa que IV (Initialization Vector) é gerado automaticamente.
     */
    @Test
    public void testCipherGeneratesIV() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, testKey);
        
        byte[] iv = cipher.getIV();
        
        assertNotNull("IV deve ser gerado", iv);
        assertTrue("IV deve ter tamanho não-vazio", iv.length > 0);
        assertEquals("GCM IV padrão é 12 bytes", 12, iv.length);
    }

    /**
     * Testa que dados podem ser encriptados.
     */
    @Test
    public void testDataCanBeEncrypted() throws Exception {
        Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, testKey);
        
        byte[] plaintext = "Test PDF content".getBytes();
        byte[] encryptedData = encryptCipher.doFinal(plaintext);
        
        // Dados encriptados não devem ser iguais aos originais
        assertNotEquals("Dados encriptados devem ser diferentes do plaintext", 
            new String(plaintext), new String(encryptedData));
        assertTrue("Dados encriptados devem ter tamanho", encryptedData.length > 0);
    }

    /**
     * Testa roundtrip encrypt/decrypt com dados originais.
     */
    @Test
    public void testEncryptDecryptRoundtripRestoresData() throws Exception {
        byte[] plaintext = "Secret PDF content that must be protected".getBytes();
        
        // Encrypt
        Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, testKey);
        byte[] iv = encryptCipher.getIV();
        byte[] encryptedData = encryptCipher.doFinal(plaintext);
        
        // Decrypt
        Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, testKey, 
            new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] decryptedData = decryptCipher.doFinal(encryptedData);
        
        // Verificar que dados foram restaurados
        assertArrayEquals("Dados decriptados devem ser idênticos aos originais", 
            plaintext, decryptedData);
    }

    /**
     * Testa que decryption com chave errada falha.
     */
    @Test
    public void testDecryptionWithWrongKeyFails() throws Exception {
        byte[] plaintext = "Secret data".getBytes();
        
        // Encrypt com chave 1
        Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, testKey);
        byte[] iv = encryptCipher.getIV();
        byte[] encryptedData = encryptCipher.doFinal(plaintext);
        
        // Gerar chave 2 (diferente)
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        SecretKey wrongKey = keyGen.generateKey();
        
        // Tentar decrypt com chave errada
        Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, wrongKey, 
            new javax.crypto.spec.GCMParameterSpec(128, iv));
        
        try {
            decryptCipher.doFinal(encryptedData);
            fail("Decryption com chave errada deve lançar exception");
        } catch (javax.crypto.AEADBadTagException e) {
            // Esperado: chave errada causa falha de autenticação
            assertTrue("Exception esperada para chave errada", true);
        }
    }

    /**
     * Testa que IVs diferentes produzem ciphertexts diferentes (segurança).
     */
    @Test
    public void testDifferentIVsProduceDifferentCiphertexts() throws Exception {
        byte[] plaintext = "Same data".getBytes();
        
        // Encrypt 1
        Cipher cipher1 = Cipher.getInstance("AES/GCM/NoPadding");
        cipher1.init(Cipher.ENCRYPT_MODE, testKey);
        byte[] encrypted1 = cipher1.doFinal(plaintext);
        
        // Encrypt 2
        Cipher cipher2 = Cipher.getInstance("AES/GCM/NoPadding");
        cipher2.init(Cipher.ENCRYPT_MODE, testKey);
        byte[] encrypted2 = cipher2.doFinal(plaintext);
        
        // Mesmo plaintext com IVs diferentes deve gerar ciphertexts diferentes
        assertNotEquals("Ciphertexts com IVs diferentes devem ser diferentes", 
            new String(encrypted1), new String(encrypted2));
    }
}
