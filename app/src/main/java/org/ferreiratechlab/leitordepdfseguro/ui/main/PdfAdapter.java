package org.ferreiratechlab.leitordepdfseguro.ui.main;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ferreiratechlab.leitordepdfseguro.ui.display.PdfDocumentWrapper;
import org.ferreiratechlab.leitordepdfseguro.R;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class PdfAdapter extends RecyclerView.Adapter<PdfAdapter.PdfViewHolder> {
    private List<PdfDocumentWrapper> pdfDocuments;
    private List<PdfDocumentWrapper> filteredList;
    private OnPdfClickListener listener;
    private OnPdfMenuClickListener menuClickListener;

    public interface OnPdfMenuClickListener {
        void onPdfMenuClick(PdfDocumentWrapper pdf, View anchor);
    }

    public interface OnPdfClickListener {
        void onPdfClick(Uri pdfUri) throws IOException, GeneralSecurityException;
    }

    public PdfAdapter(List<PdfDocumentWrapper> pdfDocuments, OnPdfClickListener listener) {
        this.pdfDocuments = pdfDocuments;
        this.filteredList = new ArrayList<>(pdfDocuments);
        this.listener = listener;
    }

    public void setOnPdfMenuClickListener(OnPdfMenuClickListener menuClickListener) {
        this.menuClickListener = menuClickListener;
    }

    @NonNull
    @Override
    public PdfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.pdf_item, parent, false);
        return new PdfViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull PdfViewHolder holder, int position) {
        PdfDocumentWrapper pdfDocument = filteredList.get(position);
        holder.pdfTitle.setText(pdfDocument.getTitle());

        holder.itemView.setOnClickListener(v -> {
            try {
                if (listener != null) {
                    listener.onPdfClick(pdfDocument.getUri());
                }
            } catch (IOException | GeneralSecurityException e) {
                e.printStackTrace();
            }
        });

        holder.btnMenu.setOnClickListener(v -> {
            if (menuClickListener != null) {
                menuClickListener.onPdfMenuClick(pdfDocument, v);
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updatePdfDocuments(List<PdfDocumentWrapper> newPdfDocuments) {
        this.pdfDocuments.clear();
        this.pdfDocuments.addAll(newPdfDocuments);
        applyFilter(""); // Reset filter
    }

    @SuppressLint("NotifyDataSetChanged")
    public void applyFilter(String query) {
        String normalizedQuery = query.toLowerCase().trim();
        filteredList.clear();
        if (normalizedQuery.isEmpty()) {
            filteredList.addAll(pdfDocuments);
        } else {
            for (PdfDocumentWrapper doc : pdfDocuments) {
                if (doc.getTitle().toLowerCase().contains(normalizedQuery)) {
                    filteredList.add(doc);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public boolean isEmpty() {
        return pdfDocuments.isEmpty();
    }

    public boolean isFilterEmpty() {
        return filteredList.isEmpty();
    }

    public static class PdfViewHolder extends RecyclerView.ViewHolder {
        TextView pdfTitle;
        ImageButton btnMenu;

        public PdfViewHolder(View itemView) {
            super(itemView);
            pdfTitle = itemView.findViewById(R.id.pdf_title);
            btnMenu = itemView.findViewById(R.id.btn_item_menu);
        }
    }
}
