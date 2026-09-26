package com.druvu.lib.fx.dock;

import com.druvu.lib.fx.dock.DockLayout.Entry;
import com.druvu.lib.fx.dock.DockLayout.Leaf;
import com.druvu.lib.fx.dock.DockLayout.Split;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javafx.geometry.Orientation;

/**
 * The text form of a {@link DockLayout} - what goes into a preferences file - and its parser. Grammar:
 *
 * <pre>
 * layout := leaf | split
 * split  := ('H' | 'V') '{' entry (',' entry)+ '}'
 * entry  := share ' ' layout
 * leaf   := [A-Za-z0-9_.-]+
 * </pre>
 *
 * A leaf may itself start with {@code H} or {@code V}; only a brace right after makes it a split.
 *
 * @author Deniss Larka <br>
 *     on 26 Sep 2026
 */
final class DockLayoutText {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.-]+");

    private final String text;
    private int pos;

    private DockLayoutText(String text) {
        this.text = text;
    }

    static DockLayout parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty layout text");
        }
        final DockLayoutText parser = new DockLayoutText(text.strip());
        final DockLayout layout = parser.layout();
        if (parser.pos != parser.text.length()) {
            throw parser.error("unexpected '" + parser.text.charAt(parser.pos) + "'");
        }
        return layout;
    }

    static String format(DockLayout layout) {
        final StringBuilder out = new StringBuilder();
        append(layout, out);
        return out.toString();
    }

    /** Three decimals: enough for any window, and the file stays readable. */
    static String share(double share) {
        return String.format(Locale.ROOT, "%.3f", share);
    }

    static void requireId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("a dock node id is [A-Za-z0-9_.-]+, got '" + id + "'");
        }
    }

    private static void append(DockLayout layout, StringBuilder out) {
        switch (layout) {
            case Leaf leaf -> out.append(leaf.id());
            case Split split -> {
                out.append(split.orientation() == Orientation.HORIZONTAL ? 'H' : 'V').append('{');
                for (int i = 0; i < split.entries().size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    final Entry entry = split.entries().get(i);
                    out.append(share(entry.share())).append(' ');
                    append(entry.child(), out);
                }
                out.append('}');
            }
        }
    }

    private DockLayout layout() {
        if (pos + 1 < text.length() && (text.charAt(pos) == 'H' || text.charAt(pos) == 'V') && text.charAt(pos + 1) == '{') {
            return split();
        }
        return leaf();
    }

    private Split split() {
        final Orientation orientation = text.charAt(pos) == 'H' ? Orientation.HORIZONTAL : Orientation.VERTICAL;
        pos += 2;
        final List<Entry> entries = new ArrayList<>();
        while (true) {
            final double share = share();
            expect(' ');
            entries.add(new Entry(layout(), share));
            if (pos < text.length() && text.charAt(pos) == ',') {
                pos++;
                continue;
            }
            expect('}');
            break;
        }
        try {
            return new Split(orientation, entries);
        } catch (IllegalArgumentException invalid) {
            throw error(invalid.getMessage());
        }
    }

    private Leaf leaf() {
        final int start = pos;
        while (pos < text.length() && isIdChar(text.charAt(pos))) {
            pos++;
        }
        if (start == pos) {
            throw error("expected a dock node id");
        }
        return new Leaf(text.substring(start, pos));
    }

    private double share() {
        final int start = pos;
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw error("expected a share");
        }
        try {
            return Double.parseDouble(text.substring(start, pos));
        } catch (NumberFormatException malformed) {
            throw error("malformed share '" + text.substring(start, pos) + "'");
        }
    }

    private void expect(char expected) {
        if (pos >= text.length() || text.charAt(pos) != expected) {
            throw error("expected '" + expected + "'");
        }
        pos++;
    }

    private static boolean isIdChar(char c) {
        return Character.isLetterOrDigit(c) && c < 128 || c == '_' || c == '.' || c == '-';
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("dock layout: " + what + " at " + pos + " in '" + text + "'");
    }
}
