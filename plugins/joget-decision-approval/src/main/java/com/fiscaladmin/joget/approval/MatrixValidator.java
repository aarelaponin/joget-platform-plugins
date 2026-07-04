package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A pure consistency gate for an authority action's bands, so a bad configuration is caught BEFORE
 * it routes a real decision. Validates, for the bands of one action type:
 * <ul>
 *   <li><b>level-exists</b> — each band's level is a known rank;</li>
 *   <li><b>bodyType</b> — SINGLE / CHAIN / COLLEGIAL (blank = SINGLE);</li>
 *   <li><b>ascending chain</b> — a CHAIN lists ≥ 2 levels strictly ascending in rank;</li>
 *   <li><b>quorum</b> — a COLLEGIAL body needs a quorum of at least 2;</li>
 *   <li><b>range sanity</b> — amountMin ≤ amountMax;</li>
 *   <li><b>overlap / gap</b> — among the bands effective on the as-of date, no two amount ranges
 *       overlap and there is no uncovered gap between consecutive bands.</li>
 * </ul>
 * Free of Joget types so it is exhaustively unit-testable; the caller feeds it the live rows.
 */
public final class MatrixValidator {

    private MatrixValidator() {
    }

    public static final class Result {
        public final boolean valid;
        public final List<String> issues;

        Result(List<String> issues) {
            this.issues = issues;
            this.valid = issues.isEmpty();
        }

        @Override
        public String toString() {
            return valid ? "VALID" : (issues.size() + " issue(s)");
        }
    }

    public static Result validate(List<Map<String, String>> bands, String asOfDate) {
        List<String> issues = new ArrayList<String>();
        if (bands == null || bands.isEmpty()) {
            issues.add("no bands defined for this action");
            return new Result(issues);
        }
        // ---- per-band structural checks (all bands) ----
        for (Map<String, String> b : bands) {
            String body = up(s(b, "bodyType"));
            if (body.isEmpty()) {
                body = "SINGLE";
            }
            String level = s(b, "level");
            if (!"CHAIN".equals(body) && DecisionService.rank(level) <= 0) {
                issues.add("unknown level '" + level + "' in band " + range(b));
            }
            if (!body.equals("SINGLE") && !body.equals("CHAIN") && !body.equals("COLLEGIAL")) {
                issues.add("unknown bodyType '" + body + "' in band " + range(b));
            }
            if ("CHAIN".equals(body)) {
                String[] steps = level.split("[,;]");
                if (steps.length < 2) {
                    issues.add("CHAIN needs >= 2 levels in band " + range(b) + " (got '" + level + "')");
                }
                int prev = -1;
                for (String st : steps) {
                    int rk = DecisionService.rank(st.trim());
                    if (rk <= 0) {
                        issues.add("unknown chain level '" + st.trim() + "' in band " + range(b));
                    } else if (rk <= prev) {
                        issues.add("CHAIN not ascending in band " + range(b) + " ('" + level + "')");
                        break;
                    }
                    prev = rk;
                }
            }
            if ("COLLEGIAL".equals(body) && num(s(b, "quorum"), 0) < 2) {
                issues.add("COLLEGIAL quorum must be >= 2 in band " + range(b)
                        + " (got '" + s(b, "quorum") + "')");
            }
            double min = num(s(b, "amountMin"), 0);
            String maxRaw = s(b, "amountMax");
            if (!maxRaw.isEmpty() && num(maxRaw, Double.MAX_VALUE) < min) {
                issues.add("amountMin > amountMax in band " + range(b));
            }
        }
        // ---- overlap / gap on the effective set ----
        List<Map<String, String>> eff = new ArrayList<Map<String, String>>();
        for (Map<String, String> b : bands) {
            if (effectiveOn(b, asOfDate)) {
                eff.add(b);
            }
        }
        Collections.sort(eff, new Comparator<Map<String, String>>() {
            @Override
            public int compare(Map<String, String> a, Map<String, String> b) {
                return Double.compare(num(s(a, "amountMin"), 0), num(s(b, "amountMin"), 0));
            }
        });
        for (int i = 0; i + 1 < eff.size(); i++) {
            Map<String, String> a = eff.get(i);
            Map<String, String> b = eff.get(i + 1);
            double aMax = num(s(a, "amountMax"), Double.MAX_VALUE);
            double bMin = num(s(b, "amountMin"), 0);
            if (aMax >= bMin) {
                issues.add("overlap: band " + range(a) + " overlaps band " + range(b));
            } else if (aMax + 0.01 < bMin) {
                issues.add("gap: uncovered amounts between band " + range(a) + " and band " + range(b));
            }
        }
        return new Result(issues);
    }

    private static boolean effectiveOn(Map<String, String> b, String asOf) {
        if (asOf == null || asOf.isEmpty()) {
            return true;
        }
        String from = s(b, "effectiveFrom");
        String to = s(b, "effectiveTo");
        if (!from.isEmpty() && from.compareTo(asOf) > 0) {
            return false;
        }
        if (!to.isEmpty() && to.compareTo(asOf) < 0) {
            return false;
        }
        return true;
    }

    private static String range(Map<String, String> b) {
        String max = s(b, "amountMax");
        return "[" + s(b, "amountMin") + ".." + (max.isEmpty() ? "∞" : max) + "]";
    }

    private static String s(Map<String, String> m, String k) {
        String v = m == null ? null : m.get(k);
        return v == null ? "" : v.trim();
    }

    private static String up(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private static double num(String s, double dflt) {
        try {
            return (s == null || s.isEmpty()) ? dflt : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
