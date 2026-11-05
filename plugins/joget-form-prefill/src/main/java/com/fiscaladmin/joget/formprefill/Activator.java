package com.fiscaladmin.joget.formprefill;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Bundle Activator for the project-neutral joget-form-prefill bundle.
 * Registers two plugins — {@link FormPrefillLoadBinder} and, since 1.1.0,
 * {@link GridRowsPrefillBinder} — so the JAR can be dropped into any Joget instance
 * without dragging in unrelated engines.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();
        registrationList.add(context.registerService(
                FormPrefillLoadBinder.class.getName(), new FormPrefillLoadBinder(), null));
        // 1.1.0 (METHOD-2026-10-24-01): a grid's lines carried from the record a form is opened on
        registrationList.add(context.registerService(
                GridRowsPrefillBinder.class.getName(), new GridRowsPrefillBinder(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
