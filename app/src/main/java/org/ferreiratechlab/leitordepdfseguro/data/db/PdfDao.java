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

    @Query("SELECT COUNT(*) FROM Pdf WHERE keyVersion = 1")
    int countLegacyKeyVersionPdfs();

    @Query("SELECT * FROM Pdf WHERE keyVersion = 1")
    List<Pdf> getLegacyKeyVersionPdfs();

    @Query("SELECT COUNT(*) FROM Pdf WHERE metadataVersion = 1")
    int countLegacyMetadataVersionPdfs();

    @Query("SELECT * FROM Pdf WHERE metadataVersion = 1")
    List<Pdf> getLegacyMetadataVersionPdfs();

    @Update
    void update(Pdf pdf);

    @Delete
    void delete(Pdf pdf);

    @Query("DELETE FROM Pdf WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM Pdf")
    void deleteAll();
}

