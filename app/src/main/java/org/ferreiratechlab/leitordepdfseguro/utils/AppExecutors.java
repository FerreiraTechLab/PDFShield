package org.ferreiratechlab.leitordepdfseguro.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executor único de background compartilhado pelas tasks do app (substitui o
 * SERIAL_EXECUTOR implícito do AsyncTask). Usar uma única thread preserva o
 * comportamento anterior de executar as tasks em sequência, evitando que uma
 * criptografia e um backup concorram pelo mesmo arquivo/banco ao mesmo tempo.
 */
public final class AppExecutors {

    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();

    private AppExecutors() {
    }

    public static ExecutorService background() {
        return BACKGROUND;
    }
}
