package com.fiscaladmin.joget.eventchain;

import java.util.Date;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.FormRow;
import org.joget.directory.model.User;
import org.joget.directory.model.service.DirectoryUtil;
import org.joget.workflow.model.service.WorkflowUserManager;

/**
 * The columns the platform fills on a form's row when it saves it, filled on the event rows
 * {@link CaseEventWriter} writes (METHOD-2026-11-05-51, R34; MT-DM's hand-off of 8 November 2026,
 * answer to item 46).
 *
 * <p>The rule is {@code AppServiceImpl.storeFormData} of Joget 9.0.7 (lines 2161-2196), as the
 * transition guard and the approval route already apply it (R32): for a row it saves,
 * {@code dateModified} is the instant of the save, {@code modifiedBy} is
 * {@code WorkflowUserManager.getCurrentUsername()} as it stands (the signed-in user,
 * {@code roleSystem}, or {@code roleAnonymous} where nobody is signed in) and
 * {@code modifiedByName} is {@code DirectoryUtil.getUserFullName} of the current user, none where
 * there is no user on the thread; {@code dateCreated}, {@code createdBy} and
 * {@code createdByName} are kept as the row was loaded and filled as the modified three only
 * where the row holds no {@code dateCreated}. An event is always a new row, so it takes all six.
 * {@code FormDataDao.saveOrUpdate}, which the writer calls, fills none of them.
 *
 * <p><b>These columns are not part of the chain.</b> An event's hash is
 * {@code SHA-256(payload + prevHash)} (see {@link CaseEventWriter}); the stamps are columns of
 * the row beside {@code payload}, never inside it, so a chain written before the stamps existed
 * verifies exactly as it did, and a chain written with them verifies the same way.
 *
 * <p>Reading the user is fail-soft: a stamp that cannot read the platform's user (no application
 * context, a class the bundle cannot see) leaves the who-columns empty and never stops the event.
 */
public final class RowStamp {

    /** Who is acting, as the platform reads it. The test seam. */
    public interface Who {
        /** {@code WorkflowUserManager.getCurrentUsername()}, or null where it cannot be read. */
        String username();

        /** {@code DirectoryUtil.getUserFullName} of the current user, or null where there is none. */
        String fullName();
    }

    /** The platform's own: the workflow user manager the platform saves a form's row with. */
    public static final Who PLATFORM = new Who() {
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

    private RowStamp() {
    }

    /** Stamps {@code row} as storeFormData stamps a row it saves, at {@code at}, by {@code who}. */
    static void apply(FormRow row, Date at, Who who) {
        if (row == null) {
            return;
        }
        String user = who.username();
        String name = who.fullName();
        row.setDateModified(at);
        row.setModifiedBy(user);
        row.setModifiedByName(name);
        if (row.getDateCreated() == null) {
            row.setDateCreated(at);
            row.setCreatedBy(user);
            row.setCreatedByName(name);
        }
    }
}
