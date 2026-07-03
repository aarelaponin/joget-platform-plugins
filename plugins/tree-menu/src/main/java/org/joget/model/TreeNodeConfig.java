package org.joget.model;

import org.joget.apps.form.model.FormRow;

/**
 * Generic tree node configuration.
 * Can represent either a folder (grouping) or a data source (connected to a Joget table).
 */
public class TreeNodeConfig {

    // Node types
    public static final String TYPE_FOLDER = "folder";
    public static final String TYPE_DATA = "data";

    private String id;
    private String parentId;
    private String label;
    private String icon;
    private int sortOrder;
    private String nodeType;  // "folder" or "data"

    // Data source configuration (only for type=data)
    private String dataTable;
    private String formId;
    private String labelField;
    private String labelTemplate;
    private String parentField;  // For hierarchical data
    private String sortField;
    private String filter;

    public TreeNodeConfig() {
    }

    /**
     * Factory method to create from FormRow.
     */
    public static TreeNodeConfig fromFormRow(FormRow row) {
        if (row == null) {
            return null;
        }

        TreeNodeConfig config = new TreeNodeConfig();
        config.setId(row.getId());
        config.setParentId(getStr(row, "c_parent_id"));
        config.setLabel(getStr(row, "c_label"));
        config.setIcon(getStr(row, "c_icon"));
        config.setSortOrder(getInt(row, "c_sort_order", 0));
        config.setNodeType(getStr(row, "c_node_type"));
        config.setDataTable(getStr(row, "c_data_table"));
        config.setFormId(getStr(row, "c_form_id"));
        config.setLabelField(getStr(row, "c_label_field"));
        config.setLabelTemplate(getStr(row, "c_label_template"));
        config.setParentField(getStr(row, "c_parent_field"));
        config.setSortField(getStr(row, "c_sort_field"));
        config.setFilter(getStr(row, "c_filter"));

        return config;
    }

    private static String getStr(FormRow row, String key) {
        Object val = row.getProperty(key);
        return val != null ? val.toString() : null;
    }

    private static int getInt(FormRow row, String key, int defaultVal) {
        Object val = row.getProperty(key);
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    // ===== Helper methods =====

    public boolean isFolder() {
        return TYPE_FOLDER.equals(nodeType);
    }

    public boolean isDataSource() {
        return TYPE_DATA.equals(nodeType);
    }

    public boolean isHierarchical() {
        return parentField != null && !parentField.trim().isEmpty();
    }

    public boolean isRoot() {
        return parentId == null || parentId.trim().isEmpty();
    }

    public String getEffectiveSortField() {
        if (sortField != null && !sortField.trim().isEmpty()) {
            return sortField;
        }
        return labelField;
    }

    public String getEffectiveIcon() {
        if (icon != null && !icon.trim().isEmpty()) {
            return icon;
        }
        return isFolder() ? "fas fa-folder" : "fas fa-database";
    }

    // ===== Getters and Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getDataTable() {
        return dataTable;
    }

    public void setDataTable(String dataTable) {
        this.dataTable = dataTable;
    }

    public String getFormId() {
        return formId;
    }

    public void setFormId(String formId) {
        this.formId = formId;
    }

    public String getLabelField() {
        return labelField;
    }

    public void setLabelField(String labelField) {
        this.labelField = labelField;
    }

    public String getLabelTemplate() {
        return labelTemplate;
    }

    public void setLabelTemplate(String labelTemplate) {
        this.labelTemplate = labelTemplate;
    }

    public String getParentField() {
        return parentField;
    }

    public void setParentField(String parentField) {
        this.parentField = parentField;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Override
    public String toString() {
        return "TreeNodeConfig{id='" + id + "', label='" + label + "', type='" + nodeType + "'}";
    }
}
