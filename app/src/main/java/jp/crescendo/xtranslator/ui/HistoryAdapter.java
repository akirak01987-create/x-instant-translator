package jp.crescendo.xtranslator.ui;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.NotificationEntity;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    public interface Listener {
        void onOpen(NotificationEntity item);

        void onDelete(NotificationEntity item);
    }

    private final List<NotificationEntity> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN);

    public HistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<NotificationEntity> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationEntity item = items.get(position);

        holder.meta.setText(dateFormat.format(item.receivedAt) + "　" +
                (TextUtils.isEmpty(item.author) ? "投稿者不明" : item.author));
        holder.original.setText(item.originalText);
        holder.original.setTextColor(item.textColor);

        if (item.wasTranslated && !TextUtils.isEmpty(item.translatedText)) {
            holder.translated.setVisibility(View.VISIBLE);
            holder.translated.setText(item.translatedText);
            holder.translated.setTextColor(item.textColor);
        } else {
            holder.translated.setVisibility(View.GONE);
        }

        holder.filterTag.setText("適用フィルター: " + item.filterName);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(item.backgroundColor);
        bg.setCornerRadius(dp(holder.itemView, 10));
        holder.cardContent.setBackground(bg);

        holder.itemView.setOnClickListener(v -> listener.onOpen(item));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View cardContent;
        final TextView meta;
        final TextView original;
        final TextView translated;
        final TextView filterTag;
        final View deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContent = itemView.findViewById(R.id.card_content);
            meta = itemView.findViewById(R.id.text_meta);
            original = itemView.findViewById(R.id.text_original);
            translated = itemView.findViewById(R.id.text_translated);
            filterTag = itemView.findViewById(R.id.text_filter_tag);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
