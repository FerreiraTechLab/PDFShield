package org.ferreiratechlab.leitordepdfseguro.service;

import android.content.Context;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.EncryptionKey;
import org.ferreiratechlab.leitordepdfseguro.utils.KeyManagerUtils;


public class EncryptionService {

    private AppDatabase db;
    private Context context;

    public EncryptionService(Context context) {
        db = AppDatabase.getInstance(context);
        this.context=context;
    }

    public String generateAndSaveKey() throws Exception {

        String key = String.valueOf(KeyManagerUtils.getOrCreateKey(context));
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
