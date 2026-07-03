package org.joget.model;

import org.joget.apps.form.model.FormRow;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link TreeNodeConfig} — the folder/data node value object.
 * Covers the classification helpers, the effective-value fallbacks, and the
 * FormRow factory mapping. Uses a real (Properties-backed) FormRow.
 */
public class TreeNodeConfigTest {

    @Test
    public void folderNode_isFolderNotDataSource() {
        TreeNodeConfig n = new TreeNodeConfig();
        n.setNodeType(TreeNodeConfig.TYPE_FOLDER);
        assertTrue(n.isFolder());
        assertFalse(n.isDataSource());
    }

    @Test
    public void dataNode_isDataSourceNotFolder() {
        TreeNodeConfig n = new TreeNodeConfig();
        n.setNodeType(TreeNodeConfig.TYPE_DATA);
        assertTrue(n.isDataSource());
        assertFalse(n.isFolder());
    }

    @Test
    public void isRoot_trueWhenParentBlankOrNull() {
        TreeNodeConfig n = new TreeNodeConfig();
        assertTrue("null parent → root", n.isRoot());
        n.setParentId("   ");
        assertTrue("blank parent → root", n.isRoot());
        n.setParentId("p1");
        assertFalse("non-blank parent → not root", n.isRoot());
    }

    @Test
    public void isHierarchical_trueOnlyWhenParentFieldSet() {
        TreeNodeConfig n = new TreeNodeConfig();
        assertFalse(n.isHierarchical());
        n.setParentField("c_parent");
        assertTrue(n.isHierarchical());
    }

    @Test
    public void effectiveSortField_fallsBackToLabelField() {
        TreeNodeConfig n = new TreeNodeConfig();
        n.setLabelField("c_name");
        assertEquals("no sortField → use labelField", "c_name", n.getEffectiveSortField());
        n.setSortField("c_order");
        assertEquals("explicit sortField wins", "c_order", n.getEffectiveSortField());
    }

    @Test
    public void effectiveIcon_defaultsByNodeType() {
        TreeNodeConfig folder = new TreeNodeConfig();
        folder.setNodeType(TreeNodeConfig.TYPE_FOLDER);
        assertEquals("fas fa-folder", folder.getEffectiveIcon());

        TreeNodeConfig data = new TreeNodeConfig();
        data.setNodeType(TreeNodeConfig.TYPE_DATA);
        assertEquals("fas fa-database", data.getEffectiveIcon());

        data.setIcon("fas fa-star");
        assertEquals("explicit icon wins", "fas fa-star", data.getEffectiveIcon());
    }

    @Test
    public void fromFormRow_nullReturnsNull() {
        assertNull(TreeNodeConfig.fromFormRow(null));
    }

    @Test
    public void fromFormRow_mapsColumnsIncludingIntSortOrder() {
        FormRow row = new FormRow();
        row.setId("node1");
        row.setProperty("c_parent_id", "root");
        row.setProperty("c_label", "Districts");
        row.setProperty("c_node_type", TreeNodeConfig.TYPE_DATA);
        row.setProperty("c_data_table", "app_fd_district");
        row.setProperty("c_label_field", "c_name");
        row.setProperty("c_sort_order", "5");

        TreeNodeConfig n = TreeNodeConfig.fromFormRow(row);

        assertNotNull(n);
        assertEquals("node1", n.getId());
        assertEquals("root", n.getParentId());
        assertEquals("Districts", n.getLabel());
        assertTrue(n.isDataSource());
        assertEquals("app_fd_district", n.getDataTable());
        assertEquals("c_name", n.getLabelField());
        assertEquals("string c_sort_order parses to int", 5, n.getSortOrder());
    }

    @Test
    public void fromFormRow_badSortOrderDefaultsToZero() {
        FormRow row = new FormRow();
        row.setId("n");
        row.setProperty("c_sort_order", "not-a-number");
        TreeNodeConfig n = TreeNodeConfig.fromFormRow(row);
        assertEquals("unparseable sort order → 0", 0, n.getSortOrder());
    }
}
