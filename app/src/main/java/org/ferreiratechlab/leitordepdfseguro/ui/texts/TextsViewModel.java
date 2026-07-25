package org.ferreiratechlab.leitordepdfseguro.ui.texts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.db.TextDao;
import org.ferreiratechlab.leitordepdfseguro.data.model.SavedText;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

import java.util.List;

public class TextsViewModel extends ViewModel {
    private TextDao textDao;
    private final MutableLiveData<List<SavedText>> texts = new MutableLiveData<>();

    public void init(AppDatabase db) {
        textDao = db.textDao();
        refresh();
    }

    public void refresh() {
        texts.postValue(textDao.getAll());
    }

    public LiveData<List<SavedText>> getTexts() {
        return texts;
    }

    public void deleteText(int id) {
        textDao.deleteById(id);
        refresh();
    }

    public void deleteAllTexts() {
        textDao.deleteAll();
        refresh();
    }

    /** Salva um texto novo (já verificado como não-duplicata pelo chamador). */
    public void saveText(String plainText) throws Exception {
        String encrypted = EncryptionUtils.encryptString(SessionKeyHolder.require(), plainText);
        textDao.insert(new SavedText(encrypted, System.currentTimeMillis()));
        refresh();
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
        for (SavedText savedText : dao.getAll()) {
            if (candidate.equals(toDisplayString(savedText))) {
                return true;
            }
        }
        return false;
    }
}
