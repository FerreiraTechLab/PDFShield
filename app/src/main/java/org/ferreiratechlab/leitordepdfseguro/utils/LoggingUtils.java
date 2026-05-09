package org.ferreiratechlab.leitordepdfseguro.utils;

import android.util.Log;

/**
 * Utilitário de logging seguro para PDFShield.
 * 
 * Princípios:
 * - NUNCA logar dados sensíveis: PIN, salt, hash, chaves criptográficas, URIs privados
 * - SEMPRE logar apenas eventos e estados (sem valores sensíveis)
 * - Usar níveis apropriados: Log.e (erro), Log.w (aviso), Log.i (info)
 * - Log.d e Log.v SÃO REMOVIDOS em release (ProGuard stripping)
 * 
 * Exemplos SEGUROS:
 * - "PIN verification failed for user attempt"
 * - "Biometric authentication invalidated due to enrollment change"
 * - "PDF encryption completed successfully"
 * 
 * Exemplos INSEGUROS (NUNCA fazer):
 * - "PIN is: 1234"
 * - "Salt hash: abcd1234..."
 * - "User entered PIN: " + userPin
 * - "PDF file: /path/to/sensitive/file.pdf"
 */
public final class LoggingUtils {

    private static final String TAG = "PDFShield";
    
    // Feature tags para organização
    private static final String TAG_PIN_AUTH = "PinAuth";
    private static final String TAG_BIOMETRIC = "BioAuth";
    private static final String TAG_ENCRYPTION = "Crypto";
    private static final String TAG_STORAGE = "Storage";
    private static final String TAG_UI = "UI";

    private LoggingUtils() {
        // Utility class
    }

    // ============================================================
    // PIN AUTHENTICATION (Seguro - sem valores)
    // ============================================================

    /**
     * Log quando PIN é configurado com sucesso (sem expor PIN).
     */
    public static void logPinConfigured() {
        Log.i(TAG_PIN_AUTH, "PIN configured successfully");
    }

    /**
     * Log quando verificação de PIN falha (sem expor tentativa).
     */
    public static void logPinVerificationFailed() {
        Log.w(TAG_PIN_AUTH, "PIN verification failed");
    }

    /**
     * Log quando verificação de PIN sucede (apenas que sucedeu).
     */
    public static void logPinVerificationSuccess() {
        Log.i(TAG_PIN_AUTH, "PIN verification succeeded");
    }

    /**
     * Log quando migração de PIN legacy ocorre.
     */
    public static void logPinAutoMigrationTriggered() {
        Log.i(TAG_PIN_AUTH, "Legacy PIN auto-migration triggered and completed");
    }

    // ============================================================
    // BIOMETRIC AUTHENTICATION (Seguro - sem dados de fingerprint)
    // ============================================================

    /**
     * Log quando biometric auth é ativado.
     */
    public static void logBiometricEnabled() {
        Log.i(TAG_BIOMETRIC, "Biometric authentication enabled");
    }

    /**
     * Log quando biometric auth é desabilitado.
     */
    public static void logBiometricDisabled() {
        Log.w(TAG_BIOMETRIC, "Biometric authentication disabled");
    }

    /**
     * Log quando fingerprint enrollment muda (key invalidation).
     */
    public static void logBiometricEnrollmentChanged() {
        Log.w(TAG_BIOMETRIC, "Device fingerprint enrollment changed - biometric key invalidated");
    }

    /**
     * Log quando biometric authentication falha (genérico).
     */
    public static void logBiometricAuthenticationFailed(String reason) {
        Log.w(TAG_BIOMETRIC, "Biometric authentication failed: " + reason);
    }

    /**
     * Log quando biometric authentication sucede.
     */
    public static void logBiometricAuthenticationSuccess() {
        Log.i(TAG_BIOMETRIC, "Biometric authentication succeeded");
    }

    // ============================================================
    // ENCRYPTION / DECRYPTION (Seguro - sem conteúdo)
    // ============================================================

    /**
     * Log quando PDF é encriptado com sucesso.
     */
    public static void logPdfEncryptionCompleted(String fileName) {
        // Logar apenas nome do arquivo, não path completo
        Log.i(TAG_ENCRYPTION, "PDF encrypted: " + sanitizeFileName(fileName));
    }

    /**
     * Log quando PDF é decriptado com sucesso.
     */
    public static void logPdfDecryptionCompleted(String fileName) {
        Log.i(TAG_ENCRYPTION, "PDF decrypted: " + sanitizeFileName(fileName));
    }

    /**
     * Log quando decryption falha.
     */
    public static void logPdfDecryptionFailed(String fileName) {
        Log.w(TAG_ENCRYPTION, "PDF decryption failed: " + sanitizeFileName(fileName));
    }

    /**
     * Log quando criptografia de arquivo original falha.
     */
    public static void logOriginalFileZeroFillFailed(String reason) {
        Log.w(TAG_ENCRYPTION, "Original file secure deletion failed: " + reason);
    }

    // ============================================================
    // STORAGE OPERATIONS (Seguro - sem paths)
    // ============================================================

    /**
     * Log quando arquivo é deletado com sucesso.
     */
    public static void logFileDeleted(String fileName) {
        Log.i(TAG_STORAGE, "File deleted securely: " + sanitizeFileName(fileName));
    }

    /**
     * Log quando exclusão segura falha.
     */
    public static void logFileSecureDeleteFailed(String fileName) {
        Log.w(TAG_STORAGE, "Secure file deletion failed: " + sanitizeFileName(fileName));
    }

    /**
     * Log quando lista de PDFs é limpa.
     */
    public static void logAllPdfsDeleted() {
        Log.i(TAG_STORAGE, "All PDFs deleted from storage");
    }

    // ============================================================
    // UI / ACTIVITY LIFECYCLE (Seguro - sem dados sensíveis)
    // ============================================================

    /**
     * Log quando atividade inicia autenticação.
     */
    public static void logActivityAuthenticationStarted(String activityName) {
        Log.d(TAG_UI, "Authentication started: " + activityName);
    }

    /**
     * Log quando atividade completa autenticação.
     */
    public static void logActivityAuthenticationCompleted(String activityName) {
        Log.i(TAG_UI, "Authentication completed: " + activityName);
    }

    /**
     * Log quando atividade é pausada (segurança).
     */
    public static void logActivityPaused(String activityName) {
        Log.d(TAG_UI, "Activity paused (FLAG_SECURE active): " + activityName);
    }

    // ============================================================
    // ERROR LOGGING (Seguro - sem stack traces completos em release)
    // ============================================================

    /**
     * Log error com mensagem segura (sem exception trace em release).
     * 
     * IMPORTANTE: Em production, considere usar Crashlytics sem PII.
     * Aqui apenas logamos a categoria do erro.
     */
    public static void logError(String feature, String errorCategory) {
        Log.e(TAG, feature + ": " + errorCategory);
    }

    /**
     * Log error com exception (em debug; remove em release via ProGuard).
     */
    public static void logErrorDebug(String feature, Exception e) {
        // Log.d é removido em release via ProGuard
        Log.d(TAG, feature + ": " + e.getClass().getSimpleName());
    }

    // ============================================================
    // HELPER METHODS (Sanitização)
    // ============================================================

    /**
     * Sanitiza nome de arquivo para evitar expor paths completos.
     * Exemplo: "/path/to/file.pdf" → "file.pdf"
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "[unknown]";
        }
        // Retornar apenas o nome do arquivo, não o path
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            return fileName.substring(lastSlash + 1);
        }
        return fileName;
    }

    /**
     * Sanitiza exception message removendo dados sensíveis.
     */
    private static String sanitizeExceptionMessage(String message) {
        if (message == null) {
            return "[exception]";
        }
        // Remover paths e valores suspeitos
        return message
            .replaceAll("/[\\w/\\.\\-]+", "[path]")
            .replaceAll("[a-f0-9]{32,}", "[hash]");
    }

    // ============================================================
    // FEATURE FLAGS (Para ativar/desativar logging em debug)
    // ============================================================

    /**
     * Ativa verbose logging (apenas debug builds).
     * Em release, ProGuard remove Log.d e Log.v automaticamente.
     */
    public static boolean isDebugLoggingEnabled() {
        // BuildConfig.DEBUG é injetado em compile-time
        // BuildConfig.DEBUG = true em debug build, false em release
        return android.util.Log.isLoggable(TAG, android.util.Log.DEBUG);
    }
}
