package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;

import java.util.List;

@Dao
public interface TextDao {
    @Query("SELECT * FROM SavedText ORDER BY createdAt DESC")
    LiveData<List<SavedText>> getAll();

    @Query("SELECT * FROM SavedText")
    List<SavedText> getAllSync();

    @Query("SELECT COUNT(*) FROM SavedText")
    LiveData<Integer> getCount();

    @Query("SELECT * FROM SavedText WHERE id = :id LIMIT 1")
    SavedText getById(int id);

    @Insert
    void insert(SavedText savedText);

    @Update
    void update(SavedText savedText);

    @Query("DELETE FROM SavedText WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM SavedText")
    void deleteAll();
}
