package org.joget.service;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provides demo/test data for the Tree Menu plugin.
 * Demonstrates two use cases:
 * 1. Simple hierarchical list (Chart of Accounts)
 * 2. Categorized data sources (Enterprise Architecture)
 */
public class DemoDataService {

    /**
     * Build demo tree configuration.
     * Shows both use cases in one demo.
     */
    public String buildDemoTreeJson() {
        try {
            JSONArray nodes = new JSONArray();

            // === USE CASE 1: Simple hierarchical data (Chart of Accounts) ===
            nodes.put(createConfigNode("config_coa", "#", "Chart of Accounts", "fas fa-list-ol", "dataSource", true, true));

            // === USE CASE 2: Categorized data sources (Enterprise Architecture) ===
            // Root folder
            nodes.put(createConfigNode("config_ea", "#", "Enterprise Architecture", "fas fa-sitemap", "folder", true, true));

            // Sub-folders
            nodes.put(createConfigNode("config_ba", "config_ea", "Business Architecture", "fas fa-building", "folder", true, false));
            nodes.put(createConfigNode("config_aa", "config_ea", "Application Architecture", "fas fa-laptop-code", "folder", true, false));
            nodes.put(createConfigNode("config_ta", "config_ea", "Technology Architecture", "fas fa-server", "folder", true, false));

            // Data sources under Business Architecture
            nodes.put(createConfigNode("config_cap", "config_ba", "Business Capabilities", "fas fa-puzzle-piece", "dataSource", true, false));
            nodes.put(createConfigNode("config_proc", "config_ba", "Business Processes", "fas fa-project-diagram", "dataSource", true, false));

            // Data sources under Application Architecture
            nodes.put(createConfigNode("config_app", "config_aa", "Applications", "fas fa-window-maximize", "dataSource", true, false));

            // Data sources under Technology Architecture
            nodes.put(createConfigNode("config_srv", "config_ta", "Servers", "fas fa-server", "dataSource", true, false));

            return nodes.toString();

        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Get demo children for a given config node.
     */
    public JSONObject getDemoChildren(String configId, String parentRecordId, int page, int pageSize) {
        JSONObject result = new JSONObject();

        try {
            JSONArray nodes = new JSONArray();

            // Clean up configId
            String cleanId = configId.replace("config_", "");

            switch (cleanId) {
                case "coa":
                    // Chart of Accounts - hierarchical
                    nodes = getChartOfAccountsNodes(parentRecordId);
                    break;

                case "cap":
                    // Business Capabilities - hierarchical
                    nodes = getCapabilitiesNodes(parentRecordId);
                    break;

                case "proc":
                    // Business Processes - flat
                    nodes = getProcessNodes();
                    break;

                case "app":
                    // Applications - flat
                    nodes = getApplicationNodes();
                    break;

                case "srv":
                    // Servers - flat
                    nodes = getServerNodes();
                    break;
            }

            result.put("nodes", nodes);
            result.put("hasMore", false);
            result.put("page", page);

        } catch (Exception e) {
            try {
                result.put("nodes", new JSONArray());
                result.put("hasMore", false);
                result.put("error", e.getMessage());
            } catch (Exception ex) {
                // ignore
            }
        }

        return result;
    }

    // ==================== Demo Data ====================

    private JSONArray getChartOfAccountsNodes(String parentId) throws Exception {
        JSONArray nodes = new JSONArray();

        if (parentId == null || parentId.isEmpty()) {
            // Root accounts
            nodes.put(createRecordNode("coa", "1000", "1000 - Assets", true));
            nodes.put(createRecordNode("coa", "2000", "2000 - Liabilities", true));
            nodes.put(createRecordNode("coa", "3000", "3000 - Equity", true));
            nodes.put(createRecordNode("coa", "4000", "4000 - Revenue", true));
            nodes.put(createRecordNode("coa", "5000", "5000 - Expenses", true));
        } else {
            switch (parentId) {
                case "1000":
                    nodes.put(createRecordNode("coa", "1100", "1100 - Current Assets", true));
                    nodes.put(createRecordNode("coa", "1200", "1200 - Fixed Assets", true));
                    break;
                case "1100":
                    nodes.put(createRecordNode("coa", "1110", "1110 - Cash", false));
                    nodes.put(createRecordNode("coa", "1120", "1120 - Accounts Receivable", false));
                    nodes.put(createRecordNode("coa", "1130", "1130 - Inventory", false));
                    break;
                case "1200":
                    nodes.put(createRecordNode("coa", "1210", "1210 - Equipment", false));
                    nodes.put(createRecordNode("coa", "1220", "1220 - Buildings", false));
                    nodes.put(createRecordNode("coa", "1230", "1230 - Land", false));
                    break;
                case "2000":
                    nodes.put(createRecordNode("coa", "2100", "2100 - Current Liabilities", true));
                    nodes.put(createRecordNode("coa", "2200", "2200 - Long-term Liabilities", false));
                    break;
                case "2100":
                    nodes.put(createRecordNode("coa", "2110", "2110 - Accounts Payable", false));
                    nodes.put(createRecordNode("coa", "2120", "2120 - Accrued Expenses", false));
                    break;
                case "3000":
                    nodes.put(createRecordNode("coa", "3100", "3100 - Common Stock", false));
                    nodes.put(createRecordNode("coa", "3200", "3200 - Retained Earnings", false));
                    break;
                case "4000":
                    nodes.put(createRecordNode("coa", "4100", "4100 - Sales Revenue", false));
                    nodes.put(createRecordNode("coa", "4200", "4200 - Service Revenue", false));
                    break;
                case "5000":
                    nodes.put(createRecordNode("coa", "5100", "5100 - Cost of Goods Sold", false));
                    nodes.put(createRecordNode("coa", "5200", "5200 - Operating Expenses", true));
                    break;
                case "5200":
                    nodes.put(createRecordNode("coa", "5210", "5210 - Salaries", false));
                    nodes.put(createRecordNode("coa", "5220", "5220 - Rent", false));
                    nodes.put(createRecordNode("coa", "5230", "5230 - Utilities", false));
                    break;
            }
        }

        return nodes;
    }

    private JSONArray getCapabilitiesNodes(String parentId) throws Exception {
        JSONArray nodes = new JSONArray();

        if (parentId == null || parentId.isEmpty()) {
            nodes.put(createRecordNode("cap", "cap_customer", "Customer Management", true));
            nodes.put(createRecordNode("cap", "cap_product", "Product Management", true));
            nodes.put(createRecordNode("cap", "cap_finance", "Financial Management", true));
            nodes.put(createRecordNode("cap", "cap_hr", "Human Resources", true));
        } else {
            switch (parentId) {
                case "cap_customer":
                    nodes.put(createRecordNode("cap", "cap_crm", "CRM", false));
                    nodes.put(createRecordNode("cap", "cap_support", "Customer Support", false));
                    nodes.put(createRecordNode("cap", "cap_onboard", "Onboarding", false));
                    break;
                case "cap_product":
                    nodes.put(createRecordNode("cap", "cap_design", "Product Design", false));
                    nodes.put(createRecordNode("cap", "cap_dev", "Product Development", false));
                    break;
                case "cap_finance":
                    nodes.put(createRecordNode("cap", "cap_ar", "Accounts Receivable", false));
                    nodes.put(createRecordNode("cap", "cap_ap", "Accounts Payable", false));
                    nodes.put(createRecordNode("cap", "cap_gl", "General Ledger", false));
                    break;
                case "cap_hr":
                    nodes.put(createRecordNode("cap", "cap_recruit", "Recruitment", false));
                    nodes.put(createRecordNode("cap", "cap_payroll", "Payroll", false));
                    break;
            }
        }

        return nodes;
    }

    private JSONArray getProcessNodes() throws Exception {
        JSONArray nodes = new JSONArray();
        nodes.put(createRecordNode("proc", "proc_o2c", "Order to Cash", false));
        nodes.put(createRecordNode("proc", "proc_p2p", "Procure to Pay", false));
        nodes.put(createRecordNode("proc", "proc_h2r", "Hire to Retire", false));
        nodes.put(createRecordNode("proc", "proc_r2r", "Record to Report", false));
        return nodes;
    }

    private JSONArray getApplicationNodes() throws Exception {
        JSONArray nodes = new JSONArray();
        nodes.put(createRecordNode("app", "app_sf", "APP001 - Salesforce", false));
        nodes.put(createRecordNode("app", "app_sap", "APP002 - SAP ERP", false));
        nodes.put(createRecordNode("app", "app_wd", "APP003 - Workday", false));
        nodes.put(createRecordNode("app", "app_sn", "APP004 - ServiceNow", false));
        nodes.put(createRecordNode("app", "app_jira", "APP005 - Jira", false));
        nodes.put(createRecordNode("app", "app_conf", "APP006 - Confluence", false));
        return nodes;
    }

    private JSONArray getServerNodes() throws Exception {
        JSONArray nodes = new JSONArray();
        nodes.put(createRecordNode("srv", "srv_web1", "web-prod-01 (10.0.1.10)", false));
        nodes.put(createRecordNode("srv", "srv_web2", "web-prod-02 (10.0.1.11)", false));
        nodes.put(createRecordNode("srv", "srv_app1", "app-prod-01 (10.0.2.10)", false));
        nodes.put(createRecordNode("srv", "srv_db1", "db-prod-01 (10.0.3.10)", false));
        nodes.put(createRecordNode("srv", "srv_db2", "db-prod-02 (10.0.3.11)", false));
        return nodes;
    }

    // ==================== Helper Methods ====================

    private JSONObject createConfigNode(String id, String parent, String text, String icon,
                                        String nodeType, boolean hasChildren, boolean opened) throws Exception {
        JSONObject node = new JSONObject();
        node.put("id", id);
        node.put("parent", parent);
        node.put("text", text);
        node.put("icon", icon);
        node.put("type", nodeType);
        node.put("children", hasChildren);

        JSONObject data = new JSONObject();
        data.put("configId", id.replace("config_", ""));
        data.put("nodeType", nodeType);
        data.put("isHierarchical", "coa".equals(id.replace("config_", "")) || "cap".equals(id.replace("config_", "")));
        node.put("data", data);

        if (opened) {
            JSONObject state = new JSONObject();
            state.put("opened", true);
            node.put("state", state);
        }

        return node;
    }

    private JSONObject createRecordNode(String configId, String recordId, String text, boolean hasChildren) throws Exception {
        JSONObject node = new JSONObject();
        node.put("id", "record_" + configId + "_" + recordId);
        node.put("text", text);
        node.put("icon", "fas fa-file");
        node.put("type", "record");
        node.put("children", hasChildren);

        JSONObject data = new JSONObject();
        data.put("configId", configId);
        data.put("recordId", recordId);
        data.put("isHierarchical", hasChildren);
        node.put("data", data);

        return node;
    }
}
