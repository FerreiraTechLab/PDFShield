package org.ferreiratechlab.leitordepdfseguro.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Um texto/link salvo pelo usuário. O campo content sempre guarda o resultado de
 * EncryptionUtils.encryptString(SessionKeyHolder.require(), texto) — nunca texto puro, já que
 * esta tabela nasce depois da DEK amarrada ao PIN/biometria existir (sem versão legada).
 */
@Entity
public class SavedText {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "createdAt")
    public long createdAt;

    public SavedText(String content, long createdAt) {
        this.content = content;
        this.createdAt = createdAt;
    }
}
