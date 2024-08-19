package org.ferreiratechlab.leitordepdfseguro.ui.main;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;



import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDocumentWrapper;
import org.ferreiratechlab.leitordepdfseguro.R;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

public class PdfAdapter extends RecyclerView.Adapter<PdfAdapter.PdfViewHolder> {
    private List<PdfDocumentWrapper> pdfDocuments;
    private OnPdfClickListener listener;
    private OnPdfLongClickListener longClickListener;

    public interface OnPdfLongClickListener {
        void onPdfLongClick(int position);
    }

    public interface OnPdfClickListener {
        void onPdfClick(Uri pdfUri) throws IOException, GeneralSecurityException;
    }

    public PdfAdapter(List<PdfDocumentWrapper> pdfDocuments, OnPdfClickListener listener) {
        this.pdfDocuments = pdfDocuments;
        this.listener = listener;
    }

    public void setOnPdfLongClickListener(OnPdfLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @Override
    public PdfViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.pdf_item, parent, false);
        return new PdfViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(PdfViewHolder holder, @SuppressLint("RecyclerView") int position) {
        PdfDocumentWrapper pdfDocument = pdfDocuments.get(position);
        holder.pdfTitle.setText(pdfDocument.getTitle());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    listener.onPdfClick(pdfDocument.getUri());
                } catch (IOException | GeneralSecurityException e) {
                    e.printStackTrace();
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (longClickListener != null) {
                    longClickListener.onPdfLongClick(position);
                    return true;
                }
                return false;
            }
        });
    }
    public void updatePdfDocuments(List<PdfDocumentWrapper> newPdfDocuments) {
        this.pdfDocuments.clear();
        this.pdfDocuments.addAll(newPdfDocuments);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return pdfDocuments.size();
    }

    public class PdfViewHolder extends RecyclerView.ViewHolder {
        TextView pdfTitle;

        public PdfViewHolder(View itemView) {
            super(itemView);
            pdfTitle = itemView.findViewById(R.id.pdf_title);
        }
    }

    public List<PdfDocumentWrapper> getItems() {
        return pdfDocuments;
    }

}
