package com.fiscaladmin.joget.approval;

import java.time.Clock;
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

    /** Stamps {@code row} as storeFormData stamps a row it saves, at this moment. */
    void stamp(FormRow row) {
        if (row == null) {
            return;
        }
        Date d = Date.from(clock.instant());
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

    /** The bundle's save of one row: stamped, then saved by the data access object. */
    void save(FormDataDao dao, String form, String table, FormRow row) {
        stamp(row);
        FormRowSet set = new FormRowSet();
        set.add(row);
        dao.saveOrUpdate(form, table, set);
    }
}
