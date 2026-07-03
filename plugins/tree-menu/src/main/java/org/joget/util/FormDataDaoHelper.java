package org.joget.util;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;

import java.util.Collection;

/**
 * Helper class for FormDataDao operations.
 * Provides convenient methods for querying form data tables.
 */
public class FormDataDaoHelper {

    private static final String CLASS_NAME = FormDataDaoHelper.class.getName();

    /**
     * Get FormDataDao bean from Spring context.
     */
    public static FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    /**
     * Get FormService bean from Spring context.
     */
    public static FormService getFormService() {
        return (FormService) AppUtil.getApplicationContext().getBean("formService");
    }

    /**
     * Get FormDefinitionDao bean from Spring context.
     */
    public static FormDefinitionDao getFormDefinitionDao() {
        return (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
    }

    /**
     * Load a form by its ID.
     */
    public static Form loadFormById(String formId, AppDefinition appDef) {
        if (formId == null || formId.isEmpty() || appDef == null) {
            return null;
        }
        try {
            FormService formService = getFormService();
            FormDefinitionDao formDefinitionDao = getFormDefinitionDao();
            FormDefinition formDef = formDefinitionDao.loadById(formId, appDef);
            if (formDef != null) {
                String formJson = formDef.getJson();
                return (Form) formService.createElementFromJson(formJson);
            }
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading form: " + formId);
        }
        return null;
    }

    /**
     * Load all rows from a form table.
     *
     * @param tableName The form table name (e.g., "tm_portfolio")
     * @param appDef    The application definition
     * @param sortField The field to sort by (can be null)
     * @param sortDesc  Sort descending if true
     * @param start     Start index for pagination (null for no pagination)
     * @param rows      Number of rows to return (null for all)
     * @return FormRowSet containing the results
     */
    public static FormRowSet loadFormData(String tableName, AppDefinition appDef,
                                          String sortField, Boolean sortDesc,
                                          Integer start, Integer rows) {
        if (tableName == null || tableName.isEmpty()) {
            return new FormRowSet();
        }

        try {
            FormDataDao formDataDao = getFormDataDao();

            // Build simple condition (no filtering)
            String condition = null;
            Object[] params = null;

            return formDataDao.find(null, tableName, condition, params, sortField, sortDesc, start, rows);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading form data from table: " + tableName);
            return new FormRowSet();
        }
    }

    /**
     * Load rows from a form table with a condition.
     *
     * @param tableName The form table name
     * @param appDef    The application definition
     * @param condition HQL WHERE clause (e.g., "WHERE e.customProperties.c_parent_id = ?")
     * @param params    Parameters for the condition
     * @param sortField The field to sort by
     * @param sortDesc  Sort descending if true
     * @param start     Start index for pagination
     * @param rows      Number of rows to return
     * @return FormRowSet containing the results
     */
    public static FormRowSet loadFormDataWithCondition(String tableName, AppDefinition appDef,
                                                       String condition, Object[] params,
                                                       String sortField, Boolean sortDesc,
                                                       Integer start, Integer rows) {
        if (tableName == null || tableName.isEmpty()) {
            return new FormRowSet();
        }

        try {
            FormDataDao formDataDao = getFormDataDao();
            return formDataDao.find(null, tableName, condition, params, sortField, sortDesc, start, rows);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading form data with condition from table: " + tableName);
            return new FormRowSet();
        }
    }

    /**
     * Load a single row by ID.
     *
     * @param tableName The form table name
     * @param appDef    The application definition
     * @param recordId  The record ID to load
     * @return The FormRow or null if not found
     */
    public static FormRow loadFormDataById(String tableName, AppDefinition appDef, String recordId) {
        if (tableName == null || recordId == null) {
            return null;
        }

        try {
            FormDataDao formDataDao = getFormDataDao();
            return formDataDao.load(null, tableName, recordId);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading form data by ID: " + recordId);
            return null;
        }
    }

    /**
     * Count rows in a form table.
     *
     * @param tableName The form table name
     * @param appDef    The application definition
     * @param condition HQL WHERE clause (can be null)
     * @param params    Parameters for the condition
     * @return The count of matching rows
     */
    public static long countFormData(String tableName, AppDefinition appDef,
                                     String condition, Object[] params) {
        if (tableName == null || tableName.isEmpty()) {
            return 0;
        }

        try {
            FormDataDao formDataDao = getFormDataDao();
            return formDataDao.count(null, tableName, condition, params);
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error counting form data in table: " + tableName);
            return 0;
        }
    }

    /**
     * Load root records (records with no parent).
     *
     * @param tableName   The form table name
     * @param parentField The parent field name
     * @param sortField   The field to sort by
     * @param sortDesc    Sort descending if true
     * @return FormRowSet containing root records
     */
    public static FormRowSet loadRootRecords(String tableName, String parentField,
                                             String sortField, Boolean sortDesc) {
        if (tableName == null || tableName.isEmpty()) {
            return new FormRowSet();
        }

        String condition = null;
        Object[] params = null;

        if (parentField != null && !parentField.isEmpty()) {
            // Find records where parent is null or empty
            condition = "WHERE (e.customProperties." + parentField + " IS NULL OR e.customProperties." + parentField + " = '')";
        }

        return loadFormDataWithCondition(tableName, null, condition, params, sortField, sortDesc, null, null);
    }

    /**
     * Load child records for a given parent.
     *
     * @param tableName      The form table name
     * @param parentField    The parent field name
     * @param parentRecordId The parent record ID
     * @param sortField      The field to sort by
     * @param sortDesc       Sort descending if true
     * @param start          Start index for pagination
     * @param rows           Number of rows to return
     * @return FormRowSet containing child records
     */
    public static FormRowSet loadChildRecords(String tableName, String parentField,
                                              String parentRecordId, String sortField, Boolean sortDesc,
                                              Integer start, Integer rows) {
        if (tableName == null || tableName.isEmpty() || parentField == null || parentField.isEmpty()) {
            return new FormRowSet();
        }

        String condition = "WHERE e.customProperties." + parentField + " = ?";
        Object[] params = new Object[]{parentRecordId};

        return loadFormDataWithCondition(tableName, null, condition, params, sortField, sortDesc, start, rows);
    }

    /**
     * Check if a record has children.
     *
     * @param tableName      The form table name
     * @param parentField    The parent field name
     * @param parentRecordId The potential parent record ID
     * @return true if the record has children
     */
    public static boolean hasChildren(String tableName, String parentField, String parentRecordId) {
        if (tableName == null || parentField == null || parentRecordId == null) {
            return false;
        }

        String condition = "WHERE e.customProperties." + parentField + " = ?";
        Object[] params = new Object[]{parentRecordId};

        return countFormData(tableName, null, condition, params) > 0;
    }

    /**
     * Count children of a record.
     *
     * @param tableName      The form table name
     * @param parentField    The parent field name
     * @param parentRecordId The parent record ID
     * @return The count of child records
     */
    public static long countChildren(String tableName, String parentField, String parentRecordId) {
        if (tableName == null || parentField == null || parentRecordId == null) {
            return 0;
        }

        String condition = "WHERE e.customProperties." + parentField + " = ?";
        Object[] params = new Object[]{parentRecordId};

        return countFormData(tableName, null, condition, params);
    }

    /**
     * Get a property value from a FormRow as a String.
     *
     * @param row The FormRow
     * @param key The property key
     * @return The property value as String, or null if not found
     */
    public static String getStringProperty(FormRow row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object value = row.getProperty(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a property value from a FormRow as an int.
     *
     * @param row          The FormRow
     * @param key          The property key
     * @param defaultValue Default value if property is not found or not a number
     * @return The property value as int
     */
    public static int getIntProperty(FormRow row, String key, int defaultValue) {
        if (row == null || key == null) {
            return defaultValue;
        }
        Object value = row.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
