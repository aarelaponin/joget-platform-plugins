package com.fiscaladmin.joget.formprefill;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * OSGi Bundle Activator for the project-neutral joget-form-prefill bundle.
 * Registers exactly one plugin — {@link FormPrefillLoadBinder} — so the JAR can be
 * dropped into any Joget instance without dragging in unrelated engines.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();
        registrationList.add(context.registerService(
                FormPrefillLoadBinder.class.getName(), new FormPrefillLoadBinder(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
