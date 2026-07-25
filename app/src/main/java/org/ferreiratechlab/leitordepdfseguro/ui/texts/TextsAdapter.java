package org.ferreiratechlab.leitordepdfseguro.ui.texts;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.ferreiratechlab.leitordepdfseguro.R;

import java.util.List;

public class TextsAdapter extends RecyclerView.Adapter<TextsAdapter.TextViewHolder> {
    private final List<TextEntry> entries;
    private OnTextClickListener clickListener;
    private OnTextLongClickListener longClickListener;

    /** Par (id da linha, texto já decifrado) exibido na lista. */
    public static class TextEntry {
        public final int id;
        public final String content;

        public TextEntry(int id, String content) {
            this.id = id;
            this.content = content;
        }
    }

    public interface OnTextClickListener {
        void onTextClick(TextEntry entry);
    }

    public interface OnTextLongClickListener {
        void onTextLongClick(TextEntry entry);
    }

    public TextsAdapter(List<TextEntry> entries) {
        this.entries = entries;
    }

    public void setOnTextClickListener(OnTextClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTextLongClickListener(OnTextLongClickListener listener) {
        this.longClickListener = listener;
    }

    @Override
    public TextViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.text_item, parent, false);
        return new TextViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(TextViewHolder holder, @SuppressLint("RecyclerView") int position) {
        TextEntry entry = entries.get(position);
        holder.textContent.setText(entry.content);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTextClick(entry);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onTextLongClick(entry);
                return true;
            }
            return false;
        });
    }

    public void updateEntries(List<TextEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView textContent;

        public TextViewHolder(View itemView) {
            super(itemView);
            textContent = itemView.findViewById(R.id.text_content);
        }
    }
}
