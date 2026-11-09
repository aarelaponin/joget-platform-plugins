package com.fiscaladmin.joget.transitionguard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryUtil;
import org.joget.workflow.model.service.WorkflowUserManager;

/**
 * The columns the platform fills on a form's row when it saves it, filled by the guard on the
 * rows it writes (METHOD-2026-11-05-45, MT-DM's hand-off of 8 November 2026, item 46).
 *
 * <p>The rule is {@code AppServiceImpl.storeFormData} of Joget 9.0.7 (lines 2161-2196, read from
 * the installation's class): for a row it saves, {@code dateModified} is the instant of the save,
 * {@code modifiedBy} is {@code WorkflowUserManager.getCurrentUsername()} as it stands (the
 * signed-in user, {@code roleSystem}, or {@code roleAnonymous} where nobody is signed in) and
 * {@code modifiedByName} is {@code DirectoryUtil.getUserFullName} of the current user, none where
 * there is no user on the thread; {@code dateCreated}, {@code createdBy} and
 * {@code createdByName} are kept as the row was loaded, and stamped as the modified three only
 * where the row holds no {@code dateCreated}. A row the guard makes (a new {@code FormRow}) holds
 * none, so it takes all six; a row it loads and changes keeps its created three.
 * {@code FormDataDao.saveOrUpdate}, which every write of the guard calls, fills none of them.
 *
 * <p>One stamp is taken for each move: its instant is read once, and the move's moment
 * ({@link #at}) is that instant in the clock's zone (the JVM's zone for the platform's clock), so a
 * row's {@code moment} and its {@code dateCreated} are one reading. The user is read when the
 * stamp is taken. Reading the user is fail-soft: a stamp that cannot read the platform's user
 * leaves the who-columns empty and never stops the move.
 */
final class RowStamp {

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

    /** The one instant of the move. */
    final Instant instant;
    /** That instant in the clock's zone: the move's moment, to the second. */
    final LocalDateTime at;
    final String user;
    final String name;

    RowStamp(Clock clock, Who who) {
        this(clock.instant(), clock.getZone(), who);
    }

    /**
     * A stamp taken at an instant already read (1.11.0): the move's moment an engine handed the guard, so no
     * clock is read here. The moment is that instant in {@code zone}; the user is read as the other constructor reads it.
     */
    RowStamp(Instant instant, ZoneId zone, Who who) {
        this.instant = instant;
        this.at = LocalDateTime.ofInstant(instant, zone);
        this.user = who.username();
        this.name = who.fullName();
    }

    /** Stamps {@code row} as storeFormData stamps a row it saves. */
    void apply(FormRow row) {
        if (row == null) return;
        Date d = Date.from(instant);
        row.setDateModified(d);
        row.setModifiedBy(user);
        row.setModifiedByName(name);
        if (row.getDateCreated() == null) {
            row.setDateCreated(d);
            row.setCreatedBy(user);
            row.setCreatedByName(name);
        }
    }
}
