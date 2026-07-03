-- Sample Configuration Data for Tree Menu Plugin
-- Uses single config table (tm_tree_config) with generic nodes

-- =====================================================
-- TREE CONFIGURATION TABLE (tm_tree_config)
-- =====================================================

-- Create the configuration table
CREATE TABLE IF NOT EXISTS app_fd_tm_tree_config (
    id VARCHAR(255) PRIMARY KEY,
    c_parent_id VARCHAR(255),
    c_label VARCHAR(255),
    c_icon VARCHAR(100),
    c_sort_order INT,
    c_node_type VARCHAR(20),          -- 'folder' or 'data'
    c_data_table VARCHAR(255),        -- Joget form table name (for data nodes)
    c_form_id VARCHAR(255),           -- Form ID for detail view
    c_label_field VARCHAR(100),       -- Column for node label
    c_label_template VARCHAR(500),    -- Template: "{c_code} - {c_name}"
    c_parent_field VARCHAR(100),      -- Column for parent ID (hierarchical data)
    c_sort_field VARCHAR(100),        -- Column for ordering
    c_filter VARCHAR(1000),           -- HQL filter condition
    dateCreated DATETIME,
    dateModified DATETIME
);


-- =====================================================
-- USE CASE 1: Simple Chart of Accounts (Hierarchical)
-- =====================================================

INSERT INTO app_fd_tm_tree_config (id, c_parent_id, c_label, c_icon, c_sort_order, c_node_type,
    c_data_table, c_form_id, c_label_field, c_label_template, c_parent_field, c_sort_field, c_filter,
    dateCreated, dateModified) VALUES
('coa', NULL, 'Chart of Accounts', 'fas fa-list-ol', 1, 'data',
    'app_fd_fin_account', 'fin_account', 'c_name', '{c_code} - {c_name}', 'c_parent_id', 'c_code', NULL,
    NOW(), NOW());


-- =====================================================
-- USE CASE 2: Enterprise Architecture (Folders + Data)
-- =====================================================

-- Root folder
INSERT INTO app_fd_tm_tree_config (id, c_parent_id, c_label, c_icon, c_sort_order, c_node_type,
    c_data_table, c_form_id, c_label_field, c_label_template, c_parent_field, c_sort_field, c_filter,
    dateCreated, dateModified) VALUES
('ea', NULL, 'Enterprise Architecture', 'fas fa-sitemap', 2, 'folder',
    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NOW(), NOW());

-- Sub-folders
INSERT INTO app_fd_tm_tree_config (id, c_parent_id, c_label, c_icon, c_sort_order, c_node_type,
    c_data_table, c_form_id, c_label_field, c_label_template, c_parent_field, c_sort_field, c_filter,
    dateCreated, dateModified) VALUES
('ba', 'ea', 'Business Architecture', 'fas fa-building', 1, 'folder',
    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NOW(), NOW()),
('aa', 'ea', 'Application Architecture', 'fas fa-laptop-code', 2, 'folder',
    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NOW(), NOW()),
('ta', 'ea', 'Technology Architecture', 'fas fa-server', 3, 'folder',
    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NOW(), NOW());

-- Data sources under Business Architecture
INSERT INTO app_fd_tm_tree_config (id, c_parent_id, c_label, c_icon, c_sort_order, c_node_type,
    c_data_table, c_form_id, c_label_field, c_label_template, c_parent_field, c_sort_field, c_filter,
    dateCreated, dateModified) VALUES
('capability', 'ba', 'Business Capabilities', 'fas fa-puzzle-piece', 1, 'data',
    'app_fd_ea_capability', 'ea_capability', 'c_name', NULL, 'c_parent_id', 'c_sort_order', NULL,
    NOW(), NOW()),
('process', 'ba', 'Business Processes', 'fas fa-project-diagram', 2, 'data',
    'app_fd_ea_process', 'ea_process', 'c_name', '{c_code} - {c_name}', NULL, 'c_code', NULL,
    NOW(), NOW());

-- Data sources under Application Architecture
INSERT INTO app_fd_tm_tree_config (id, c_parent_id, c_label, c_icon, c_sort_order, c_node_type,
    c_data_table, c_form_id, c_label_field, c_label_template, c_parent_field, c_sort_field, c_filter,
    dateCreated, dateModified) VALUES
('application', 'aa', 'Applications', 'fas fa-window-maximize', 1, 'data',
    'app_fd_ea_application', 'ea_application', 'c_name', '{c_code} - {c_name}', NULL, 'c_name', NULL,
    NOW(), NOW()),
('interface', 'aa', 'Interfaces', 'fas fa-exchange-alt', 2, 'data',
    'app_fd_ea_interface', 'ea_interface', 'c_name', NULL, NULL, 'c_name', NULL,
    NOW(), NOW());

-- Data sources under Technology Architecture
INSERT INTO app_fd_tm_tree_config (id, c_parent_id, c_label, c_icon, c_sort_order, c_node_type,
    c_data_table, c_form_id, c_label_field, c_label_template, c_parent_field, c_sort_field, c_filter,
    dateCreated, dateModified) VALUES
('server', 'ta', 'Servers', 'fas fa-server', 1, 'data',
    'app_fd_ea_server', 'ea_server', 'c_name', '{c_hostname} ({c_ip_address})', NULL, 'c_hostname', NULL,
    NOW(), NOW()),
('database', 'ta', 'Databases', 'fas fa-database', 2, 'data',
    'app_fd_ea_database', 'ea_database', 'c_name', NULL, NULL, 'c_name', NULL,
    NOW(), NOW());


-- =====================================================
-- SAMPLE DATA TABLES
-- =====================================================

-- Business Capabilities (hierarchical)
CREATE TABLE IF NOT EXISTS app_fd_ea_capability (
    id VARCHAR(255) PRIMARY KEY,
    c_name VARCHAR(255),
    c_description TEXT,
    c_parent_id VARCHAR(255),
    c_sort_order INT,
    dateCreated DATETIME,
    dateModified DATETIME
);

INSERT INTO app_fd_ea_capability (id, c_name, c_description, c_parent_id, c_sort_order, dateCreated, dateModified) VALUES
('cap_customer', 'Customer Management', 'Managing customer relationships', NULL, 1, NOW(), NOW()),
('cap_customer_crm', 'CRM', 'Customer Relationship Management', 'cap_customer', 1, NOW(), NOW()),
('cap_customer_support', 'Customer Support', 'Support services', 'cap_customer', 2, NOW(), NOW()),
('cap_customer_onboard', 'Customer Onboarding', 'New customer onboarding', 'cap_customer', 3, NOW(), NOW()),
('cap_finance', 'Financial Management', 'Financial operations', NULL, 2, NOW(), NOW()),
('cap_finance_ar', 'Accounts Receivable', 'AR management', 'cap_finance', 1, NOW(), NOW()),
('cap_finance_ap', 'Accounts Payable', 'AP management', 'cap_finance', 2, NOW(), NOW()),
('cap_finance_gl', 'General Ledger', 'GL operations', 'cap_finance', 3, NOW(), NOW()),
('cap_hr', 'Human Resources', 'HR management', NULL, 3, NOW(), NOW()),
('cap_hr_recruit', 'Recruitment', 'Hiring processes', 'cap_hr', 1, NOW(), NOW()),
('cap_hr_payroll', 'Payroll', 'Payroll management', 'cap_hr', 2, NOW(), NOW());

-- Applications (flat list)
CREATE TABLE IF NOT EXISTS app_fd_ea_application (
    id VARCHAR(255) PRIMARY KEY,
    c_code VARCHAR(50),
    c_name VARCHAR(255),
    c_description TEXT,
    c_status VARCHAR(50),
    dateCreated DATETIME,
    dateModified DATETIME
);

INSERT INTO app_fd_ea_application (id, c_code, c_name, c_description, c_status, dateCreated, dateModified) VALUES
('app_salesforce', 'APP001', 'Salesforce', 'CRM platform', 'Active', NOW(), NOW()),
('app_sap', 'APP002', 'SAP ERP', 'Enterprise resource planning', 'Active', NOW(), NOW()),
('app_workday', 'APP003', 'Workday', 'HR management system', 'Active', NOW(), NOW()),
('app_servicenow', 'APP004', 'ServiceNow', 'IT service management', 'Active', NOW(), NOW()),
('app_jira', 'APP005', 'Jira', 'Project tracking', 'Active', NOW(), NOW()),
('app_confluence', 'APP006', 'Confluence', 'Documentation wiki', 'Active', NOW(), NOW()),
('app_slack', 'APP007', 'Slack', 'Team communication', 'Active', NOW(), NOW()),
('app_zoom', 'APP008', 'Zoom', 'Video conferencing', 'Active', NOW(), NOW());

-- Chart of Accounts (hierarchical)
CREATE TABLE IF NOT EXISTS app_fd_fin_account (
    id VARCHAR(255) PRIMARY KEY,
    c_code VARCHAR(50),
    c_name VARCHAR(255),
    c_parent_id VARCHAR(255),
    c_type VARCHAR(50),
    dateCreated DATETIME,
    dateModified DATETIME
);

INSERT INTO app_fd_fin_account (id, c_code, c_name, c_parent_id, c_type, dateCreated, dateModified) VALUES
('acc_1000', '1000', 'Assets', NULL, 'Header', NOW(), NOW()),
('acc_1100', '1100', 'Current Assets', 'acc_1000', 'Header', NOW(), NOW()),
('acc_1110', '1110', 'Cash', 'acc_1100', 'Detail', NOW(), NOW()),
('acc_1120', '1120', 'Accounts Receivable', 'acc_1100', 'Detail', NOW(), NOW()),
('acc_1130', '1130', 'Inventory', 'acc_1100', 'Detail', NOW(), NOW()),
('acc_1200', '1200', 'Fixed Assets', 'acc_1000', 'Header', NOW(), NOW()),
('acc_1210', '1210', 'Equipment', 'acc_1200', 'Detail', NOW(), NOW()),
('acc_1220', '1220', 'Buildings', 'acc_1200', 'Detail', NOW(), NOW()),
('acc_2000', '2000', 'Liabilities', NULL, 'Header', NOW(), NOW()),
('acc_2100', '2100', 'Current Liabilities', 'acc_2000', 'Header', NOW(), NOW()),
('acc_2110', '2110', 'Accounts Payable', 'acc_2100', 'Detail', NOW(), NOW()),
('acc_2120', '2120', 'Accrued Expenses', 'acc_2100', 'Detail', NOW(), NOW()),
('acc_3000', '3000', 'Equity', NULL, 'Header', NOW(), NOW()),
('acc_3100', '3100', 'Common Stock', 'acc_3000', 'Detail', NOW(), NOW()),
('acc_3200', '3200', 'Retained Earnings', 'acc_3000', 'Detail', NOW(), NOW());
