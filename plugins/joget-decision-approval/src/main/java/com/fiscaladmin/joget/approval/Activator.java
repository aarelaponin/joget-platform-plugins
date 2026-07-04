package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Registers the decision &amp; approval Joget plugins: the decide gate, the SLA sweep, the delegate
 * hand-off, the authority-matrix validator, and the per-user inbox binder. The service classes they
 * drive are exported for consumers; the effect bodies are supplied by the consumer through
 * {@link DecisionEffects} at its own start-up.
 */
public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();
        registrationList.add(context.registerService(
                ApprovalGateEngine.class.getName(), new ApprovalGateEngine(), null));
        registrationList.add(context.registerService(
                ApprovalSweepEngine.class.getName(), new ApprovalSweepEngine(), null));
        registrationList.add(context.registerService(
                ApprovalDelegateEngine.class.getName(), new ApprovalDelegateEngine(), null));
        registrationList.add(context.registerService(
                AuthorityMatrixEngine.class.getName(), new AuthorityMatrixEngine(), null));
        registrationList.add(context.registerService(
                ApprovalInboxBinder.class.getName(), new ApprovalInboxBinder(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}
