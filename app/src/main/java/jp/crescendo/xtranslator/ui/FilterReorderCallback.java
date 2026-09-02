package jp.crescendo.xtranslator.ui;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class FilterReorderCallback extends ItemTouchHelper.Callback {
    public interface Listener {
        void onOrderFinalized();
    }

    private final FilterAdapter adapter;
    private final Listener listener;

    public FilterReorderCallback(FilterAdapter adapter, Listener listener) {
        this.adapter = adapter;
        this.listener = listener;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false; // ドラッグはハンドルのタッチ開始でのみ発生させる
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        if (!(viewHolder instanceof FilterAdapter.FilterViewHolder)) return 0;
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        if (!(target instanceof FilterAdapter.FilterViewHolder)) return false;
        int from = viewHolder.getAdapterPosition();
        int to = target.getAdapterPosition();
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
        adapter.moveItem(from, to);
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // スワイプ削除は使用しない
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        listener.onOrderFinalized();
    }
}
