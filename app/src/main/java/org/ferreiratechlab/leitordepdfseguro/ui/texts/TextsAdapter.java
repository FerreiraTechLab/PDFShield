package org.ferreiratechlab.leitordepdfseguro.ui.texts;

import android.annotation.SuppressLint;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ferreiratechlab.leitordepdfseguro.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TextsAdapter extends RecyclerView.Adapter<TextsAdapter.TextViewHolder> {
    private List<TextEntry> allEntries;
    private List<TextEntry> filteredEntries;
    private OnTextClickListener clickListener;
    private OnTextMenuClickListener menuClickListener;
    private OnTextDoubleClickListener doubleClickListener;

    private long lastClickTime = 0;
    private int lastClickedId = -1;
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // milissegundos

    public static class TextEntry {
        public final int id;
        public final String content;
        public final long timestamp;

        public TextEntry(int id, String content, long timestamp) {
            this.id = id;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    public interface OnTextClickListener {
        void onTextClick(TextEntry entry);
    }

    public interface OnTextMenuClickListener {
        void onTextMenuClick(TextEntry entry, View anchor);
    }

    public interface OnTextDoubleClickListener {
        void onTextDoubleClick(TextEntry entry);
    }

    public TextsAdapter(List<TextEntry> entries) {
        this.allEntries = entries;
        this.filteredEntries = new ArrayList<>(entries);
    }

    public void setOnTextClickListener(OnTextClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTextMenuClickListener(OnTextMenuClickListener listener) {
        this.menuClickListener = listener;
    }

    public void setOnTextDoubleClickListener(OnTextDoubleClickListener listener) {
        this.doubleClickListener = listener;
    }

    @NonNull
    @Override
    public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.text_item, parent, false);
        return new TextViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
        TextEntry entry = filteredEntries.get(position);
        holder.textContent.setText(entry.content);
        
        // Formata data
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        cal.setTimeInMillis(entry.timestamp);
        String dateStr = DateFormat.format("dd MMM yyyy, HH:mm", cal).toString();
        holder.textDate.setText(dateStr);

        // Ícone dinâmico
        boolean isLink = TextsViewModel.isLink(entry.content);
        holder.textIcon.setImageResource(isLink ? R.drawable.baseline_help_24 : android.R.drawable.ic_menu_edit);
        
        holder.itemView.setOnClickListener(v -> {
            long clickTime = System.currentTimeMillis();
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA && lastClickedId == entry.id) {
                if (doubleClickListener != null) {
                    doubleClickListener.onTextDoubleClick(entry);
                }
            } else {
                if (clickListener != null) {
                    clickListener.onTextClick(entry);
                }
            }
            lastClickTime = clickTime;
            lastClickedId = entry.id;
        });
        
        holder.btnMenu.setOnClickListener(v -> {
            if (menuClickListener != null) {
                menuClickListener.onTextMenuClick(entry, v);
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateEntries(List<TextEntry> newEntries) {
        this.allEntries.clear();
        this.allEntries.addAll(newEntries);
        applyFilter("");
    }

    @SuppressLint("NotifyDataSetChanged")
    public void applyFilter(String query) {
        String normalizedQuery = query.toLowerCase().trim();
        filteredEntries.clear();
        if (normalizedQuery.isEmpty()) {
            filteredEntries.addAll(allEntries);
        } else {
            for (TextEntry entry : allEntries) {
                if (entry.content.toLowerCase().contains(normalizedQuery)) {
                    filteredEntries.add(entry);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return filteredEntries.size();
    }

    public boolean isEmpty() {
        return allEntries.isEmpty();
    }

    public boolean isFilterEmpty() {
        return filteredEntries.isEmpty();
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView textContent;
        TextView textDate;
        ImageView textIcon;
        ImageButton btnMenu;

        public TextViewHolder(View itemView) {
            super(itemView);
            textContent = itemView.findViewById(R.id.text_content);
            textDate = findViewById(R.id.text_date);
            textIcon = findViewById(R.id.text_icon);
            btnMenu = findViewById(R.id.btn_item_menu);
        }

        private <T extends View> T findViewById(int id) {
            return itemView.findViewById(id);
        }
    }
}
