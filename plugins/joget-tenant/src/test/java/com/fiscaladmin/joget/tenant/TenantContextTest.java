package com.fiscaladmin.joget.tenant;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;

/** TenantContext — profile→tenant resolution with a safe default. */
public class TenantContextTest {

    private FormDataDao dao;
    private final List<FormRow> mappings = new ArrayList<FormRow>();

    @Before
    public void setUp() {
        dao = mock(FormDataDao.class);
        mappings.clear();
        when(dao.find(anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    FormRowSet out = new FormRowSet();
                    if ("userTenant".equals(inv.getArgument(0))) {
                        Object[] ps = (Object[]) inv.getArgument(3);
                        String user = ps != null && ps.length > 0 ? String.valueOf(ps[0]) : "";
                        for (FormRow m : mappings) {
                            if (user.equals(m.getProperty("username"))
                                    && "true".equals(m.getProperty("active"))) {
                                out.add(m);
                            }
                        }
                    }
                    return out;
                });
    }

    private TenantContext svc() {
        return new TenantContext(dao);
    }

    @Test
    public void mappedUserResolvesToItsTenant() {
        mappings.add(map("alice", "TST", "true"));
        assertEquals("TST", svc().resolve("alice"));
    }

    @Test
    public void unmappedUserFallsBackToDefault() {
        assertEquals(TenantContext.DEFAULT_TENANT, svc().resolve("nobody"));
    }

    @Test
    public void inactiveMappingIsIgnored() {
        mappings.add(map("bob", "TST", "false"));
        assertEquals(TenantContext.DEFAULT_TENANT, svc().resolve("bob"));
    }

    @Test
    public void blankUsernameResolvesToDefault() {
        assertEquals(TenantContext.DEFAULT_TENANT, svc().resolve(""));
        assertEquals(TenantContext.DEFAULT_TENANT, svc().resolve(null));
    }

    private static FormRow map(String user, String tenant, String active) {
        FormRow r = new FormRow();
        r.setProperty("username", user);
        r.setProperty("tenantCode", tenant);
        r.setProperty("active", active);
        return r;
    }
}
