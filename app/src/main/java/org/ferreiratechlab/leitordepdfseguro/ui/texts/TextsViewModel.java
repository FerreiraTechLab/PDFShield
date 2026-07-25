package org.ferreiratechlab.leitordepdfseguro.ui.texts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.db.TextDao;
import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;
import org.ferreiratechlab.leitordepdfseguro.utils.AppExecutors;

import java.util.List;
import java.util.regex.Pattern;

public class TextsViewModel extends ViewModel {
    private TextDao textDao;
    private LiveData<List<SavedText>> texts;

    private static final Pattern LINK_PATTERN = Pattern.compile("^(?:https?://|ftp://|www\\.)\\S+$", Pattern.CASE_INSENSITIVE);

    public static boolean isLink(String text) {
        return text != null && LINK_PATTERN.matcher(text.trim()).matches();
    }

    public void init(AppDatabase db) {
        textDao = db.textDao();
        texts = textDao.getAll();
    }

    public LiveData<List<SavedText>> getTexts() {
        return texts;
    }

    public void deleteText(int id) {
        AppExecutors.background().execute(() -> textDao.deleteById(id));
    }

    public void deleteAllTexts() {
        AppExecutors.background().execute(() -> textDao.deleteAll());
    }

    public SavedText getNoteById(int id) {
        return textDao.getById(id);
    }

    /** Salva um texto novo (já verificado como não-duplicata pelo chamador). */
    public void saveText(String plainText) throws Exception {
        String encrypted = EncryptionUtils.encryptString(SessionKeyHolder.require(), plainText);
        textDao.insert(new SavedText(encrypted, System.currentTimeMillis()));
    }

    /** Atualiza um texto existente com nova criptografia. */
    public void updateText(int id, String newPlainText) throws Exception {
        SavedText existing = textDao.getById(id);
        if (existing != null) {
            existing.content = EncryptionUtils.encryptString(SessionKeyHolder.require(), newPlainText);
            textDao.update(existing);
        }
    }

    /** Decifra o conteúdo salvo para exibição. */
    public static String toDisplayString(SavedText savedText) {
        try {
            return EncryptionUtils.decryptString(SessionKeyHolder.require(), savedText.content);
        } catch (Exception e) {
            LoggingUtils.logErrorDebug("TextsViewModel", e);
            return "";
        }
    }

    /**
     * Busca um texto já salvo com o mesmo conteúdo (decifrando cada linha para comparar, já
     * que o valor em texto puro nunca é comparável via SQL contra o texto cifrado). Mesmo
     * padrão de PdfEncryptionTask#findExistingByTitle.
     */
    public static boolean alreadyExists(TextDao dao, String candidate) {
        List<SavedText> all = dao.getAllSync();
        if (all == null) return false;
        
        for (SavedText savedText : all) {
            if (candidate.equals(toDisplayString(savedText))) {
                return true;
            }
        }
        return false;
    }
}
