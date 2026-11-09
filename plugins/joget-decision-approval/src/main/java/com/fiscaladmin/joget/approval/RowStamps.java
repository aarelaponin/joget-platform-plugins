package com.fiscaladmin.joget.approval;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryUtil;
import org.joget.workflow.model.service.WorkflowUserManager;

/**
 * The columns the platform fills on a form's row when it saves it, filled on the rows the
 * decision and approval service and its engines write (METHOD-2026-11-05-45; MT-DM's hand-off of
 * 8 November 2026, item 46).
 *
 * <p>The rule is {@code AppServiceImpl.storeFormData} of Joget 9.0.7 (lines 2161-2196, read from
 * the installation's class): for a row it saves, {@code dateModified} is the instant of the save,
 * {@code modifiedBy} is {@code WorkflowUserManager.getCurrentUsername()} as it stands (the
 * signed-in user, {@code roleSystem}, or {@code roleAnonymous} where nobody is signed in) and
 * {@code modifiedByName} is {@code DirectoryUtil.getUserFullName} of the current user, none where
 * there is no user on the thread; {@code dateCreated}, {@code createdBy} and
 * {@code createdByName} are kept as the row was loaded, and stamped as the modified three only
 * where the row holds no {@code dateCreated}. A row the service makes (a new {@code FormRow})
 * holds none, so it takes all six; a row it loads and changes keeps its created three.
 * {@code FormDataDao.saveOrUpdate}, which every write here calls, fills none of them.
 *
 * <p>{@link #save} is the one place the bundle's rows are saved from: it stamps the row and saves
 * it, the clock read at the save as the platform reads it. Reading the user is fail-soft: a stamp
 * that cannot read the platform's user leaves the who-columns empty and never stops the save.
 */
final class RowStamps {

    /** Who is acting, as the platform reads it. The test seam. */
    interface Who {
        /** {@code WorkflowUserManager.getCurrentUsername()}, or null where it cannot be read. */
        String username();

        /** {@code DirectoryUtil.getUserFullName} of the current user, or null where there is none. */
        String fullName();
    }

    /** The platform's own: the workflow user manager the platform saves a form's row with. */
    static final Who PLATFORM = new Who() {
        private WorkflowUserManager manager() {
            return (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        }

        @Override
        public String username() {
            try {
                return manager().getCurrentUsername();
            } catch (Exception | LinkageError e) {
                return null;
            }
        }

        @Override
        public String fullName() {
            try {
                User u = manager().getCurrentUser();
                return u == null ? null : DirectoryUtil.getUserFullName(u);
            } catch (Exception | LinkageError e) {
                return null;
            }
        }
    };

    /**
     * The instant of the latest reading {@link #now} took on this thread (version 2 of R46, G3 of the review of version 1).
     * A {@code LocalDateTime} does not say which pass of the hour that repeats when summer time ends it is in, so the
     * instant read is kept beside it and given back by {@link #instantOf}; static, because the engines and the services
     * each hold a {@code RowStamps} of their own and a move is read by one and turned by another, on one thread.
     */
    private static final ThreadLocal<Instant> READ = new ThreadLocal<Instant>();

    private final Clock clock;
    private final Who who;

    RowStamps(Clock clock, Who who) {
        this.clock = clock;
        this.who = who;
    }

    /** The platform's clock (the JVM's zone) and the platform's user. */
    static RowStamps platform() {
        return new RowStamps(Clock.systemDefaultZone(), PLATFORM);
    }

    /**
     * The moment of an act that starts a move, read from this clock (1.5.0, version 2 of R34; R36; the route and
     * authority-matrix engines since 1.6.0, R40): the gate, delegate, route and matrix engines read it once, call
     * the service with it (the route engine, for the day its band is looked up at) and save their rows with it, so the act, the
     * engine's result row and the move's rows and events are one reading of the one clock. The sweep engine's
     * moment is its {@code asOf}: this reading where the {@code asOf} is blank or cannot be parsed, so one
     * reading; the {@code asOf} it was given otherwise, so no reading of the clock, and the result row, the
     * rows and the events carry that {@code asOf}. For the platform's clock it is {@code LocalDateTime.now()}.
     */
    LocalDateTime now() {
        Instant read = clock.instant();               // the one reading; its instant is kept (version 2)
        READ.set(read);
        return LocalDateTime.ofInstant(read, clock.getZone());
    }

    /**
     * A move's moment (the {@code now} the service is called with) as an instant, in the clock's
     * zone (the JVM's zone for the platform's clock): the instant the move's events are handed to
     * the event writer with, so an event's stamps and the move's own moment are one reading.
     *
     * <p>Version 2 of R46: a moment {@link #now} read is given back as the instant it was read, never turned back
     * from its local time, which in the hour that repeats when summer time ends names two instants and takes the earlier.
     * Only a local time no reading of this thread gave (the sweep's {@code asOf}, as the caller wrote it) is turned by the
     * zone's rules, as before.
     */
    Instant instantOf(LocalDateTime moment) {
        Instant read = READ.get();
        if (read != null && LocalDateTime.ofInstant(read, clock.getZone()).equals(moment)) {
            return read;
        }
        return moment.atZone(clock.getZone()).toInstant();
    }

    /** Stamps {@code row} as storeFormData stamps a row it saves, at the clock's reading now. */
    void stamp(FormRow row) {
        stamp(row, clock.instant());
    }

    /**
     * Stamps {@code row} as storeFormData stamps a row it saves, at {@code at} (1.5.0, version 2 of R34): the
     * instant of the move that saves it, which is the instant the move's events are handed, so a row and the
     * events of one move are one reading and not two readings of a clock a second apart.
     */
    void stamp(FormRow row, Instant at) {
        if (row == null) {
            return;
        }
        Date d = Date.from(at);
        String user = who.username();
        String name = who.fullName();
        row.setDateModified(d);
        row.setModifiedBy(user);
        row.setModifiedByName(name);
        if (row.getDateCreated() == null) {
            row.setDateCreated(d);
            row.setCreatedBy(user);
            row.setCreatedByName(name);
        }
    }

    /** The bundle's save of one row that belongs to no move with a moment: stamped from the clock now, then saved. */
    void save(FormDataDao dao, String form, String table, FormRow row) {
        save(dao, form, table, row, null);
    }

    /**
     * The bundle's save of one row a move writes (1.5.0): stamped from the move's moment ({@code moment}, the
     * {@code now} the move is called with, {@code asOf} for the sweep) as {@link #instantOf} turns it, the same
     * instant its events are handed; with no moment, from the clock read once at the save.
     */
    void save(FormDataDao dao, String form, String table, FormRow row, LocalDateTime moment) {
        stamp(row, moment == null ? clock.instant() : instantOf(moment));
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(form, table, set);
    }
}
