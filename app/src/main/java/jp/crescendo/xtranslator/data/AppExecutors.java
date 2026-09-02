package jp.crescendo.xtranslator.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {}

    public static void background(Runnable task) {
        BACKGROUND.execute(task);
    }

    public static void main(Runnable task) {
        MAIN.post(task);
    }
}
