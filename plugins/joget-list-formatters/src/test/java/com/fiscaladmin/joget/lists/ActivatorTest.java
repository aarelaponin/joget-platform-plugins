package com.fiscaladmin.joget.lists;

import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * The bundle registers its formatter as the platform finds it: as a service under its class name
 * ({@code PluginManager.getPlugin} calls {@code loadOsgiPlugin}, which asks the bundle context for the service
 * reference of that name), and takes it down at stop.
 */
public class ActivatorTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void startRegistersTheFormatterUnderItsClassNameAndStopUnregistersIt() {
        BundleContext context = mock(BundleContext.class);
        ServiceRegistration registration = mock(ServiceRegistration.class);
        when(context.registerService(eq(WordedOptionsFormatter.class.getName()), any(), isNull()))
                .thenReturn(registration);
        Activator activator = new Activator();
        activator.start(context);
        verify(context).registerService(eq("com.fiscaladmin.joget.lists.WordedOptionsFormatter"),
                argThat(o -> o instanceof WordedOptionsFormatter), isNull());
        activator.stop(context);
        verify(registration).unregister();
    }

    @Test
    public void stopBeforeStartDoesNothing() {
        new Activator().stop(mock(BundleContext.class));
        assertTrue(true);
    }
}
