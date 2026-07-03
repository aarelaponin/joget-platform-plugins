package global.govstack.rulesapi.compiler;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for FieldMapping — field-id to SQL table/column mapping and the deterministic
 * SQL fragments (column reference, grid JOIN, correlation) it produces. Pure logic.
 */
public class FieldMappingTest {

    @Test
    public void simpleFieldResolvesToAliasDotCColumn() {
        FieldMapping m = new FieldMapping();
        m.setMainTable("app_fd_person", "f");
        m.addField("age", "person", "f", "age", "NUMBER");

        assertEquals("f.c_age", m.getSqlReference("age"));
        assertEquals("NUMBER", m.getField("age").getFieldType());
        assertEquals("app_fd_person", m.getMainTableName());
        assertEquals("f", m.getMainTableAlias());
    }

    @Test
    public void unknownFieldFallsBackToMainTable() {
        FieldMapping m = new FieldMapping();
        m.setMainTable("app_fd_person", "f");
        assertEquals("f.c_status", m.getSqlReference("status"));
        assertNull(m.getField("status"));
    }

    @Test
    public void gridProducesJoinAndCorrelation() {
        FieldMapping m = new FieldMapping();
        m.setMainTable("app_fd_person", "f");
        m.addGrid("household", "app_fd_household", "hm", "personId", "f");

        FieldMapping.GridInfo g = m.getGrid("household");
        assertNotNull(g);
        assertEquals("LEFT JOIN app_fd_household hm ON hm.c_personId = f.id", g.getJoinClause());
        assertEquals("hm.c_personId = f.id", g.getCorrelation());
        assertTrue(m.isGridField("household"));
        assertFalse(m.isGridField("age"));
    }

    @Test
    public void gridChildFieldIsResolvedToItsGrid() {
        FieldMapping m = new FieldMapping();
        m.setMainTable("app_fd_person", "f");
        m.addGrid("household", "app_fd_household", "hm", "personId", "f");
        m.addGridChildField("household.age", "household", "hm", "age", "NUMBER", "household");

        assertTrue(m.isGridChildField("household.age"));
        assertFalse(m.isGridChildField("age"));
        FieldMapping.GridInfo g = m.getGridForChildField("household.age");
        assertNotNull(g);
        assertEquals("household", g.getGridFieldId());
        assertEquals("hm.c_age", m.getSqlReference("household.age"));
    }

    @Test
    public void sampleMappingIsWellFormed() {
        FieldMapping m = FieldMapping.createSampleEligibilityMapping();
        assertEquals("app_fd_applicant", m.getMainTableName());
        assertNotNull("age field present", m.getField("age"));
        assertEquals("f.c_age", m.getSqlReference("age"));
        assertEquals("two demo grids", 2, m.getAllGrids().size());
    }
}
