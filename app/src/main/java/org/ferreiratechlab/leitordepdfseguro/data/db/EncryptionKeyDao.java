package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.ferreiratechlab.leitordepdfseguro.data.model.EncryptionKey;

@Dao
public interface EncryptionKeyDao {
    @Query("SELECT * FROM encryption_key LIMIT 1")
    EncryptionKey getKey();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertKey(EncryptionKey key);
}

