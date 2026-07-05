package com.fiscaladmin.gam.statusdemo;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

/**
 * OSGi Bundle Activator for the GAM status-manager reuse proof.
 *
 * <p>This is the "consumer binding" step of the platform integration contract: at
 * bundle start we point the (process-wide, settable) event-chain default at the GAM
 * demo event table, then register the one guard plugin. The platform status-manager
 * and event-chain bundles are imported from the running instance — no platform code
 * is copied here.</p>
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        // Bind the GAM demo's own event table; StatusManager appends STATUS_CHANGED here.
        CaseEventWriter.setDefaultEventFormId("gamEvent");

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
