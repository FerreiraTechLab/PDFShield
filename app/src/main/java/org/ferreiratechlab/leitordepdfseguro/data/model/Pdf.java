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

    /**
     * 1 = arquivo .enc criptografado com a chave legada do Keystore (sem gate
     * de autenticação); 2 = já migrado para a DEK amarrada ao PIN/biometria.
     * Ver Migration_1_2 e MainActivity#migrateLegacyEncryptedFiles.
     */
    @ColumnInfo(name = "keyVersion", defaultValue = "1")
    public int keyVersion = 1;

    /**
     * 1 = title ainda em texto puro e arquivo no disco nomeado a partir do nome original;
     * 2 = title criptografado com a DEK da sessão e arquivo no disco renomeado para um UUID
     * opaco. Ver Migration_2_3 e MainActivity#migrateLegacyMetadataIfNeeded.
     */
    @ColumnInfo(name = "metadataVersion", defaultValue = "1")
    public int metadataVersion = 1;


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
