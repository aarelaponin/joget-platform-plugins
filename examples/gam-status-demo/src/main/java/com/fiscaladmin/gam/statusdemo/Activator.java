package com.fiscaladmin.gam.statusdemo;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;


/**
 * OSGi Bundle Activator for the GAM status-manager reuse proof.
 *
 * <p>This is the "consumer binding" step of the platform integration contract:
 * at bundle start we register the one guard plugin. The event carrier is named by
 * the consumer at the call site ({@link GamMoveGuard#F_EVENT}) — binding it here
 * from a static would re-aim every other consumer sharing the event-chain library
 * bundle in this JVM. The platform status-manager and event-chain bundles are
 * imported from the running instance — no platform code is copied here.</p>
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        // The GAM demo's event table is named at the call site (GamMoveGuard.F_EVENT).
        // No process-wide default: joget-event-chain is a shared library bundle and a
        // static set here re-aims every other consumer in the JVM.
        registrationList = new ArrayList<ServiceRegistration>();
        registrationList.add(context.registerService(
                GamMoveGuard.class.getName(), new GamMoveGuard(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
