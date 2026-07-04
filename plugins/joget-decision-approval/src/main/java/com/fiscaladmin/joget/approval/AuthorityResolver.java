package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

/**
 * Resolve an approver's rank level from their DIRECTORY identity, not a self-declared field.
 * API-only by design:
 * <ul>
 *   <li>the user's role-groups come from the Joget <b>directory API</b>
 *       ({@code DirectoryManager.getGroupByUsername}) — never a raw {@code dir_user_group} query;</li>
 *   <li>the role-group&rarr;level map comes from the <b>form-data API</b> (FormDataDao over the
 *       {@code mmRoleLevel} config carrier), overlaid on a consumer-supplied default map so the
 *       gate is configurable.</li>
 * </ul>
 * The directory lookup is injected as a {@link GroupSource} so the resolution logic is unit-testable
 * without a live directory; {@link #directoryGroups()} is the live binding.
 *
 * <p>The default role-group&rarr;level map ships EMPTY (no project group ids in platform code); a
 * consumer registers its defaults once via {@link #registerRoleLevelDefault}, or seeds mmRoleLevel.
 */
public final class AuthorityResolver {

    /** Abstracts "which role-groups does this user hold" so the logic is testable. */
    public interface GroupSource {
        List<String> groupsOf(String username);
    }

    /** Consumer-supplied role-group &rarr; rank-level defaults (overridden/extended by mmRoleLevel rows). */
    static final Map<String, String> DEFAULT_MAP = new HashMap<String, String>();

    /** Register a default role-group &rarr; level mapping (call once at consumer start-up). */
    public static void registerRoleLevelDefault(String roleGroup, String level) {
        if (roleGroup != null && !roleGroup.trim().isEmpty() && level != null && !level.trim().isEmpty()) {
            DEFAULT_MAP.put(roleGroup.trim(), level.trim().toUpperCase());
        }
    }

    private final FormDataDao dao;
    private final GroupSource groups;

    public AuthorityResolver(FormDataDao dao, GroupSource groups) {
        this.dao = dao;
        this.groups = groups;
    }

    /**
     * The highest rank level the user holds through their role-groups, or "" if none maps. The
     * caller treats "" as "no resolvable authority" (the rank gate then blocks any approval).
     */
    public String resolveLevel(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "";
        }
        Map<String, String> map = roleLevelMap();
        String best = "";
        List<String> gids = groups == null ? null : groups.groupsOf(username);
        if (gids != null) {
            for (String gid : gids) {
                if (gid == null) {
                    continue;
                }
                String lvl = map.get(gid);
                if (lvl != null && DecisionService.rank(lvl) > DecisionService.rank(best)) {
                    best = lvl;
                }
            }
        }
        return best;
    }

    /** Default map overlaid by any mmRoleLevel config rows (roleGroup -&gt; level). */
    Map<String, String> roleLevelMap() {
        Map<String, String> m = new HashMap<String, String>(DEFAULT_MAP);
        try {
            FormRowSet rows = dao.find("mmRoleLevel", "mmRoleLevel", null, null, null, null, 0, -1);
            if (rows != null) {
                for (FormRow r : rows) {
                    String g = Rows.prop(r, "roleGroup");
                    String l = Rows.prop(r, "level");
                    if (!g.isEmpty() && !l.isEmpty()) {
                        m.put(g, l.trim().toUpperCase());
                    }
                }
            }
        } catch (Exception ignore) {
            // mmRoleLevel not provisioned yet → the default map is authoritative.
        }
        return m;
    }

    /** Live binding: the user's role-groups from the Joget directory API (no dir_* SQL). */
    public static GroupSource directoryGroups() {
        return new GroupSource() {
            @Override
            public List<String> groupsOf(String username) {
                List<String> ids = new ArrayList<String>();
                try {
                    Object dm = AppUtil.getApplicationContext().getBean("directoryManager");
                    java.lang.reflect.Method m = dm.getClass().getMethod("getGroupByUsername", String.class);
                    Object res = m.invoke(dm, username);
                    if (res instanceof Collection) {
                        for (Object g : (Collection<?>) res) {
                            Object id = g.getClass().getMethod("getId").invoke(g);
                            if (id != null) {
                                ids.add(id.toString());
                            }
                        }
                    }
                } catch (Exception ignore) {
                    // directory unavailable / pseudo-user → no groups.
                }
                return ids;
            }
        };
    }
}
