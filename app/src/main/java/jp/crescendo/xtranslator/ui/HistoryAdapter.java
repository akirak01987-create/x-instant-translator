package jp.crescendo.xtranslator.ui;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.NotificationEntity;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private static final float BASE_META_SP = 11f;
    private static final float BASE_BODY_SP = 13f;
    private static final float BASE_TRANSLATED_SP = 14f;

    public interface Listener {
        void onOpen(NotificationEntity item);

        void onDelete(NotificationEntity item);
    }

    private final List<NotificationEntity> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN);
    private float textScale = 1f;

    public HistoryAdapter(Listener listener) {
        this.listener = listener;
    }

    /** タブレットなど画面が大きい端末向けの文字サイズ倍率(1.0が標準)。変更時は一覧を再描画する。 */
    public void setTextScale(float scale) {
        if (textScale == scale) return;
        textScale = scale;
        notifyDataSetChanged();
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
        holder.meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_META_SP * textScale);
        holder.original.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_BODY_SP * textScale);
        holder.translated.setTextSize(TypedValue.COMPLEX_UNIT_SP, BASE_TRANSLATED_SP * textScale);

        boolean hasTranslation = item.wasTranslated && !TextUtils.isEmpty(item.translatedText);
        if (hasTranslation) {
            // 翻訳文だけを表示する。原文は元のX投稿を開けば確認できる。
            holder.original.setVisibility(View.GONE);
            holder.translated.setVisibility(View.VISIBLE);
            holder.translated.setText(item.translatedText);
            holder.translated.setTextColor(item.textColor);
        } else {
            // 翻訳がない場合(日本語の投稿や翻訳オフのフィルターなど)は原文を表示する。
            holder.translated.setVisibility(View.GONE);
            holder.original.setVisibility(View.VISIBLE);
            holder.original.setText(item.originalText);
            holder.original.setTextColor(item.textColor);
        }

        holder.cardRoot.setCardBackgroundColor(item.backgroundColor);
        holder.accentStripe.setBackgroundColor(item.textColor);

        holder.itemView.setOnClickListener(v -> listener.onOpen(item));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CardView cardRoot;
        final View accentStripe;
        final TextView meta;
        final TextView original;
        final TextView translated;
        final View deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = (CardView) itemView;
            accentStripe = itemView.findViewById(R.id.accent_stripe);
            meta = itemView.findViewById(R.id.text_meta);
            original = itemView.findViewById(R.id.text_original);
            translated = itemView.findViewById(R.id.text_translated);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
