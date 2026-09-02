package jp.crescendo.xtranslator.filter;

import android.graphics.Color;

import java.util.List;
import java.util.Locale;

import jp.crescendo.xtranslator.data.DefaultFilterEntity;
import jp.crescendo.xtranslator.data.FilterEntity;

public final class FilterMatcher {

    public static final String DEFAULT_FILTER_NAME = "デフォルト設定";

    private FilterMatcher() {}

    public static class Effective {
        public String filterName;
        public boolean translate;
        public boolean popup;
        public boolean sound;
        public int textColor = Color.BLACK;
        public int backgroundColor = Color.WHITE;
    }

    /** 上にあるフィルターほど優先。最初に一致したフィルターを適用し、無ければデフォルト設定を返す。 */
    public static Effective resolve(List<FilterEntity> filters, DefaultFilterEntity defaults, String author, String text) {
        for (FilterEntity f : filters) {
            if (!f.enabled) continue;
            if (matches(f, author, text)) {
                Effective e = new Effective();
                e.filterName = f.name;
                e.translate = f.translateEnabled;
                e.popup = f.popupEnabled;
                e.sound = f.soundEnabled;
                e.textColor = f.textColor;
                e.backgroundColor = f.backgroundColor;
                return e;
            }
        }
        Effective e = new Effective();
        e.filterName = DEFAULT_FILTER_NAME;
        if (defaults != null) {
            e.translate = defaults.translateEnabled;
            e.popup = defaults.popupEnabled;
            e.sound = defaults.soundEnabled;
            e.textColor = defaults.textColor;
            e.backgroundColor = defaults.backgroundColor;
        } else {
            e.translate = true;
            e.popup = true;
            e.sound = true;
            e.textColor = Color.BLACK;
            e.backgroundColor = Color.WHITE;
        }
        return e;
    }

    private static boolean matches(FilterEntity f, String author, String text) {
        boolean authorSpecified = f.authorPattern != null && !f.authorPattern.trim().isEmpty();
        boolean keywordsSpecified = f.keywordsRaw != null && !f.keywordsRaw.trim().isEmpty();
        if (!authorSpecified && !keywordsSpecified) return true;
        boolean authorOk = !authorSpecified || matchesAuthor(f.authorPattern, author);
        boolean keywordOk = !keywordsSpecified || matchesKeywords(f, text);
        return authorOk && keywordOk;
    }

    private static boolean matchesAuthor(String pattern, String author) {
        if (author == null) return false;
        String p = pattern.trim();
        if (p.startsWith("@")) p = p.substring(1);
        if (p.isEmpty()) return true;
        return author.toLowerCase(Locale.ROOT).contains(p.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesKeywords(FilterEntity f, String text) {
        if (text == null) text = "";
        String[] keywords = f.keywordsRaw.split("\n");
        int total = 0;
        int matched = 0;
        for (String raw : keywords) {
            String kw = raw.trim();
            if (kw.isEmpty()) continue;
            total++;
            String haystack = f.caseSensitive ? text : text.toLowerCase(Locale.ROOT);
            String needle = f.caseSensitive ? kw : kw.toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) matched++;
        }
        if (total == 0) return true;
        return f.matchAll ? matched == total : matched > 0;
    }
}
