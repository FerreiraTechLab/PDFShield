package org.ferreiratechlab.leitordepdfseguro.ui.display;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.data.db.PdfDao;
import org.ferreiratechlab.leitordepdfseguro.utils.EncryptionUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.LoggingUtils;
import org.ferreiratechlab.leitordepdfseguro.utils.SessionKeyHolder;

import java.util.List;

public class PdfViewModel extends ViewModel {
    private PdfDao pdfDao;
    private LiveData<List<Pdf>> pdfs;

    public void init(AppDatabase db) {
        pdfDao = db.pdfDao();
        pdfs = pdfDao.getAll();
    }

    public LiveData<List<Pdf>> getPdfs() {
        return pdfs;
    }

    public void deletePdf(int id) {
        new Thread(() -> pdfDao.deleteById(id)).start();
    }

    /**
     * Monta o wrapper de exibição a partir de uma linha do banco, decifrando o título quando
     * já migrado (Pdf#metadataVersion == 2). Um Pdf lido do banco guarda o título em texto
     * cifrado; um PdfDocumentWrapper sempre guarda o nome pronto para exibição.
     */
    public static PdfDocumentWrapper toWrapper(Pdf pdf) {
        String displayTitle = pdf.title;
        if (pdf.metadataVersion == 2) {
            try {
                displayTitle = EncryptionUtils.decryptString(SessionKeyHolder.require(), pdf.title);
            } catch (Exception e) {
                LoggingUtils.logErrorDebug("PdfViewModel", e);
            }
        }
        PdfDocumentWrapper wrapper = new PdfDocumentWrapper(Uri.parse(pdf.uri), displayTitle);
        wrapper.setId(pdf.id);
        return wrapper;
    }
}
