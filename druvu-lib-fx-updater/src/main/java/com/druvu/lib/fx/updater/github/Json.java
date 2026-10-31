package com.druvu.lib.fx.updater.github;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reader for the JSON the GitHub API answers with, enough for one release: objects become {@code Map<String,
 * Object>}, arrays {@code List<Object>}, strings {@code String}, numbers {@code Long} or {@code Double}, and the three
 * literals {@code Boolean} or {@code null}. No dependency, no writing, no streaming.
 */
final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @return the document's one value
     * @throws IllegalArgumentException naming the position where the text stopped being JSON
     */
    static Object read(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.skipWhitespace();
        if (json.at != text.length()) {
            throw json.malformed("text after the document");
        }
        return value;
    }

    /** @throws IllegalArgumentException when the value is not an object */
    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("an object was expected, not " + describe(value));
    }

    /** The member as text; empty when absent, null or not a string. */
    static String string(Map<String, Object> object, String key) {
        return object.get(key) instanceof String text ? text : "";
    }

    /** The member as a whole number; the default when absent or not a number. */
    static long number(Map<String, Object> object, String key, long defaultValue) {
        return object.get(key) instanceof Number number ? number.longValue() : defaultValue;
    }

    /**
     * The objects of the member's array; those of its elements that are not objects are left out; empty when absent.
     */
    static List<Map<String, Object>> objects(Map<String, Object> object, String key) {
        List<Map<String, Object>> objects = new ArrayList<>();
        if (object.get(key) instanceof List<?> elements) {
            for (Object element : elements) {
                if (element instanceof Map<?, ?>) {
                    objects.add(object(element));
                }
            }
        }
        return objects;
    }

    private Object value() {
        skipWhitespace();
        if (at >= text.length()) {
            throw malformed("a value");
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> members = new LinkedHashMap<>();
        at++;
        skipWhitespace();
        if (peek() == '}') {
            at++;
            return members;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw malformed("a member name");
            }
            String name = string();
            skipWhitespace();
            expect(':');
            members.put(name, value());
            skipWhitespace();
            char next = peek();
            at++;
            if (next == '}') {
                return members;
            }
            if (next != ',') {
                throw malformed("',' or '}'");
            }
        }
    }

    private List<Object> array() {
        List<Object> elements = new ArrayList<>();
        at++;
        skipWhitespace();
        if (peek() == ']') {
            at++;
            return elements;
        }
        while (true) {
            elements.add(value());
            skipWhitespace();
            char next = peek();
            at++;
            if (next == ']') {
                return elements;
            }
            if (next != ',') {
                throw malformed("',' or ']'");
            }
        }
    }

    private String string() {
        at++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw malformed("the closing quote");
            }
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                throw malformed("an escape");
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(unicode());
                default -> throw malformed("a known escape");
            }
        }
    }

    private char unicode() {
        if (at + 4 > text.length()) {
            throw malformed("four hex digits");
        }
        try {
            char c = (char) Integer.parseInt(text, at, at + 4, 16);
            at += 4;
            return c;
        } catch (NumberFormatException e) {
            throw malformed("four hex digits");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw malformed("'" + word + "'");
        }
        at += word.length();
        return value;
    }

    private Number number() {
        int start = at;
        while (at < text.length() && isNumberChar(text.charAt(at))) {
            at++;
        }
        String token = text.substring(start, at);
        if (token.isEmpty()) {
            throw malformed("a value");
        }
        try {
            boolean integral = token.chars().noneMatch(c -> c == '.' || c == 'e' || c == 'E');
            // Two returns on purpose: a conditional expression would promote the Long to a Double.
            if (integral) {
                return Long.valueOf(token);
            }
            return Double.valueOf(token);
        } catch (NumberFormatException e) {
            throw malformed("a number");
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
    }

    private void expect(char c) {
        if (peek() != c) {
            throw malformed("'" + c + "'");
        }
        at++;
    }

    private char peek() {
        if (at >= text.length()) {
            throw malformed("more text");
        }
        return text.charAt(at);
    }

    private void skipWhitespace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private IllegalArgumentException malformed(String expected) {
        return new IllegalArgumentException("malformed JSON at " + at + ": expected " + expected);
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
