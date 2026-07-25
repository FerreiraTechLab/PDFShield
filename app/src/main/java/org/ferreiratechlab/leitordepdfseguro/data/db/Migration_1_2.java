package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Adiciona a coluna keyVersion à tabela Pdf e remove a tabela encryption_key
 * (código morto — nunca foi usada, e se fosse, guardaria a chave do Keystore
 * em texto puro no banco). Linhas existentes de Pdf (arquivos .enc já
 * criptografados com a chave legada do Keystore, sem gate de autenticação)
 * ficam com o default keyVersion=1; MainActivity re-criptografa esses
 * arquivos com a DEK amarrada ao PIN/biometria e sobe cada linha para
 * keyVersion=2 conforme conclui, de forma resumível caso o app seja
 * interrompido no meio.
 */
public class Migration_1_2 extends Migration {

    public Migration_1_2() {
        super(1, 2);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE Pdf ADD COLUMN keyVersion INTEGER NOT NULL DEFAULT 1");
        database.execSQL("DROP TABLE IF EXISTS encryption_key");
    }
}
