package org.joget.service;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.model.TreeNodeConfig;
import org.joget.util.FormDataDaoHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clean API for Tree Menu operations.
 * Separates UI concerns from data access.
 *
 * API Methods:
 * - getTreeConfig(): Returns initial tree structure (folders + data source definitions)
 * - getChildren(nodeId, parentRecordId, page, pageSize): Returns children of a node
 */
public class TreeMenuApi {

    private static final String CLASS_NAME = TreeMenuApi.class.getName();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private final String configTable;
    private final AppDefinition appDef;

    public TreeMenuApi(String configTable) {
        this.configTable = configTable;
        this.appDef = AppUtil.getCurrentAppDefinition();
    }

    // ==================== PUBLIC API ====================

    /**
     * Get initial tree configuration.
     * Returns all folder nodes and data source definitions (not the actual records).
     *
     * @return JSON array of tree nodes for initial display
     */
    public JSONArray getTreeConfig() {
        try {
            List<TreeNodeConfig> configs = loadAllConfigs();
            return buildConfigTree(configs);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting tree config");
            return new JSONArray();
        }
    }

    /**
     * Get children of a node.
     *
     * @param configNodeId The config node ID (for data sources)
     * @param parentRecordId Parent record ID (for hierarchical data, null for root)
     * @param page Page number (1-based)
     * @param pageSize Records per page
     * @return JSON object with nodes array and pagination info
     */
    public JSONObject getChildren(String configNodeId, String parentRecordId, int page, int pageSize) {
        JSONObject result = new JSONObject();

        try {
            TreeNodeConfig config = loadConfigById(configNodeId);

            if (config == null || !config.isDataSource()) {
                result.put("nodes", new JSONArray());
                result.put("hasMore", false);
                result.put("error", "Invalid config node: " + configNodeId);
                return result;
            }

            return loadRecords(config, parentRecordId, page, pageSize);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting children for node: " + configNodeId);
            try {
                result.put("nodes", new JSONArray());
                result.put("hasMore", false);
                result.put("error", e.getMessage());
            } catch (Exception ex) {
                // ignore
            }
            return result;
        }
    }

    // ==================== INTERNAL METHODS ====================

    /**
     * Load all configuration nodes from the config table.
     */
    private List<TreeNodeConfig> loadAllConfigs() {
        List<TreeNodeConfig> configs = new ArrayList<>();

        if (configTable == null || configTable.isEmpty()) {
            return configs;
        }

        FormRowSet rows = FormDataDaoHelper.loadFormData(
                configTable, appDef, "c_sort_order", false, null, null);

        if (rows != null) {
            for (Object obj : rows) {
                if (obj instanceof FormRow) {
                    TreeNodeConfig config = TreeNodeConfig.fromFormRow((FormRow) obj);
                    if (config != null) {
                        configs.add(config);
                    }
                }
            }
        }

        return configs;
    }

    /**
     * Load a single config by ID.
     */
    private TreeNodeConfig loadConfigById(String id) {
        if (configTable == null || id == null) {
            return null;
        }

        FormRow row = FormDataDaoHelper.loadFormDataById(configTable, appDef, id);
        return row != null ? TreeNodeConfig.fromFormRow(row) : null;
    }

    /**
     * Build the initial tree structure from config nodes.
     */
    private JSONArray buildConfigTree(List<TreeNodeConfig> configs) throws Exception {
        JSONArray nodes = new JSONArray();

        // Sort by sortOrder
        configs.sort(Comparator.comparingInt(TreeNodeConfig::getSortOrder));

        // Group by parent
        Map<String, List<TreeNodeConfig>> byParent = new HashMap<>();
        for (TreeNodeConfig c : configs) {
            String parentId = c.getParentId();
            if (parentId == null) parentId = "";
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(c);
        }

        // Build nodes recursively starting from roots
        List<TreeNodeConfig> roots = byParent.getOrDefault("", new ArrayList<>());
        for (TreeNodeConfig root : roots) {
            addConfigNode(nodes, root, "#", byParent);
        }

        return nodes;
    }

    /**
     * Add a config node and its children to the tree.
     */
    private void addConfigNode(JSONArray nodes, TreeNodeConfig config, String parentNodeId,
                               Map<String, List<TreeNodeConfig>> byParent) throws Exception {

        String nodeId = "config_" + config.getId();
        List<TreeNodeConfig> children = byParent.getOrDefault(config.getId(), new ArrayList<>());

        // Determine if node has children
        boolean hasChildren = !children.isEmpty() || config.isDataSource();

        // Create node JSON
        JSONObject node = new JSONObject();
        node.put("id", nodeId);
        node.put("parent", parentNodeId);
        node.put("text", config.getLabel() != null ? config.getLabel() : "Untitled");
        node.put("icon", config.getEffectiveIcon());
        node.put("type", config.isFolder() ? "folder" : "dataSource");
        node.put("children", hasChildren);

        // Node data
        JSONObject data = new JSONObject();
        data.put("configId", config.getId());
        data.put("nodeType", config.getNodeType());
        if (config.isDataSource()) {
            data.put("dataTable", config.getDataTable());
            data.put("formId", config.getFormId());
            data.put("isHierarchical", config.isHierarchical());
        }
        node.put("data", data);

        // State (open root folders by default)
        if (parentNodeId.equals("#") && config.isFolder()) {
            JSONObject state = new JSONObject();
            state.put("opened", true);
            node.put("state", state);
        }

        nodes.put(node);

        // Add child config nodes
        for (TreeNodeConfig child : children) {
            addConfigNode(nodes, child, nodeId, byParent);
        }
    }

    /**
     * Load records from a data source.
     */
    private JSONObject loadRecords(TreeNodeConfig config, String parentRecordId,
                                   int page, int pageSize) throws Exception {

        JSONObject result = new JSONObject();
        JSONArray nodes = new JSONArray();

        String dataTable = config.getDataTable();
        if (dataTable == null || dataTable.isEmpty()) {
            result.put("nodes", nodes);
            result.put("hasMore", false);
            return result;
        }

        // Build query
        String condition = buildCondition(config, parentRecordId);
        Object[] params = buildParams(config, parentRecordId);
        String sortField = config.getEffectiveSortField();

        // Calculate pagination
        int start = (page - 1) * pageSize;

        // Load one extra to check if there are more
        FormRowSet rows = FormDataDaoHelper.loadFormDataWithCondition(
                dataTable, appDef, condition, params, sortField, false, start, pageSize + 1);

        boolean hasMore = false;
        if (rows != null && rows.size() > pageSize) {
            hasMore = true;
            rows.remove(rows.size() - 1);
        }

        // Convert to tree nodes
        if (rows != null) {
            for (Object obj : rows) {
                if (obj instanceof FormRow) {
                    FormRow row = (FormRow) obj;
                    JSONObject node = createRecordNode(config, row);
                    nodes.put(node);
                }
            }
        }

        result.put("nodes", nodes);
        result.put("hasMore", hasMore);
        result.put("page", page);
        result.put("pageSize", pageSize);

        return result;
    }

    /**
     * Build HQL condition for record query.
     */
    private String buildCondition(TreeNodeConfig config, String parentRecordId) {
        StringBuilder condition = new StringBuilder();

        // Parent condition for hierarchical data
        if (config.isHierarchical()) {
            String parentField = config.getParentField();
            if (parentRecordId == null || parentRecordId.isEmpty()) {
                // Root records
                condition.append("WHERE (e.customProperties.").append(parentField)
                        .append(" IS NULL OR e.customProperties.").append(parentField).append(" = '')");
            } else {
                // Child records
                condition.append("WHERE e.customProperties.").append(parentField).append(" = ?");
            }
        }

        // Additional filter
        String filter = config.getFilter();
        if (filter != null && !filter.trim().isEmpty()) {
            if (condition.length() > 0) {
                condition.append(" AND (").append(filter).append(")");
            } else {
                condition.append("WHERE ").append(filter);
            }
        }

        return condition.length() > 0 ? condition.toString() : null;
    }

    /**
     * Build query parameters.
     */
    private Object[] buildParams(TreeNodeConfig config, String parentRecordId) {
        if (config.isHierarchical() && parentRecordId != null && !parentRecordId.isEmpty()) {
            return new Object[]{parentRecordId};
        }
        return null;
    }

    /**
     * Create a tree node JSON for a record.
     */
    private JSONObject createRecordNode(TreeNodeConfig config, FormRow row) throws Exception {
        String recordId = row.getId();
        String label = applyLabelTemplate(config, row);

        // Check if has children (for hierarchical)
        boolean hasChildren = false;
        if (config.isHierarchical()) {
            hasChildren = FormDataDaoHelper.hasChildren(
                    config.getDataTable(), config.getParentField(), recordId);
        }

        // Build node ID
        String nodeId = "record_" + config.getId() + "_" + recordId;

        JSONObject node = new JSONObject();
        node.put("id", nodeId);
        node.put("text", label);
        node.put("icon", config.getEffectiveIcon());
        node.put("type", "record");
        node.put("children", hasChildren);

        // Node data
        JSONObject data = new JSONObject();
        data.put("configId", config.getId());
        data.put("recordId", recordId);
        data.put("formId", config.getFormId());
        data.put("dataTable", config.getDataTable());
        data.put("isHierarchical", config.isHierarchical());
        node.put("data", data);

        return node;
    }

    /**
     * Apply label template to a record.
     */
    private String applyLabelTemplate(TreeNodeConfig config, FormRow row) {
        String template = config.getLabelTemplate();
        String labelField = config.getLabelField();

        if (template != null && !template.trim().isEmpty()) {
            Matcher matcher = TEMPLATE_PATTERN.matcher(template);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String field = matcher.group(1);
                String value = FormDataDaoHelper.getStringProperty(row, field);
                if (value == null) value = "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(sb);

            return StringUtil.stripHtmlRelaxed(sb.toString());
        }

        if (labelField != null && !labelField.isEmpty()) {
            String value = FormDataDaoHelper.getStringProperty(row, labelField);
            if (value != null) {
                return StringUtil.stripHtmlRelaxed(value);
            }
        }

        return row.getId() != null ? row.getId() : "Untitled";
    }
}
