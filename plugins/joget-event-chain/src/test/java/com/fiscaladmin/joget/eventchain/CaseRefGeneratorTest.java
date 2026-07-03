package com.fiscaladmin.joget.eventchain;

import org.joget.apps.form.dao.FormDataDao;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CaseRefGenerator} — prefix + zero-padded counter from an
 * id-format string, with a collision guard.
 */
public class CaseRefGeneratorTest {

    /** dao where the type-count is `typeCount` and no candidate ever collides. */
    private FormDataDao daoNoCollision(long typeCount) {
        FormDataDao dao = mock(FormDataDao.class);
        // first count() form: the "already referenced of this type" count (2 params)
        // second count() form: the per-candidate collision check (1 param)
        when(dao.count(anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    Object[] params = inv.getArgument(3);
                    return params != null && params.length == 2 ? typeCount : 0L;
                });
        return dao;
    }

    @Test
    public void generate_firstOfType_isPaddedToWidth() {
        CaseRefGenerator g = new CaseRefGenerator(daoNoCollision(0), "cmCase");
        assertEquals("TT-000001", g.generate("TT", "TT-??????"));
    }

    @Test
    public void generate_countDrivesTheNextNumber() {
        CaseRefGenerator g = new CaseRefGenerator(daoNoCollision(5), "cmCase");
        assertEquals("TT-000006", g.generate("TT", "TT-??????"));
    }

    @Test
    public void generate_defaultWidthWhenNoPlaceholders() {
        // no '?' -> prefix is the whole string, default width 6
        CaseRefGenerator g = new CaseRefGenerator(daoNoCollision(0), "cmCase");
        assertEquals("REF000001", g.generate("X", "REF"));
    }

    @Test
    public void generate_advancesPastCollisions() {
        FormDataDao dao = mock(FormDataDao.class);
        // type count = 0 (2-param call) -> starts at 1; collision check (1-param)
        // returns >0 for the first candidate only, forcing an advance to 2.
        when(dao.count(anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    Object[] p = inv.getArgument(3);
                    if (p != null && p.length == 2) return 0L;          // type count
                    return "TT-000001".equals(p[0]) ? 1L : 0L;          // collision
                });
        CaseRefGenerator g = new CaseRefGenerator(dao, "cmCase");
        assertEquals("TT-000002", g.generate("TT", "TT-??????"));
    }
}
