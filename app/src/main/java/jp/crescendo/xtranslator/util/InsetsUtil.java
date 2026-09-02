package jp.crescendo.xtranslator.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * targetSdk 35以降、Android 15+では強制的にedge-to-edge表示になり、
 * インセットを考慮しないとステータスバーやジェスチャーナビゲーションバーに
 * ボタンなどが隠れて押せなくなる。既存paddingに加算する形でシステムバー分の
 * paddingを与える。
 */
public final class InsetsUtil {
    private InsetsUtil() {}

    public static void applySystemBarPadding(View view, boolean top, boolean bottom) {
        int baseLeft = view.getPaddingLeft();
        int baseTop = view.getPaddingTop();
        int baseRight = view.getPaddingRight();
        int baseBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    baseLeft,
                    baseTop + (top ? bars.top : 0),
                    baseRight,
                    baseBottom + (bottom ? bars.bottom : 0));
            return insets;
        });
        view.requestApplyInsets();
    }
}
