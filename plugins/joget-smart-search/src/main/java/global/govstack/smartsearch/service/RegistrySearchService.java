package global.govstack.smartsearch.service;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Generic Registry Search Service.
 *
 * The farmer-agnostic sibling of {@link FarmerSearchService}: it searches ANY Joget
 * form table (a registry) using columns supplied by configuration, and maps the rows
 * into the SAME response contract the SmartSearchElement already renders
 * (id, nationalId, firstName, lastName, district, village, relevanceScore). This is
 * what makes the Lesotho smart-search plugin reusable for taxpayers (or any registry)
 * without touching the element, its JavaScript, or the proven farmer path — the plugin
 * routes here whenever a `registryTable` is configured on the API definition.
 *
 * Table/column identifiers come only from the (admin-set) plugin configuration and are
 * validated against a safe identifier pattern; the user's search term is always bound
 * as a parameter. No user input reaches the SQL string.
 */
public class RegistrySearchService {

    private static final String CLASS_NAME = RegistrySearchService.class.getName();
    private static final int MAX_RETURN_RESULTS = 20;

    /** Registry mapping, read from the API definition's plugin properties. */
    public static class RegistryConfig {
        public String table;             // e.g. app_fd_dsp_taxpayer
        public String idColumn = "id";   // Joget record id (stored/looked up)
        public String identifierColumn;  // -> nationalId slot   (e.g. c_tin)
        public String primaryColumn;     // -> firstName slot    (e.g. c_name)
        public String secondaryColumn;   // -> lastName slot     (optional)
        public String extraColumn1;      // -> district slot     (optional, e.g. c_taxpayer_type)
        public String extraColumn2;      // -> village slot      (optional, e.g. c_reg_status)
        public List<String> searchColumns = new ArrayList<>(); // columns the free-text term matches

        public boolean isConfigured() {
            return isSafe(table) && isSafe(identifierColumn) && isSafe(primaryColumn);
        }
    }

    private final RegistryConfig cfg;

    public RegistrySearchService(RegistryConfig cfg) {
        this.cfg = cfg;
    }

    /** Partial-knowledge search: match the term against any configured search column. */
    public List<Map<String, Object>> search(String term, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (term == null || term.trim().isEmpty()) return out;
        List<String> cols = cfg.searchColumns.isEmpty()
                ? Arrays.asList(cfg.identifierColumn, cfg.primaryColumn)
                : cfg.searchColumns;

        StringBuilder where = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (!isSafe(cols.get(i))) continue;
            where.append(i == 0 ? "(" : " OR ");
            where.append("LOWER(").append(cols.get(i)).append(") LIKE LOWER(?)");
        }
        where.append(")");

        String sql = "SELECT " + selectList() + " FROM " + cfg.table
                + " WHERE " + where + " ORDER BY " + cfg.primaryColumn
                + " LIMIT " + clamp(limit);
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                String like = "%" + term.trim() + "%";
                int n = 0;
                for (String c : cols) if (isSafe(c)) ps.setString(++n, like);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapRow(rs, 70));
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Registry search failed on " + cfg.table);
        }
        return out;
    }

    /** Exact match on the identifier column (the "by national id" path). */
    public List<Map<String, Object>> exactByIdentifier(String value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty() || !cfg.isConfigured()) return out;
        String sql = "SELECT " + selectList() + " FROM " + cfg.table
                + " WHERE " + cfg.identifierColumn + " = ? LIMIT " + MAX_RETURN_RESULTS;
        try {
            DataSource ds = getDataSource();
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, value.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapRow(rs, 100));
                }
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Registry exact lookup failed on " + cfg.table);
        }
        return out;
    }

    // ---- helpers ------------------------------------------------------------

    private String selectList() {
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        cols.add(cfg.idColumn);
        cols.add(cfg.identifierColumn);
        cols.add(cfg.primaryColumn);
        for (String c : new String[]{cfg.secondaryColumn, cfg.extraColumn1, cfg.extraColumn2})
            if (isSafe(c)) cols.add(c);
        StringBuilder sb = new StringBuilder();
        for (String c : cols) { if (sb.length() > 0) sb.append(", "); sb.append(c); }
        return sb.toString();
    }

    /** Map a registry row into the response contract the SmartSearchElement renders. */
    private Map<String, Object> mapRow(ResultSet rs, int score) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString(cfg.idColumn));
        m.put("nationalId", rs.getString(cfg.identifierColumn));
        m.put("nationalIdMasked", rs.getString(cfg.identifierColumn));
        m.put("firstName", rs.getString(cfg.primaryColumn));
        m.put("lastName", isSafe(cfg.secondaryColumn) ? rs.getString(cfg.secondaryColumn) : "");
        m.put("district", isSafe(cfg.extraColumn1) ? rs.getString(cfg.extraColumn1) : "");
        m.put("districtName", isSafe(cfg.extraColumn1) ? rs.getString(cfg.extraColumn1) : "");
        m.put("village", isSafe(cfg.extraColumn2) ? rs.getString(cfg.extraColumn2) : "");
        m.put("relevanceScore", score);
        return m;
    }

    private static int clamp(int limit) {
        if (limit <= 0 || limit > MAX_RETURN_RESULTS) return MAX_RETURN_RESULTS;
        return limit;
    }

    /** Only allow simple identifiers (table/column names) into the SQL string. */
    private static boolean isSafe(String s) {
        return s != null && !s.trim().isEmpty() && s.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private DataSource getDataSource() {
        return (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
    }
}
