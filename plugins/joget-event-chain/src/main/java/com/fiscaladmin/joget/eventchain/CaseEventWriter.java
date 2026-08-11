package com.fiscaladmin.joget.eventchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

/**
 * Append-only writer for the case event log — a tamper-evident case history.
 * Every accepted or rejected transition appends one row:
 * {timestamp, actor, prevState, newState, reason} as a JSON payload, chained by
 * hash = SHA-256(payload + prevHash). Root rows carry prevHash = "".
 * Never updates or deletes — immutability is enforced by having no other write
 * path (the event table has no edit UI).
 *
 * <p><b>The event table (form id) is per-writer and mandatory.</b> Every consumer
 * states its own carrier at construction. There is deliberately no process-wide
 * default and no setter.
 *
 * <p><b>Why (incident, 3–11 August 2026).</b> This class previously carried a
 * {@code static} default event form id with a process-wide setter, which each
 * consuming bundle called from its own OSGi Activator. This bundle is a shared
 * <em>library</em> bundle: two consumer bundles in one JVM share one class
 * instance, so the last Activator to start won for the whole process. When
 * {@code joget-transition-guard} (tax registration, {@code statusEvent}) was
 * installed alongside {@code cmbb-plugins} (collection management,
 * {@code cmEvent}) on 3 August 2026, every CMBB/DMBB case event was appended to
 * the tax-registration module's hash-linked chain instead of its own — silently,
 * for eight days. Seventeen of twenty-four regression runners failed with an
 * empty {@code cmEvent}, and the registration chain took ~30 foreign rows.
 * A per-JVM default in a cross-bundle library is a defect by construction:
 * make the caller say what it means.
 */
public class CaseEventWriter {

    private final FormDataDao dao;
    private final String eventFormId;
    /** Chains consecutive appends within one guard run without re-querying. */
    private String lastHash;
    private String lastHashCaseId;
    private long lastSeq = -1;

    /**
     * @param dao         the Joget form data access object
     * @param eventFormId the consumer's own event carrier (form id), required
     * @throws IllegalArgumentException if {@code eventFormId} is null or blank —
     *         an unaimed writer is never silently pointed somewhere plausible
     */
    public CaseEventWriter(FormDataDao dao, String eventFormId) {
        if (eventFormId == null || eventFormId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "CaseEventWriter: eventFormId is required — name the consumer's own event carrier");
        }
        this.dao = dao;
        this.eventFormId = eventFormId.trim();
    }

    public String getEventFormId() {
        return eventFormId;
    }

    /**
     * Appends an event row and returns its hash.
     * extraJson (nullable) is merged into the payload object verbatim —
     * pass pre-escaped JSON members like "\"link\":\"abc\"".
     */
    public String append(String caseId, String eventType, String actor,
                         String prevState, String newState, String reason, String extraJson) {
        String prevHash = lastEventHash(caseId);
        long seq = lastSeq + 1;
        String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(new Date());
        StringBuilder payload = new StringBuilder("{");
        payload.append("\"timestamp\":\"").append(esc(ts)).append('"');
        payload.append(",\"actor\":\"").append(esc(actor)).append('"');
        payload.append(",\"prevState\":\"").append(esc(prevState)).append('"');
        payload.append(",\"newState\":\"").append(esc(newState)).append('"');
        payload.append(",\"reason\":\"").append(esc(reason)).append('"');
        if (extraJson != null && !extraJson.isEmpty()) {
            payload.append(',').append(extraJson);
        }
        payload.append('}');
        String hash = sha256(payload + prevHash);

        FormRow row = new FormRow();
        row.setId(UUID.randomUUID().toString());
        row.setProperty("caseId", caseId);
        row.setProperty("seq", String.format("%010d", seq));
        row.setProperty("eventType", eventType);
        row.setProperty("actor", actor == null ? "" : actor);
        row.setProperty("eventTime", ts);
        row.setProperty("prevHash", prevHash);
        row.setProperty("hash", hash);
        row.setProperty("payload", payload.toString());
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(eventFormId, eventFormId, set);

        lastHash = hash;
        lastHashCaseId = caseId;
        lastSeq = seq;
        return hash;
    }

    /** Hash of the case's latest event, or "" for the root row. */
    public String lastEventHash(String caseId) {
        if (caseId.equals(lastHashCaseId) && lastHash != null) {
            return lastHash;
        }
        // chain order = zero-padded seq: same-millisecond eventTime ties across
        // different writer instances would otherwise fork the chain.
        FormRowSet rows = dao.find(eventFormId, eventFormId,
                "WHERE e.customProperties.caseId = ?1", new Object[]{caseId},
                "seq", Boolean.TRUE, 0, 1);
        if (rows == null || rows.isEmpty()) {
            lastSeq = -1;
            return "";
        }
        FormRow prev = rows.get(0);
        lastSeq = parseSeq(prev.getProperty("seq"));
        String h = prev.getProperty("hash");
        return h == null ? "" : h;
    }

    static long parseSeq(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0; // legacy rows without seq
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
