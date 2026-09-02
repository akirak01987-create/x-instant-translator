package jp.crescendo.xtranslator.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.data.AppDatabase;
import jp.crescendo.xtranslator.data.AppExecutors;
import jp.crescendo.xtranslator.data.FilterEntity;

public class FilterListFragment extends Fragment implements FilterAdapter.Listener, FilterReorderCallback.Listener {
    private FilterAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_filter_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new FilterAdapter(this);
        RecyclerView recycler = view.findViewById(R.id.recycler_filters);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(new FilterReorderCallback(adapter, this));
        helper.attachToRecyclerView(recycler);
        adapter.setItemTouchHelper(helper);

        view.findViewById(R.id.btn_add_filter).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FilterEditActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> {
            List<FilterEntity> filters = db.filterDao().getAll();
            AppExecutors.main(() -> {
                if (isAdded()) adapter.submit(filters);
            });
        });
    }

    @Override
    public void onEditFilter(FilterEntity filter) {
        Intent intent = new Intent(requireContext(), FilterEditActivity.class);
        intent.putExtra(FilterEditActivity.EXTRA_FILTER_ID, filter.id);
        startActivity(intent);
    }

    @Override
    public void onToggleEnabled(FilterEntity filter, boolean enabled) {
        filter.enabled = enabled;
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> db.filterDao().update(filter));
    }

    @Override
    public void onEditDefault() {
        Intent intent = new Intent(requireContext(), FilterEditActivity.class);
        intent.putExtra(FilterEditActivity.EXTRA_IS_DEFAULT, true);
        startActivity(intent);
    }

    @Override
    public void onOrderChanged(List<FilterEntity> newOrder) {
        persistOrder(newOrder);
    }

    @Override
    public void onOrderFinalized() {
        persistOrder(new ArrayList<>(adapter.getFilters()));
    }

    private void persistOrder(List<FilterEntity> newOrder) {
        AppDatabase db = AppDatabase.getInstance(requireContext());
        AppExecutors.background(() -> {
            for (int i = 0; i < newOrder.size(); i++) {
                FilterEntity f = newOrder.get(i);
                if (f.sortOrder != i) {
                    f.sortOrder = i;
                    db.filterDao().update(f);
                }
            }
        });
    }
}
