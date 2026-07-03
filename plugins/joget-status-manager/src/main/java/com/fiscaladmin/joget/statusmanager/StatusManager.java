package com.fiscaladmin.joget.statusmanager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * Config-driven status state machine for subject/child entities. Native + Joget-only:
 * the lifecycle lives in the {@code mmEntityState} / {@code mmEntityTransition} forms
 * (read via {@link MmConfigService}); the status is written via {@link FormDataDao}
 * (which bypasses a read-only form attribute — engines only); each accepted transition
 * appends a {@code STATUS_CHANGED} row to the case event chain via {@link CaseEventWriter}.
 * No external framework, no new database, no raw SQL, no new process.
 *
 * <p><b>Scope resolution (per-dimension):</b> most-specific-first over the supplied chain
 * (e.g. {@code taxType, caseType}); the first scope that has ANY transition rows wins
 * WHOLLY ("wholesale shadowing") — its lifecycle fully replaces the less-specific ones.
 * {@code DEFAULT} is always the final fallback.
 */
public class StatusManager {

    public static final String EVENT_TYPE = "STATUS_CHANGED";
    public static final String DEFAULT_SCOPE = "DEFAULT";

    private final FormDataDao dao;
    private final MmConfigService cfg;
    private final CaseEventWriter events;

    public StatusManager(FormDataDao dao) {
        this(dao, new MmConfigService(dao), new CaseEventWriter(dao));
    }

    /** Test seam — inject the config reader and event writer. */
    public StatusManager(FormDataDao dao, MmConfigService cfg, CaseEventWriter events) {
        this.dao = dao;
        this.cfg = cfg;
        this.events = events;
    }

    /** First scope in the chain that has transition rules; {@code DEFAULT} otherwise. */
    public String resolveScope(String entity, List<String> scopeChain) {
        for (String s : effectiveChain(scopeChain)) {
            if (cfg.entityScopeHasRules(entity, s)) {
                return s;
            }
        }
        return DEFAULT_SCOPE;
    }

    static List<String> effectiveChain(List<String> scopeChain) {
        List<String> chain = new ArrayList<String>();
        if (scopeChain != null) {
            for (String s : scopeChain) {
                if (s != null && !s.isEmpty() && !chain.contains(s)) {
                    chain.add(s);
                }
            }
        }
        if (!chain.contains(DEFAULT_SCOPE)) {
            chain.add(DEFAULT_SCOPE);
        }
        return chain;
    }

    public boolean canTransition(String entity, List<String> scopeChain, String from, String to) {
        String scope = resolveScope(entity, scopeChain);
        for (FormRow r : safe(cfg.entityTransitions(entity, scope))) {
            if (eq(r.getProperty("fromStatus"), from) && eq(r.getProperty("toStatus"), to)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> validNext(String entity, List<String> scopeChain, String from) {
        String scope = resolveScope(entity, scopeChain);
        Set<String> next = new LinkedHashSet<String>();
        for (FormRow r : safe(cfg.entityTransitions(entity, scope))) {
            if (eq(r.getProperty("fromStatus"), from)) {
                next.add(r.getProperty("toStatus"));
            }
        }
        return next;
    }

    public boolean isTerminal(String entity, List<String> scopeChain, String status) {
        return cfg.entityTerminalStatuses(entity, resolveScope(entity, scopeChain)).contains(status);
    }

    /**
     * Guarded transition: loads the record, validates current → target against the
     * resolved scope, writes the new status (FormDataDao), and appends a
     * STATUS_CHANGED event to the case chain. Throws {@link InvalidTransitionException}
     * on an illegal move (nothing is written).
     */
    public void transition(String entity, String tableName, String statusField, String recordId,
                           String caseId, String targetStatus, List<String> scopeChain,
                           String actor, String reason) {
        FormRow row = dao.load(entity, tableName, recordId);
        if (row == null) {
            throw new InvalidTransitionException("", targetStatus,
                    entity + " record not found: " + recordId);
        }
        String current = nz(row.getProperty(statusField));
        String scope = resolveScope(entity, scopeChain);
        if (!canTransition(entity, scopeChain, current, targetStatus)) {
            throw new InvalidTransitionException(current, targetStatus,
                    "Illegal transition for " + entity + " [scope=" + scope + "]: "
                            + current + " -> " + targetStatus);
        }
        row.setProperty(statusField, targetStatus);
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(entity, tableName, set);
        String extra = "\"entity\":\"" + CaseEventWriter.esc(entity) + "\""
                + ",\"recordId\":\"" + CaseEventWriter.esc(recordId) + "\""
                + ",\"scope\":\"" + CaseEventWriter.esc(scope) + "\"";
        events.append(caseId, EVENT_TYPE, actor, current, targetStatus, reason, extra);
    }

    /**
     * Guard + audit a transition on an <b>already-loaded</b> row that the CALLER will
     * persist. Validates {@code current(row) → targetStatus} against the resolved scope;
     * on success sets the status field on the same row (so the caller's own
     * {@code saveOrUpdate} writes it together with the other field changes it has batched)
     * and appends a {@code STATUS_CHANGED} event to the case chain. Throws
     * {@link InvalidTransitionException} on an illegal move — before the row is saved, so
     * the engine aborts and nothing persists. No DB write happens here.
     *
     * <p>Use this (not {@link #transition}) inside engines that mutate several fields on a
     * row then save once; {@link #transition} is for the load-write-audit-in-one case.
     */
    public void apply(String entity, FormRow row, String statusField, String caseId,
                      String targetStatus, List<String> scopeChain, String actor, String reason) {
        String current = nz(row.getProperty(statusField));
        String scope = resolveScope(entity, scopeChain);
        if (!canTransition(entity, scopeChain, current, targetStatus)) {
            throw new InvalidTransitionException(current, targetStatus,
                    "Illegal transition for " + entity + " [scope=" + scope + "]: "
                            + current + " -> " + targetStatus);
        }
        row.setProperty(statusField, targetStatus);
        String extra = "\"entity\":\"" + CaseEventWriter.esc(entity) + "\""
                + ",\"recordId\":\"" + CaseEventWriter.esc(row.getId()) + "\""
                + ",\"scope\":\"" + CaseEventWriter.esc(scope) + "\"";
        events.append(caseId, EVENT_TYPE, actor, current, targetStatus, reason, extra);
    }

    private static FormRowSet safe(FormRowSet rs) {
        return rs == null ? new FormRowSet() : rs;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
