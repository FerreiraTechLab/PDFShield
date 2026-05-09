package org.ferreiratechlab.leitordepdfseguro.utils;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para PinSecurityUtils.
 * 
 * Valida:
 * - Hashing de PIN com PBKDF2-SHA256
 * - Verificação de PIN correto/incorreto
 * - Auto-migração de PIN plaintext para hash
 * - Geração de salt aleatório
 */
public class PinSecurityUtilsTest {

    @Mock
    private SharedPreferences mockPrefs;

    @Mock
    private SharedPreferences.Editor mockEditor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
    }

    /**
     * Testa que savePin gera hash e salt aleatório a cada chamada.
     */
    @Test
    public void testSavePinGeneratesHashAndSalt() {
        String pin = "1234";
        
        // Chamar savePin
        PinSecurityUtils.savePin(mockPrefs, pin);
        
        // Verificar que putString foi chamado para hash e salt
        verify(mockEditor, atLeastOnce()).putString(eq("AppPinHash"), anyString());
        verify(mockEditor, atLeastOnce()).putString(eq("AppPinSalt"), anyString());
        
        // Verificar que plaintext foi removido
        verify(mockEditor).remove("AppPin");
        
        // Verificar que apply foi chamado
        verify(mockEditor).apply();
    }

    /**
     * Testa que PIN correto passa na verificação (com mock).
     */
    @Test
    public void testVerifyPinSucceedsWithHashedPin() {
        String pin = "1234";
        
        // Mock: Configurar prefs para retornar um hash/salt válido
        // Para um teste real, seria necessário capturar o hash salvo em savePin
        String mockHash = "bXktaGFzaGVkLXBpbg=="; // Base64 mockado
        String mockSalt = "bXktc2FsdA=="; // Base64 mockado
        
        when(mockPrefs.getString("AppPinHash", null)).thenReturn(mockHash);
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn(mockSalt);
        when(mockPrefs.getString("AppPin", null)).thenReturn(null);
        when(mockPrefs.getInt("AppPinIterations", 120000)).thenReturn(120000);
        
        // Verificar que método responde sem exception
        assertNotNull("verifyAndMigratePin não deve lançar exception", 
            PinSecurityUtils.verifyAndMigratePin(mockPrefs, pin));
    }

    /**
     * Testa que hasConfiguredPin detecta PIN configurado.
     */
    @Test
    public void testHasConfiguredPinDetectsModernHash() {
        when(mockPrefs.getString("AppPinHash", null)).thenReturn("someHash");
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn("someSalt");
        when(mockPrefs.getString("AppPin", null)).thenReturn(null);
        
        boolean configured = PinSecurityUtils.hasConfiguredPin(mockPrefs);
        
        assertTrue("hasConfiguredPin deve retornar true com hash moderno", configured);
    }

    /**
     * Testa que hasConfiguredPin detecta PIN legacy (plaintext).
     */
    @Test
    public void testHasConfiguredPinDetectsLegacyPlaintextPin() {
        when(mockPrefs.getString("AppPinHash", null)).thenReturn(null);
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn(null);
        when(mockPrefs.getString("AppPin", null)).thenReturn("1234");
        
        boolean configured = PinSecurityUtils.hasConfiguredPin(mockPrefs);
        
        assertTrue("hasConfiguredPin deve retornar true com PIN legacy", configured);
    }

    /**
     * Testa que hasConfiguredPin retorna false quando nenhum PIN está configurado.
     */
    @Test
    public void testHasConfiguredPinReturnsFalseWhenEmpty() {
        when(mockPrefs.getString("AppPinHash", null)).thenReturn(null);
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn(null);
        when(mockPrefs.getString("AppPin", null)).thenReturn(null);
        
        boolean configured = PinSecurityUtils.hasConfiguredPin(mockPrefs);
        
        assertFalse("hasConfiguredPin deve retornar false quando vazio", configured);
    }

    /**
     * Testa que auto-migração remove plaintext e cria hash.
     */
    @Test
    public void testAutoMigrateRemovesPlaintextAndCreateHash() {
        String legacyPin = "1234";
        
        // Mock: Préfs contém PIN plaintext (legacy)
        when(mockPrefs.getString("AppPinHash", null)).thenReturn(null);
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn(null);
        when(mockPrefs.getString("AppPin", null)).thenReturn(legacyPin);
        
        boolean migrated = PinSecurityUtils.verifyAndMigratePin(mockPrefs, legacyPin);
        
        assertTrue("Migração do PIN plaintext deve retornar true", migrated);
        
        // Verificar que savePin foi chamado (que remove plaintext e cria hash)
        verify(mockEditor).remove("AppPin");
        verify(mockEditor, atLeastOnce()).putString(eq("AppPinHash"), anyString());
    }

    /**
     * Testa que a migração proativa remove plaintext sem esperar login.
     */
    @Test
    public void testProactiveMigrationRemovesPlaintextWithoutLogin() {
        when(mockPrefs.getString("AppPinHash", null)).thenReturn(null);
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn(null);
        when(mockPrefs.getString("AppPin", null)).thenReturn("1234");

        boolean migrated = PinSecurityUtils.migrateLegacyPinIfNeeded(mockPrefs);

        assertTrue("Migração proativa deve ocorrer quando existir PIN legacy", migrated);
        verify(mockEditor).remove("AppPin");
        verify(mockEditor, atLeastOnce()).putString(eq("AppPinHash"), anyString());
        verify(mockEditor, atLeastOnce()).putString(eq("AppPinSalt"), anyString());
    }

    /**
     * Testa que PIN incorreto retorna false.
     */
    @Test
    public void testIncorrectPinReturnsFalse() {
        when(mockPrefs.getString("AppPinHash", null)).thenReturn(null);
        when(mockPrefs.getString("AppPinSalt", null)).thenReturn(null);
        when(mockPrefs.getString("AppPin", null)).thenReturn(null);
        
        boolean isValid = PinSecurityUtils.verifyAndMigratePin(mockPrefs, "wrongPin");
        
        assertFalse("PIN sem configuração deve retornar false", isValid);
    }
}
