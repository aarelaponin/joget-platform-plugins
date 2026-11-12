package com.fiscaladmin.joget.lists;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Registers the bundle's column formatter as an OSGi service under its class name, as every plugin bundle of the
 * library does. The platform finds a plugin by that name: {@code PluginManager.getPlugin} calls
 * {@code loadOsgiPlugin}, which asks the bundle context for the service reference of the class name, loads the class
 * through the registering bundle and makes a new instance of it (jw 9.0.7, javap -c of PluginManager.getPlugin and
 * loadOsgiPlugin); {@code loadOsgiPlugins} lists every registered service that is an {@code instanceof Plugin}.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();
        registrationList.add(context.registerService(
                WordedOptionsFormatter.class.getName(), new WordedOptionsFormatter(), null));
    }

    public void stop(BundleContext context) {
        if (registrationList == null) {
            return;
        }
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
