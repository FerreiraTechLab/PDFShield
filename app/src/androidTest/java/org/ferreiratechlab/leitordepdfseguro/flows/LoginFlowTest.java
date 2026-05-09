package org.ferreiratechlab.leitordepdfseguro.flows;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.ferreiratechlab.leitordepdfseguro.utils.PinSecurityUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Testes de integração para fluxo de login com PIN.
 * 
 * Executa em dispositivo/emulador (não é teste unitário puro).
 * Valida interação entre SharedPreferences e PinSecurityUtils.
 */
@RunWith(AndroidJUnit4.class)
public class LoginFlowTest {

    private Context context;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "TestAuthPrefs";

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Limpar preferências antes de cada teste
        prefs.edit().clear().apply();
    }

    /**
     * Testa fluxo completo: Usuário define PIN → Faz login com PIN correto.
     */
    @Test
    public void testCompleteLoginFlowWithCorrectPin() {
        String userPin = "1234";
        
        // 1. Usuário define PIN na primeira vez
        PinSecurityUtils.savePin(prefs, userPin);
        
        // Verificar que PIN foi salvo (hash + salt, não plaintext)
        String savedHash = prefs.getString("AppPinHash", null);
        String savedSalt = prefs.getString("AppPinSalt", null);
        String plainPin = prefs.getString("AppPin", null);
        
        assertNotNull("Hash deve ser salvo", savedHash);
        assertNotNull("Salt deve ser salvo", savedSalt);
        assertNull("PIN plaintext não deve ser salvo", plainPin);
        
        // 2. Usuário tenta fazer login com PIN correto
        boolean loginSuccess = PinSecurityUtils.verifyAndMigratePin(prefs, userPin);
        
        assertTrue("Login com PIN correto deve suceder", loginSuccess);
    }

    /**
     * Testa que login falha com PIN incorreto.
     */
    @Test
    public void testLoginFailsWithIncorrectPin() {
        String correctPin = "1234";
        String wrongPin = "5678";
        
        // Setup: Salvar PIN correto
        PinSecurityUtils.savePin(prefs, correctPin);
        
        // Tentar login com PIN errado
        boolean loginSuccess = PinSecurityUtils.verifyAndMigratePin(prefs, wrongPin);
        
        assertFalse("Login com PIN incorreto deve falhar", loginSuccess);
    }

    /**
     * Testa auto-migração: PIN plaintext legacy → hash moderno.
     */
    @Test
    public void testAutoMigrationFromLegacyPlaintextPin() {
        String legacyPin = "1234";
        
        // Setup: Simular app antigo com PIN plaintext
        prefs.edit().putString("AppPin", legacyPin).apply();
        
        // Verificar que está em formato legacy
        assertNotNull("Legacy PIN deve existir", prefs.getString("AppPin", null));
        assertNull("Hash não deve existir ainda", prefs.getString("AppPinHash", null));
        
        // Usuário entra PIN (trigger migration)
        boolean loginSuccess = PinSecurityUtils.verifyAndMigratePin(prefs, legacyPin);
        
        assertTrue("Login deve suceder com PIN legacy", loginSuccess);
        
        // Verificar que migração ocorreu
        String migratedHash = prefs.getString("AppPinHash", null);
        String migratedSalt = prefs.getString("AppPinSalt", null);
        String plainPinAfterMigration = prefs.getString("AppPin", null);
        
        assertNotNull("Hash deve ser criado durante migração", migratedHash);
        assertNotNull("Salt deve ser criado durante migração", migratedSalt);
        assertNull("PIN plaintext deve ser removido após migração", plainPinAfterMigration);
    }

    /**
     * Testa que PIN pode ser atualizado (change PIN).
     */
    @Test
    public void testUpdatePinFlow() {
        String oldPin = "1111";
        String newPin = "2222";
        
        // Setup: PIN antigo
        PinSecurityUtils.savePin(prefs, oldPin);
        
        // Verificar que PIN antigo funciona
        boolean oldPinWorks = PinSecurityUtils.verifyAndMigratePin(prefs, oldPin);
        assertTrue("PIN antigo deve funcionar antes da mudança", oldPinWorks);
        
        // Usuário muda PIN
        PinSecurityUtils.savePin(prefs, newPin);
        
        // Verificar que novo PIN funciona
        boolean newPinWorks = PinSecurityUtils.verifyAndMigratePin(prefs, newPin);
        assertTrue("Novo PIN deve funcionar após mudança", newPinWorks);
        
        // Verificar que PIN antigo não funciona mais
        boolean oldPinStillWorks = PinSecurityUtils.verifyAndMigratePin(prefs, oldPin);
        assertFalse("PIN antigo não deve funcionar após mudança", oldPinStillWorks);
    }

    /**
     * Testa comportamento quando não há PIN configurado (first-time user).
     */
    @Test
    public void testNoLoginPossibleWhenNoPinConfigured() {
        // Setup: Nenhum PIN configurado (prefs vazias)
        
        // Tentar fazer login sem PIN
        boolean loginSuccess = PinSecurityUtils.verifyAndMigratePin(prefs, "anyPin");
        
        assertFalse("Login deve falhar quando nenhum PIN foi configurado", loginSuccess);
    }

    /**
     * Testa que PIN com espaços em branco é tratado corretamente.
     */
    @Test
    public void testPinWithWhitespaceIsTreatedAsIsNotTrimmed() {
        String pinWithSpaces = "12 34"; // PIN com espaço
        
        // Setup: Salvar PIN com espaço
        PinSecurityUtils.savePin(prefs, pinWithSpaces);
        
        // Tentar login com mesmo PIN (incluindo espaço)
        boolean withSpaceWorks = PinSecurityUtils.verifyAndMigratePin(prefs, pinWithSpaces);
        assertTrue("PIN com espaço deve funcionar quando idêntico", withSpaceWorks);
        
        // Tentar login SEM espaço (deve falhar)
        boolean withoutSpaceWorks = PinSecurityUtils.verifyAndMigratePin(prefs, "1234");
        assertFalse("PIN sem espaço não deve funcionar se original tinha espaço", withoutSpaceWorks);
    }

    /**
     * Testa segurança: PINs diferentes geram hashes diferentes.
     */
    @Test
    public void testDifferentPinsGenerateDifferentHashes() {
        String pin1 = "1111";
        String pin2 = "2222";
        
        // Salvar PIN 1 e obter hash
        PinSecurityUtils.savePin(prefs, pin1);
        String hash1 = prefs.getString("AppPinHash", null);
        
        // Salvar PIN 2 e obter hash
        PinSecurityUtils.savePin(prefs, pin2);
        String hash2 = prefs.getString("AppPinHash", null);
        
        // Hashes devem ser diferentes
        assertNotEquals("PINs diferentes devem gerar hashes diferentes", hash1, hash2);
    }
}
