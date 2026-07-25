package org.ferreiratechlab.leitordepdfseguro.utils;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.spec.SecretKeySpec;

/**
 * Guarda em memória, pelo tempo de vida do processo, a DEK (Data Encryption
 * Key) usada por {@link EncryptionUtils} para criptografar/descriptografar
 * PDFs. A DEK só é carregada aqui depois de uma autenticação bem-sucedida
 * (PIN digitado ou biometria) que a desembrulhou — ver
 * {@link KeyManagerUtils} e {@code PinSecurityUtils} para a derivação das
 * chaves de embrulho. Nenhum código deve obter a chave de arquivo por
 * qualquer outro caminho.
 *
 * Não existe hoje um recurso de "re-bloquear" o app em background; uma vez
 * carregada, a DEK vale pelo resto do processo, no mesmo espírito do
 * comportamento anterior (a chave do Keystore ficava sempre disponível).
 * {@link #clear()} existe para dar suporte a esse recurso no futuro.
 */
public final class SessionKeyHolder {

    private static final AtomicReference<SecretKeySpec> DEK = new AtomicReference<>();
    private static volatile byte[] rawBytes;

    private SessionKeyHolder() {
    }

    public static void set(byte[] dekBytes) {
        byte[] copy = dekBytes.clone();
        DEK.set(new SecretKeySpec(copy, "AES"));
        rawBytes = copy;
    }

    /**
     * @throws IllegalStateException se nenhuma DEK foi carregada ainda — não deve
     *         existir caminho de código legítimo que descriptografe arquivos antes
     *         de o usuário passar pela tela de PIN/biometria.
     */
    public static SecretKeySpec require() {
        SecretKeySpec key = DEK.get();
        if (key == null) {
            throw new IllegalStateException("DEK não carregada: nenhuma autenticação bem-sucedida ainda.");
        }
        return key;
    }

    public static boolean isLoaded() {
        return DEK.get() != null;
    }

    public static void clear() {
        DEK.set(null);
        byte[] bytes = rawBytes;
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
        rawBytes = null;
    }
}
