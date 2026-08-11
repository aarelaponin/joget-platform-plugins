package com.fiscaladmin.joget.transitionguard;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {
    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        // No process-wide event-form binding here. joget-event-chain is a shared
        // library bundle: setting a static default from an Activator re-aimed every
        // other consumer in the JVM (the 3–11 August 2026 collision with cmbb-plugins).
        // The carrier is named at the call site — see TransitionGuard.EVENT_FORM.
        registrationList = new ArrayList<ServiceRegistration>();
        registrationList.add(context.registerService(
                TransitionGuard.class.getName(), new TransitionGuard(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
