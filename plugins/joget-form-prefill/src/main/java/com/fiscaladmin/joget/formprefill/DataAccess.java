package com.fiscaladmin.joget.formprefill;

import java.util.List;
import java.util.Map;

/**
 * The only data the resolver needs — a tiny seam over Joget's {@code FormDataDao}.
 * The production implementation ({@link FormPrefillLoadBinder}) backs it with
 * {@code FormDataDao.find} / {@code FormDataDao.load}; unit tests back it with an
 * in-memory fake. No raw SQL on either side.
 */
public interface DataAccess {

    /** All rows of {@code formId}/{@code table} whose {@code matchField} equals {@code key}. */
    List<Map<String, String>> findByField(String formId, String table, String matchField, String key);

    /** The single row of {@code formId}/{@code table} with the given id, or {@code null}. */
    Map<String, String> loadById(String formId, String table, String id);
}
