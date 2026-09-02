package jp.crescendo.xtranslator.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import jp.crescendo.xtranslator.R;
import jp.crescendo.xtranslator.util.InsetsUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG_HISTORY = "history";
    private static final String TAG_FILTERS = "filters";
    private static final String TAG_SETTINGS = "settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        askNotificationPermission();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        InsetsUtil.applySystemBarPadding(bottomNav, false, true);
        InsetsUtil.applySystemBarPadding(findViewById(R.id.fragment_container), true, false);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                showFragment(TAG_HISTORY, HistoryFragment::new);
                return true;
            } else if (id == R.id.nav_filters) {
                showFragment(TAG_FILTERS, FilterListFragment::new);
                return true;
            } else if (id == R.id.nav_settings) {
                showFragment(TAG_SETTINGS, SettingsFragment::new);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            showFragment(TAG_HISTORY, HistoryFragment::new);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private interface FragmentFactory {
        Fragment create();
    }

    private void showFragment(String tag, FragmentFactory factory) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        for (Fragment f : fm.getFragments()) {
            tx.hide(f);
        }
        Fragment existing = fm.findFragmentByTag(tag);
        if (existing == null) {
            tx.add(R.id.fragment_container, factory.create(), tag);
        } else {
            tx.show(existing);
        }
        tx.commit();
    }
}
