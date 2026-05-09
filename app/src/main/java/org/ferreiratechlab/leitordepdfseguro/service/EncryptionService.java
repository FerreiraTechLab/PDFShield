package org.ferreiratechlab.leitordepdfseguro.service;

import android.content.Context;
import android.util.Base64;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.EncryptionKey;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;

import javax.crypto.SecretKey;


public class EncryptionService {

    private AppDatabase db;

    public EncryptionService(Context context) {
        db = AppDatabase.getInstance(context);
    }

    public String generateAndSaveKey() throws Exception {
        SecretKey secretKey = KeyManagerUtils.getOrCreateKey();
        String key = Base64.encodeToString(secretKey.getEncoded(), Base64.DEFAULT);
        EncryptionKey encryptionKey = new EncryptionKey("default_key", key);
        new Thread(() -> db.encryptionKeyDao().insertKey(encryptionKey)).start();
        return key;
    }

    public String retrieveKey() throws Exception {
        EncryptionKey encryptionKey = db.encryptionKeyDao().getKey();
        if (encryptionKey != null) {
            return encryptionKey.getKeyValue();
        } else {
            throw new Exception("Chave de criptografia não encontrada.");
        }
    }
}
