package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Cria a tabela SavedText (módulo de textos/links salvos). SQL copiado literalmente do
 * createAllTables() gerado pelo Room em AppDatabase_Impl.java (compilando uma vez com a
 * entidade já adicionada, antes desta migração existir), para garantir que bate exatamente
 * com o que o Room valida via PRAGMA table_info — uma tabela nova malformada quebra o app
 * para todo mundo que atualizar, não só em tempo de compilação.
 */
public class Migration_3_4 extends Migration {

    public Migration_3_4() {
        super(3, 4);
    }

    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `SavedText` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT, `createdAt` INTEGER NOT NULL)");
    }
}
