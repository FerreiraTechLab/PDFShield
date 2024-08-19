package org.ferreiratechlab.leitordepdfseguro.ui.display;

import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;

public class PdfDocumentWrapper {
    private PdfDocument pdfDocument;
    private String title;
    private Uri uri;  // adicione este campo

    public PdfDocumentWrapper(PdfDocument pdfDocument, String title, Uri uri) {
        this.pdfDocument = pdfDocument;
        this.title = title;
        this.uri = uri;  // inicialize o campo Uri aqui
    }

    public PdfDocumentWrapper(Uri uri, String filename) {
        this.uri = uri;
        this.title = filename;
    }

    public PdfDocument getPdfDocument() {
        return pdfDocument;
    }

    public void setPdfDocument(PdfDocument pdfDocument) {
        this.pdfDocument = pdfDocument;
    }

    public String getTitle() {
        Log.d("PdfDocumentWrapper", "Title: " + title);
        return title;
    }


    public void setTitle(String title) {
        this.title = title;
    }

    public Uri getUri() {
        return uri;  // retorne o campo Uri aqui
    }

//    /**
//     * Salva o PdfDocument para um arquivo.
//     * @param fileDescriptor Um FileDescriptor apontando para o arquivo em que o PdfDocument deve ser salvo.
//     * @throws IOException Se um erro de entrada/saída ocorrer durante a escrita para o arquivo.
//     */
//    public void saveToFile(ParcelFileDescriptor fileDescriptor) throws IOException {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            pdfDocument.writeTo(fileDescriptor.getFileDescriptor());
//        }
//    }
//
//    /**
//     * Carrega um PdfDocument de um arquivo.
//     * @param fileDescriptor Um FileDescriptor apontando para o arquivo do qual o PdfDocument deve ser carregado.
//     * @throws IOException Se um erro de entrada/saída ocorrer durante a leitura do arquivo.
//     */
//    public void loadFromFile(ParcelFileDescriptor fileDescriptor) throws IOException {
//        pdfDocument = PdfDocument.load(fileDescriptor);
//    }
}

