package org.joget;

import java.util.ArrayList;
import java.util.Collection;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        if (SecurityUtil.getNonceGenerator() != null) {
            //Register plugin here
            registrationList.add(context.registerService(TreeMenu.class.getName(), new TreeMenu(), null));
        } else {
            LogUtil.info("org.joget.TreeMenu", "Not available for community version");
        }
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}