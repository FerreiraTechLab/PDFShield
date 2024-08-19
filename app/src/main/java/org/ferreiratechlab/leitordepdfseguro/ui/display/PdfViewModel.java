package org.ferreiratechlab.leitordepdfseguro.ui.display;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.ferreiratechlab.leitordepdfseguro.data.db.AppDatabase;
import org.ferreiratechlab.leitordepdfseguro.data.model.Pdf;
import org.ferreiratechlab.leitordepdfseguro.data.db.PdfDao;

import java.util.List;

public class PdfViewModel extends ViewModel {
    private PdfDao pdfDao;
    private MutableLiveData<List<Pdf>> pdfs = new MutableLiveData<>();

    public void init(AppDatabase db) {
        pdfDao = db.pdfDao();
        List<Pdf> savedPdfs = db.pdfDao().getAll();
        pdfs.postValue(savedPdfs);
    }

    public LiveData<List<Pdf>> getPdfs() {
        return pdfs;
    }

    public void deletePdf(String title) {
        new Thread(() -> {
            Pdf pdf = pdfDao.getPdfByFilename(title);
            if (pdf != null) {
                pdfDao.delete(pdf);
            }
        }).start();
    }


}


