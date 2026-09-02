package jp.crescendo.xtranslator.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.FilterEntity;
import jp.crescendo.xtranslator.filter.FilterMatcher;

public class FilterAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_FILTER = 0;
    private static final int TYPE_DEFAULT = 1;

    public interface Listener {
        void onEditFilter(FilterEntity filter);

        void onToggleEnabled(FilterEntity filter, boolean enabled);

        void onEditDefault();

        void onOrderChanged(List<FilterEntity> newOrder);
    }

    private final List<FilterEntity> filters = new ArrayList<>();
    private final Listener listener;
    private ItemTouchHelper itemTouchHelper;

    public FilterAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItemTouchHelper(ItemTouchHelper helper) {
        this.itemTouchHelper = helper;
    }

    public void submit(List<FilterEntity> newFilters) {
        filters.clear();
        filters.addAll(newFilters);
        notifyDataSetChanged();
    }

    public List<FilterEntity> getFilters() {
        return filters;
    }

    public boolean isFilterPosition(int position) {
        return position >= 0 && position < filters.size();
    }

    public void moveItem(int from, int to) {
        if (!isFilterPosition(from) || !isFilterPosition(to)) return;
        Collections.swap(filters, from, to);
        notifyItemMoved(from, to);
    }

    @Override
    public int getItemViewType(int position) {
        return position < filters.size() ? TYPE_FILTER : TYPE_DEFAULT;
    }

    @Override
    public int getItemCount() {
        return filters.size() + 1; // +1 = デフォルト設定行
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DEFAULT) {
            View view = inflater.inflate(R.layout.item_default_filter, parent, false);
            return new DefaultViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_filter, parent, false);
        return new FilterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DefaultViewHolder) {
            holder.itemView.setOnClickListener(v -> listener.onEditDefault());
            return;
        }
        FilterViewHolder h = (FilterViewHolder) holder;
        FilterEntity f = filters.get(position);

        h.name.setText(FilterMatcher.describe(f));

        GradientDrawable dot = (GradientDrawable) h.colorDot.getBackground().mutate();
        dot.setColor(f.textColor);

        h.enabledSwitch.setOnCheckedChangeListener(null);
        h.enabledSwitch.setChecked(f.enabled);
        h.enabledSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (checked != f.enabled) listener.onToggleEnabled(f, checked);
        });

        h.itemView.setOnClickListener(v -> listener.onEditFilter(f));
        h.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                itemTouchHelper.startDrag(h);
            }
            return false;
        });
    }

    static class FilterViewHolder extends RecyclerView.ViewHolder {
        final ImageView dragHandle;
        final View colorDot;
        final TextView name;
        final Switch enabledSwitch;

        FilterViewHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            colorDot = itemView.findViewById(R.id.color_dot);
            name = itemView.findViewById(R.id.text_name);
            enabledSwitch = itemView.findViewById(R.id.switch_enabled);
        }
    }

    static class DefaultViewHolder extends RecyclerView.ViewHolder {
        DefaultViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
