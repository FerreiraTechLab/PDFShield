package org.ferreiratechlab.leitordepdfseguro.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "encryption_key")
public class EncryptionKey {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "key_alias")
    private String keyAlias;

    @NonNull
    @ColumnInfo(name = "key_value")
    private String keyValue;

    public EncryptionKey(@NonNull String keyAlias, @NonNull String keyValue) {
        this.keyAlias = keyAlias;
        this.keyValue = keyValue;
    }

    @NonNull
    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(@NonNull String keyAlias) {
        this.keyAlias = keyAlias;
    }

    @NonNull
    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(@NonNull String keyValue) {
        this.keyValue = keyValue;
    }

    public String getKey() {
        return keyValue;
    }
}
