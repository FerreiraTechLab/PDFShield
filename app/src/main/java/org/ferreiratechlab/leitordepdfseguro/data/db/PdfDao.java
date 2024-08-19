package org.ferreiratechlab.leitordepdfseguro.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;


import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;

import java.util.List;

@Dao
public interface PdfDao {
    @Query("SELECT * FROM Pdf")
    List<Pdf> getAll();

    @Insert
    void insertAll(Pdf... pdfs);

    @Query("SELECT * FROM Pdf WHERE filename = :filename")
    Pdf getPdfByFilename(String filename);

    @Update
    void update(Pdf pdf);

    @Delete
    void delete(Pdf pdf);
}

