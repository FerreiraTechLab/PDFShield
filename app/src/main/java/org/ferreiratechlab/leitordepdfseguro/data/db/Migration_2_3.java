package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Adiciona a coluna metadataVersion à tabela Pdf. Linhas existentes (título ainda em texto
 * puro, arquivo no disco nomeado a partir do nome original) ficam com o default 1;
 * MainActivity criptografa o título e renomeia o arquivo para um UUID opaco, subindo cada
 * linha para metadataVersion=2 conforme conclui, de forma resumível caso o app seja
 * interrompido no meio — mesmo padrão de Migration_1_2 para keyVersion.
 */
public class Migration_2_3 extends Migration {

    public Migration_2_3() {
        super(2, 3);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE Pdf ADD COLUMN metadataVersion INTEGER NOT NULL DEFAULT 1");
    }
}
