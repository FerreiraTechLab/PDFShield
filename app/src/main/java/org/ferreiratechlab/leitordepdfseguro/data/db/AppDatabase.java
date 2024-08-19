package org.ferreiratechlab.leitordepdfseguro.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import org.ferreiratechlab.leitordepdfseguro.data.model.EncryptionKey;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;

@Database(entities = {Pdf.class, EncryptionKey.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract PdfDao pdfDao();
    public abstract EncryptionKeyDao encryptionKeyDao();

    private static AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "database-name").build();
                }
            }
        }
        return INSTANCE;
    }
}
