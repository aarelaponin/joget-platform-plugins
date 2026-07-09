package com.fiscaladmin.joget.transitionguard;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.fiscaladmin.joget.eventchain.CaseEventWriter;

public class Activator implements BundleActivator {
    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        CaseEventWriter.setDefaultEventFormId("statusEvent");
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
