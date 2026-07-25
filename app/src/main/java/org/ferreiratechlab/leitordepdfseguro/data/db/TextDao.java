package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;

import java.util.List;

@Dao
public interface TextDao {
    @Query("SELECT * FROM SavedText ORDER BY createdAt DESC")
    List<SavedText> getAll();

    @Insert
    void insert(SavedText savedText);

    @Query("DELETE FROM SavedText WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM SavedText")
    void deleteAll();
}
