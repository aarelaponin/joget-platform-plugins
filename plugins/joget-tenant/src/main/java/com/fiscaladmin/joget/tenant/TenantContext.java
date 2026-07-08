package com.fiscaladmin.joget.tenant;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

/**
 * Invisible multi-tenancy resolver. The tenant is a PERVASIVE but INVISIBLE scope:
 * never a screen the user navigates and never a field they pick. It is resolved from
 * the user's profile — an admin-maintained mapping (username -&gt; tenantCode) — and
 * stamped onto the caller's own rows behind the scenes. When a user has no mapping the
 * configured default tenant applies, so a single-tenant deployment needs no per-user
 * configuration at all.
 *
 * <p>Pure read helper (never writes). Project-neutral: the mapping table id and the
 * default tenant are configurable (settable statics), defaulting to neutral values.</p>
 */
public class TenantContext {

    /** Neutral default mapping table id and fallback tenant. */
    public static final String DEFAULT_USER_TENANT_FORM = "userTenant";
    public static final String DEFAULT_TENANT = "DEFAULT";

    private static volatile String userTenantFormId = DEFAULT_USER_TENANT_FORM;
    private static volatile String defaultTenant = DEFAULT_TENANT;

    /** Point the resolver at a consumer's mapping table + fallback tenant (call once at start-up). */
    public static void setDefaults(String userTenantForm, String fallbackTenant) {
        if (userTenantForm != null && !userTenantForm.trim().isEmpty()) userTenantFormId = userTenantForm.trim();
        if (fallbackTenant != null && !fallbackTenant.trim().isEmpty()) defaultTenant = fallbackTenant.trim();
    }

    /** The configured fallback tenant (returned when a user carries no mapping). */
    public static String defaultTenant() {
        return defaultTenant;
    }

    private final FormDataDao dao;

    public TenantContext(FormDataDao dao) {
        this.dao = dao;
    }

    /**
     * The tenant that governs the given user, resolved from the profile mapping.
     * Falls back to the configured default when the user is unmapped, blank, or the
     * mapping table is absent — so resolution never returns blank.
     */
    public String resolve(String username) {
        if (username == null || username.trim().isEmpty()) {
            return defaultTenant;
        }
        try {
            dao.updateSchema(userTenantFormId, userTenantFormId, new FormRowSet());
            FormRowSet rows = dao.find(userTenantFormId, userTenantFormId,
                    "WHERE e.customProperties.username = ?1 AND e.customProperties.active = ?2",
                    new Object[]{username.trim(), "true"},
                    "dateCreated", Boolean.FALSE, 0, 1);
            if (rows != null && !rows.isEmpty()) {
                String tenant = rows.get(0).getProperty("tenantCode");
                if (tenant != null && !tenant.trim().isEmpty()) {
                    return tenant.trim();
                }
            }
        } catch (Exception ignored) {
            // mapping table not yet present, or a transient read issue -> safe default
        }
        return defaultTenant;
    }
}
