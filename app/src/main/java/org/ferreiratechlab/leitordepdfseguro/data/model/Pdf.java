package org.ferreiratechlab.leitordepdfseguro.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Pdf {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "filename")
    private String filename;


    public String uri;
    public String title;
    public String path;


    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Pdf(String uri, String title) {
        this.uri = uri;
        this.title = title;
        this.filename=title;
    }



    public void setFilePath(String decryptedFilePath) {
        this.path=decryptedFilePath;
    }

    public String getUri() {
        return this.uri;
    }

    public String getTitle() {
        return this.title;
    }
}
