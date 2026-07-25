package org.ferreiratechlab.leitordepdfseguro.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;

@Database(entities = {Pdf.class, SavedText.class}, version = 4)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PdfDao pdfDao();
    public abstract TextDao textDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * Única instância Room do app. Antes desta mudança, MainActivity criava
     * sua própria instância separada via Room.databaseBuilder(...) apontando
     * para o mesmo arquivo "database-name" — o que faria qualquer migração
     * registrada aqui nunca rodar de verdade nessa outra instância. Todo
     * código deve passar a usar exclusivamente este getInstance().
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "database-name")
                            .addMigrations(new Migration_1_2(), new Migration_2_3(), new Migration_3_4())
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
