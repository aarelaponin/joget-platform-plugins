package org.joget;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataListBinder;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.apps.userview.service.UserviewUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.service.DemoDataService;
import org.joget.service.TreeMenuApi;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tree Menu Plugin - Renders a hierarchical tree menu in the Joget userview sidebar.
 *
 * Supports two modes:
 * - "metamodel": Data-driven approach using configuration tables (tm_portfolio, tm_object_type)
 * - "legacy": Original DataListBinder-based approach for backward compatibility
 */
public class TreeMenu extends UserviewMenu implements PluginWebSupport {

    private final static String MESSAGE_PATH = "messages/TreeMenu";

    // Mode constants
    public static final String MODE_METAMODEL = "metamodel";
    public static final String MODE_LEGACY = "legacy";
    public static final String MODE_DEMO = "demo";

    // Default table name
    public static final String DEFAULT_CONFIG_TABLE = "tm_tree_config";

    // Default settings
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final String DEFAULT_TREE_PANEL_WIDTH = "300";

    // Legacy mode fields
    protected UserviewMenu innerMenu = null;
    protected DataListBinder binder = null;
    protected Map<String, Collection> data = null;

    // Services
    private DemoDataService demoService;

    // Thread-local to prevent recursive rendering
    private static final ThreadLocal<Set<String>> RENDERING_MENUS = new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return new HashSet<>();
        }
    };

    @Override
    public String getCategory() {
        return "Marketplace";
    }

    @Override
    public String getIcon() {
        return "/plugin/org.joget.apps.userview.lib.HtmlPage/images/grid_icon.gif";
    }

    @Override
    public String getRenderPage() {
        String mode = getPropertyString("mode");

        if (MODE_METAMODEL.equals(mode) || MODE_DEMO.equals(mode)) {
            return getMetamodelRenderPage();
        } else {
            return getLegacyRenderPage();
        }
    }

    /**
     * Render page for metamodel mode.
     * The actual content is loaded via AJAX when a record is selected.
     */
    protected String getMetamodelRenderPage() {
        // Check if a record is selected
        String recordId = getRequestParameterString("recordId");
        String formId = getRequestParameterString("formId");

        if (recordId != null && !recordId.isEmpty() && formId != null && !formId.isEmpty()) {
            // Load and render the form for the selected record
            // This will be handled by the inner content panel
            return "";  // Return empty - form is loaded via AJAX in the content panel
        }

        // Return default content HTML if configured
        String defaultContent = getPropertyString("defaultContentHtml");
        if (defaultContent != null && !defaultContent.isEmpty()) {
            return defaultContent;
        }

        return "";
    }

    /**
     * Render page for legacy mode (backward compatible).
     */
    protected String getLegacyRenderPage() {
        // Check if a specific node is selected via URL parameters
        boolean nodeSelected = false;
        String selectedNodeCode = null;
        Object[] treeNodeParams = (Object[]) getProperty("treeNodeParams");

        if (treeNodeParams != null) {
            for (Object o : treeNodeParams) {
                Map mapping = (HashMap) o;
                String param = mapping.get("paramName").toString();
                String value = getRequestParameterString(param);
                if (value != null && !value.isEmpty()) {
                    nodeSelected = true;
                    selectedNodeCode = value;
                    break;
                }
            }
        }

        // If a node is selected and has an inner menu, render it
        if (nodeSelected && getInnerMenu() != null) {
            String result = UserviewUtil.getUserviewMenuHtml(getInnerMenu());
            setProperty(UserviewMenu.ALERT_MESSAGE_PROPERTY, getInnerMenu().getPropertyString(UserviewMenu.ALERT_MESSAGE_PROPERTY));
            setProperty(UserviewMenu.REDIRECT_URL_PROPERTY, getInnerMenu().getPropertyString(UserviewMenu.REDIRECT_URL_PROPERTY));
            setProperty(UserviewMenu.REDIRECT_PARENT_PROPERTY, getInnerMenu().getPropertyString(UserviewMenu.REDIRECT_PARENT_PROPERTY));
            return result;
        }

        return "";
    }

    @Override
    public boolean isHomePageSupported() {
        String mode = getPropertyString("mode");
        if (MODE_METAMODEL.equals(mode) || MODE_DEMO.equals(mode)) {
            return true;
        }
        return getInnerMenu() != null && getInnerMenu().isHomePageSupported();
    }

    @Override
    public String getDecoratedMenu() {
        // Check if we're in the UI Builder design mode
        // In design mode, return null to use default menu rendering (makes it selectable)
        if (isInBuilderDesignMode()) {
            return null;
        }

        String mode = getPropertyString("mode");

        // Check if this is an AJAX request for tree data
        String ajaxLoad = getRequestParameterString("_ajaxTreeLoad");
        if ("true".equals(ajaxLoad)) {
            // Set response headers for JSON
            try {
                Object response = org.joget.workflow.util.WorkflowUtil.getHttpServletResponse();
                if (response != null) {
                    java.lang.reflect.Method setContentTypeMethod = response.getClass().getMethod("setContentType", String.class);
                    setContentTypeMethod.invoke(response, "application/json;charset=UTF-8");
                }
            } catch (Exception e) {
                // Continue anyway
            }

            if (MODE_DEMO.equals(mode)) {
                return getDemoTreeDataAsJson();
            } else if (MODE_METAMODEL.equals(mode)) {
                return getMetamodelTreeDataAsJson();
            } else {
                return getLegacyTreeDataAsJson();
            }
        }

        // Prevent recursive rendering
        String menuId = getPropertyString("id");
        if (menuId == null) {
            menuId = "default";
        }

        Set<String> renderingMenus = RENDERING_MENUS.get();
        if (renderingMenus.contains(menuId)) {
            return "";
        }

        try {
            renderingMenus.add(menuId);

            Map<String, Object> model = new HashMap<>();
            model.put("element", this);

            String label = getPropertyString("label");
            if (label != null) {
                label = StringUtil.stripHtmlRelaxed(label);
            }
            model.put("label", label);

            String treeId = "treemenu_" + menuId;
            model.put("treeId", treeId);
            model.put("mode", mode != null ? mode : MODE_LEGACY);

            if (MODE_DEMO.equals(mode)) {
                // Demo mode configuration
                model.put("mode", MODE_METAMODEL);  // Use metamodel template
                model.put("ajaxUrl", getUrl());
                model.put("treePanelWidth", "300");
                model.put("showSearch", true);
                model.put("showBreadcrumb", true);
                model.put("pageSize", 50);

                // Build demo tree data
                String demoTreeData = getDemoService().buildDemoTreeJson();
                model.put("initialTreeData", demoTreeData);

            } else if (MODE_METAMODEL.equals(mode)) {
                // Metamodel mode configuration
                model.put("ajaxUrl", getWebServiceUrl());
                model.put("treePanelWidth", getPropertyString("treePanelWidth"));
                model.put("showSearch", "true".equals(getPropertyString("showSearch")));
                model.put("showBreadcrumb", "true".equals(getPropertyString("showBreadcrumb")));
                model.put("pageSize", getPageSize());

                // Build initial tree data (portfolios + object types only)
                String initialTreeData = buildInitialTreeData();
                model.put("initialTreeData", initialTreeData);

            } else {
                // Legacy mode configuration
                String rootParentCode = getPropertyString("rootParentId");
                if (rootParentCode == null || rootParentCode.trim().isEmpty()) {
                    rootParentCode = "";
                }

                refreshTreeData();
                Collection<Object> rootNodes = getData().get(rootParentCode);
                List<Map<String, Object>> treeData = prepareTreeData(rootNodes);

                model.put("treeData", treeData);
                model.put("ajaxUrl", getUrl());
                model.put("treeNodeParams", getProperty("treeNodeParams"));
            }

            try {
                // Use reflection to avoid javax vs jakarta servlet incompatibility
                java.lang.reflect.Method getRequestMethod = org.joget.workflow.util.WorkflowUtil.class.getMethod("getHttpServletRequest");
                Object request = getRequestMethod.invoke(null);
                if (request != null) {
                    model.put("request", request);
                }
            } catch (Exception e) {
                LogUtil.debug(getClassName(), "Unable to get request object: " + e.getMessage());
            }

            PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
            return pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/treeMenu.ftl", MESSAGE_PATH);

        } finally {
            renderingMenus.remove(menuId);
            if (renderingMenus.isEmpty()) {
                RENDERING_MENUS.remove();
            }
        }
    }

    /**
     * Get the web service URL for AJAX calls.
     */
    protected String getWebServiceUrl() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String contextPath = AppUtil.getRequestContextPath();
        return contextPath + "/web/json/app/" + appDef.getAppId() + "/" +
               appDef.getVersion() + "/plugin/org.joget.TreeMenu/service";
    }

    /**
     * Build initial tree data for metamodel mode.
     */
    protected String buildInitialTreeData() {
        try {
            String configTable = getConfigTableName();
            TreeMenuApi api = new TreeMenuApi(configTable);
            return api.getTreeConfig().toString();
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error building initial tree data");
            return "[]";
        }
    }

    /**
     * Get tree data as JSON for metamodel mode AJAX requests.
     */
    protected String getMetamodelTreeDataAsJson() {
        try {
            String action = getRequestParameterString("action");

            if ("children".equals(action)) {
                return loadChildrenJson();
            }

            // Default: return empty array
            return "[]";

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error generating metamodel tree data JSON");
            return "[]";
        }
    }

    /**
     * Load children for lazy loading.
     */
    protected String loadChildrenJson() {
        try {
            String configId = getRequestParameterString("configId");
            String parentRecordId = getRequestParameterString("parentRecordId");
            int page = 1;
            int pageSize = getPageSize();

            try {
                String pageStr = getRequestParameterString("page");
                if (pageStr != null && !pageStr.isEmpty()) {
                    page = Integer.parseInt(pageStr);
                }
            } catch (NumberFormatException e) {
                // Use default
            }

            String configTable = getConfigTableName();
            TreeMenuApi api = new TreeMenuApi(configTable);

            JSONObject result = api.getChildren(configId, parentRecordId, page, pageSize);
            return result.toString();

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error loading children");
            return "{\"nodes\":[],\"hasMore\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // ========== PluginWebSupport Implementation ==========

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        try {
            String action = request.getParameter("action");
            String configTable = request.getParameter("configTable");
            if (configTable == null || configTable.isEmpty()) {
                configTable = DEFAULT_CONFIG_TABLE;
            }

            TreeMenuApi api = new TreeMenuApi(configTable);

            if ("children".equals(action)) {
                // Load children for lazy loading
                String configId = request.getParameter("configId");
                String parentRecordId = request.getParameter("parentRecordId");
                int page = 1;
                int pageSize = DEFAULT_PAGE_SIZE;

                try {
                    String pageStr = request.getParameter("page");
                    if (pageStr != null && !pageStr.isEmpty()) {
                        page = Integer.parseInt(pageStr);
                    }
                    String pageSizeStr = request.getParameter("pageSize");
                    if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                        pageSize = Integer.parseInt(pageSizeStr);
                    }
                } catch (NumberFormatException e) {
                    // Use defaults
                }

                JSONObject result = api.getChildren(configId, parentRecordId, page, pageSize);
                writer.write(result.toString());

            } else if ("config".equals(action)) {
                // Return initial tree configuration
                JSONArray config = api.getTreeConfig();
                writer.write(config.toString());

            } else {
                // Unknown action
                JSONObject error = new JSONObject();
                error.put("error", "Unknown action: " + action);
                writer.write(error.toString());
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error in web service");
            try {
                JSONObject error = new JSONObject();
                error.put("error", e.getMessage());
                writer.write(error.toString());
            } catch (Exception ex) {
                writer.write("{\"error\":\"Internal server error\"}");
            }
        }
    }

    // ========== Helper Methods ==========

    /**
     * Check if we're in the UI Builder design mode.
     * In design mode, we should return null from getDecoratedMenu() to allow selection.
     */
    protected boolean isInBuilderDesignMode() {
        try {
            // Use reflection to call WorkflowUtil.getHttpServletRequest() to avoid javax vs jakarta servlet incompatibility
            java.lang.reflect.Method getRequestMethod = org.joget.workflow.util.WorkflowUtil.class.getMethod("getHttpServletRequest");
            Object request = getRequestMethod.invoke(null);

            if (request != null) {
                // Get the ORIGINAL request URI (before JSP forward) using servlet forward attribute
                java.lang.reflect.Method getAttribute = request.getClass().getMethod("getAttribute", String.class);

                // Try jakarta.servlet first, then javax.servlet
                String originalURI = (String) getAttribute.invoke(request, "jakarta.servlet.forward.request_uri");
                if (originalURI == null) {
                    originalURI = (String) getAttribute.invoke(request, "javax.servlet.forward.request_uri");
                }

                // Fallback to regular URI if no forward attribute
                if (originalURI == null) {
                    java.lang.reflect.Method getRequestURI = request.getClass().getMethod("getRequestURI");
                    originalURI = (String) getRequestURI.invoke(request);
                }

                LogUtil.info(getClassName(), "TreeMenu isInBuilderDesignMode - originalURI: " + originalURI);

                // Check if we're in the userview builder by URL pattern
                if (originalURI != null) {
                    // Runtime URLs contain /userview/ - definitely not in builder
                    if (originalURI.contains("/userview/")) {
                        LogUtil.info(getClassName(), "TreeMenu isInBuilderDesignMode - detected RUNTIME mode (userview URL)");
                        return false;
                    }
                    // If it's a console/builder URL, we're in builder mode
                    if (originalURI.contains("/console/") || originalURI.contains("/ubuilder")) {
                        LogUtil.info(getClassName(), "TreeMenu isInBuilderDesignMode - detected BUILDER mode");
                        return true;
                    }
                }

                // Use reflection to call getParameter()
                java.lang.reflect.Method getParameter = request.getClass().getMethod("getParameter", String.class);

                // Check for builder-specific request attributes or parameters
                String isBuilder = (String) getParameter.invoke(request, "_builder");
                if ("true".equals(isBuilder)) {
                    LogUtil.info(getClassName(), "TreeMenu isInBuilderDesignMode - detected BUILDER mode via parameter");
                    return true;
                }
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error checking builder mode");
        }
        LogUtil.info(getClassName(), "TreeMenu isInBuilderDesignMode - detected RUNTIME mode (default)");
        return false;
    }

    protected String getConfigTableName() {
        String tableName = getPropertyString("configTableName");
        return (tableName != null && !tableName.isEmpty()) ? tableName : DEFAULT_CONFIG_TABLE;
    }

    protected int getPageSize() {
        try {
            String pageSizeStr = getPropertyString("pageSize");
            if (pageSizeStr != null && !pageSizeStr.isEmpty()) {
                return Integer.parseInt(pageSizeStr);
            }
        } catch (NumberFormatException e) {
            // Use default
        }
        return DEFAULT_PAGE_SIZE;
    }

    protected DemoDataService getDemoService() {
        if (demoService == null) {
            demoService = new DemoDataService();
        }
        return demoService;
    }

    /**
     * Get tree data as JSON for demo mode AJAX requests.
     */
    protected String getDemoTreeDataAsJson() {
        try {
            String action = getRequestParameterString("action");

            if ("children".equals(action)) {
                String configId = getRequestParameterString("configId");
                String parentRecordId = getRequestParameterString("parentRecordId");
                int page = 1;

                try {
                    String pageStr = getRequestParameterString("page");
                    if (pageStr != null && !pageStr.isEmpty()) {
                        page = Integer.parseInt(pageStr);
                    }
                } catch (NumberFormatException e) {
                    // Use default
                }

                JSONObject result = getDemoService().getDemoChildren(configId, parentRecordId, page, 50);
                return result.toString();
            }

            return "[]";

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error generating demo tree data JSON");
            return "[]";
        }
    }

    // ========== Legacy Mode Methods ==========

    /**
     * Prepare tree data structure for template rendering (legacy mode).
     */
    protected List<Map<String, Object>> prepareTreeData(Collection<Object> nodes) {
        List<Map<String, Object>> treeNodes = new ArrayList<>();

        if (nodes != null && !nodes.isEmpty()) {
            for (Object row : nodes) {
                Map<String, Object> node = new HashMap<>();

                String id = (String) DataListService.evaluateColumnValueFromRow(row, "id");
                String code = (String) DataListService.evaluateColumnValueFromRow(row, "code");
                String label = (String) DataListService.evaluateColumnValueFromRow(row, getPropertyString("treeNodeLabel"));

                node.put("id", id);
                node.put("code", code);
                node.put("label", label != null ? StringUtil.stripHtmlRelaxed(label) : "");

                boolean hasChildren = hasChildren(code);
                node.put("hasChildren", hasChildren);
                node.put("isFolder", hasChildren);

                String url = buildNodeUrl(row);
                node.put("url", url);

                treeNodes.add(node);
            }
        }

        return treeNodes;
    }

    /**
     * Check if a node has children (legacy mode).
     */
    protected boolean hasChildren(String nodeCode) {
        if (nodeCode == null) return false;

        Collection<Object> children = getData().get(nodeCode);
        return children != null && !children.isEmpty();
    }

    /**
     * Build URL for a tree node (legacy mode).
     */
    protected String buildNodeUrl(Object row) {
        String code = (String) DataListService.evaluateColumnValueFromRow(row, "code");

        if (hasChildren(code)) {
            return "#";
        }

        String url = getUrl();

        Object[] treeNodeParams = (Object[]) getProperty("treeNodeParams");
        if (treeNodeParams != null) {
            for (Object o : treeNodeParams) {
                Map mapping = (HashMap) o;
                String param = mapping.get("paramName").toString();
                String colId = mapping.get("column").toString();
                String value = mapping.get("defaultValue") != null ? mapping.get("defaultValue").toString() : "";

                try {
                    String nodeValue = (String) DataListService.evaluateColumnValueFromRow(row, colId);
                    if (nodeValue != null) {
                        value = nodeValue;
                    }
                } catch (Exception ex) {
                    // Use default value
                }

                url = StringUtil.addParamsToUrl(url, param, value);
            }
        }

        return url;
    }

    /**
     * Get tree data as JSON for AJAX requests (legacy mode).
     */
    protected String getLegacyTreeDataAsJson() {
        try {
            String parentCode = getRequestParameterString("parentCode");
            if (parentCode == null) {
                parentCode = "";
            }

            refreshTreeData();
            Collection<Object> nodes = getData().get(parentCode);
            List<Map<String, Object>> treeData = prepareTreeData(nodes);

            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> node : treeData) {
                JSONObject jsonNode = new JSONObject();
                jsonNode.put("id", node.get("id"));
                jsonNode.put("text", node.get("label"));
                jsonNode.put("children", node.get("hasChildren"));
                jsonNode.put("type", (Boolean) node.get("hasChildren") ? "folder" : "file");

                JSONObject dataObj = new JSONObject();
                dataObj.put("code", node.get("code"));
                dataObj.put("url", node.get("url"));
                jsonNode.put("data", dataObj);

                if ((Boolean) node.get("hasChildren")) {
                    JSONObject stateObj = new JSONObject();
                    stateObj.put("opened", false);
                    jsonNode.put("state", stateObj);
                }

                jsonArray.put(jsonNode);
            }

            return jsonArray.toString();

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error generating JSON tree data");
            return "[]";
        }
    }

    /**
     * Refresh tree data from data source (legacy mode).
     */
    protected void refreshTreeData() {
        data = null;
        getData();
    }

    /**
     * Get all tree data organized by parent code (legacy mode).
     */
    protected Map<String, Collection> getData() {
        if (data == null) {
            data = new HashMap<String, Collection>();

            try {
                DataListBinder binder = getBinder();
                if (binder == null) {
                    LogUtil.error(getClassName(), null, "Binder is null");
                    return data;
                }

                String orderBy = getPropertyString("orderBy");
                Boolean desc = "true".equals(getPropertyString("order"));

                DataListCollection allRows = binder.getData(null, binder.getProperties(), new org.joget.apps.datalist.model.DataListFilterQueryObject[0], orderBy, desc, null, null);

                if (allRows != null) {
                    LogUtil.debug(getClassName(), "Total rows loaded: " + allRows.size());

                    String parentField = getPropertyString("treeNodeParentId");

                    for (Object row : allRows) {
                        String parentCode = (String) DataListService.evaluateColumnValueFromRow(row, parentField);

                        if (parentCode == null || parentCode.trim().isEmpty()) {
                            parentCode = "";
                        }

                        Collection<Object> children = data.get(parentCode);
                        if (children == null) {
                            children = new ArrayList<Object>();
                            data.put(parentCode, children);
                        }
                        children.add(row);
                    }

                    if (LogUtil.isDebugEnabled(getClassName())) {
                        LogUtil.debug(getClassName(), "Data organized into " + data.size() + " parent groups");
                    }
                }
            } catch (Exception ex) {
                LogUtil.error(getClassName(), ex, "Error getting tree data");
            }
        }
        return data;
    }

    // ========== Plugin Metadata ==========

    public String getName() {
        return "Tree Menu";
    }

    public String getVersion() {
        return "8.1.12";
    }

    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.TreeMenu.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.TreeMenu.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/treeMenu.json", null, true, MESSAGE_PATH);
    }

    // ========== Inner Menu (Legacy) ==========

    protected UserviewMenu getInnerMenu() {
        if (innerMenu == null) {
            Object menuData = getProperty("innerMenu");
            if (menuData != null && menuData instanceof Map) {
                Map menuMap = (Map) menuData;
                if (menuMap.containsKey("className") && !menuMap.get("className").toString().isEmpty()) {
                    PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
                    innerMenu = (UserviewMenu) pluginManager.getPlugin(menuMap.get("className").toString());

                    if (innerMenu != null) {
                        Map menuProps = (Map) menuMap.get("properties");
                        innerMenu.setProperties(menuProps);
                        innerMenu.setRequestParameters(getRequestParameters());
                        innerMenu.setUserview(getUserview());
                        innerMenu.setProperty("menuId", getPropertyString("menuId"));

                        String url = getUrl();
                        Object[] treeNodeParams = (Object[]) getProperty("treeNodeParams");
                        if (treeNodeParams != null) {
                            for (Object o : treeNodeParams) {
                                Map mapping = (HashMap) o;
                                String param = mapping.get("paramName").toString();
                                url = StringUtil.addParamsToUrl(url, param, getRequestParameterString(param));
                            }
                        }
                        innerMenu.setUrl(url);
                    }
                }
            }
        }
        return innerMenu;
    }

    protected DataListBinder getBinder() {
        if (binder == null) {
            Object binderData = getProperty("binder");
            if (binderData != null && binderData instanceof Map) {
                Map bdMap = (Map) binderData;
                if (bdMap != null && bdMap.containsKey("className") && !bdMap.get("className").toString().isEmpty()) {
                    PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
                    binder = (DataListBinder) pluginManager.getPlugin(bdMap.get("className").toString());

                    if (binder != null) {
                        Map bdProps = new HashMap();
                        if (bdMap.get("properties") != null) {
                            bdProps = (Map) bdMap.get("properties");
                        }
                        binder.setProperties(bdProps);
                    }
                }
            }
        }
        return binder;
    }
}
