package com.fiscaladmin.joget.transitionguard;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.Group;
import org.joget.directory.model.service.DirectoryManager;

/** The roles a person holds: the ids of the directory groups they belong to. */
interface GroupLookup {

    Collection<String> groupsOf(String username);

    /** The platform's directory. An unreadable directory gives no roles, so the move is refused. */
    GroupLookup DIRECTORY = new GroupLookup() {
        @Override
        public Collection<String> groupsOf(String username) {
            Set<String> out = new HashSet<String>();
            if (username == null || username.isEmpty()) {
                return out;
            }
            try {
                DirectoryManager dm = (DirectoryManager) AppUtil.getApplicationContext()
                        .getBean("directoryManager");
                Collection<Group> groups = dm == null ? null : dm.getGroupByUsername(username);
                if (groups != null) {
                    for (Group g : groups) {
                        if (g != null && g.getId() != null) {
                            out.add(g.getId());
                        }
                    }
                }
            } catch (Exception e) {
                LogUtil.error(GroupLookup.class.getName(), e,
                        "could not read the groups of '" + username + "' — the move is refused");
            }
            return out;
        }
    };
}
