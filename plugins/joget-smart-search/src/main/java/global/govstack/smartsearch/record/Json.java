package global.govstack.smartsearch.record;

import java.util.Collection;
import java.util.Map;

/**
 * The few lines of JSON the record search writes: its answer to the page, and the settings the
 * page's script is given. Written here rather than with org.json because the platform ships its
 * own org.json classes, and the record search reads nothing back in JSON. A string is written so
 * that it may stand inside a script element: the characters that could close the element or
 * break a line of script are written as escapes.
 */
public final class Json {

    private Json() {
    }

    /** A value: a string, a number, a boolean, a collection or a map of them; null is null. */
    public static String of(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean || v instanceof Number) {
            return String.valueOf(v);
        }
        if (v instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                sb.append(str(String.valueOf(e.getKey()))).append(':').append(of(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (v instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            for (Object o : (Collection<?>) v) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                sb.append(of(o));
            }
            return sb.append(']').toString();
        }
        return str(String.valueOf(v));
    }

    /** A string, quoted and escaped. */
    public static String str(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '<': sb.append("\\u003c"); break;
                case '>': sb.append("\\u003e"); break;
                case '&': sb.append("\\u0026"); break;
                case ' ': sb.append("\\u2028"); break;
                case ' ': sb.append("\\u2029"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
