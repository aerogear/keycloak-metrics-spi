package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public final class PrometheusExporter {

    private final static String USER_EVENT_PREFIX = "keycloak_user_event_";
    private final static String ADMIN_EVENT_PREFIX = "keycloak_admin_event_";
    private final static String PROVIDER_KEYCLOAK_OPENID = "keycloak";

    private final static PrometheusExporter INSTANCE = new PrometheusExporter();

    // package private by on purpose
    final Map<String, Counter> counters = new HashMap<>();

    // package private by on purpose
    final Counter totalLogins;

    // package private by on purpose
    final Counter totalFailedLoginAttempts;

    // package private by on purpose
    final Counter totalRegistrations;

    private PrometheusExporter() {
        // The metrics collector needs to be a singleton because requiring a
        // provider from the KeyCloak session (session#getProvider) will always
        // create a new instance. Not sure if this is a bug in the SPI implementation
        // or intentional but better to avoid this. The metrics object is single-instance
        // anyway and all the Gauges are suggested to be static (it does not really make
        // sense to record the same metric in multiple places)

        // package private by on purpose
        totalLogins = Counter.build()
            .name("keycloak_logins")
            .help("Total successful logins")
            .labelNames("realm", "provider")
            .register();

        // package private by on purpose
        totalFailedLoginAttempts = Counter.build()
            .name("keycloak_failed_login_attempts")
            .help("Total failed login attempts")
            .labelNames("realm", "provider", "error")
            .register();

        // package private by on purpose
        totalRegistrations = Counter.build()
            .name("keycloak_registrations")
            .help("Total registered users")
            .labelNames("realm", "provider")
            .register();

        // Counters for all user events
        for (EventType type : EventType.values()) {
            if (type.equals(EventType.LOGIN) || type.equals(EventType.LOGIN_ERROR) || type.equals(EventType.REGISTER)) {
                continue;
            }
            final String eventName = USER_EVENT_PREFIX + type.name();
            counters.put(eventName, createCounter(eventName, false));
        }

        // Counters for all admin events
        for (OperationType type : OperationType.values()) {
            final String eventName = ADMIN_EVENT_PREFIX + type.name();
            counters.put(eventName, createCounter(eventName, true));
        }

        // Initialize the default metrics for the hotspot VM
        DefaultExports.initialize();
    }

    public static PrometheusExporter instance() {
        return INSTANCE;
    }

    /**
     * Creates a counter based on a event name
     */
    private static Counter createCounter(final String name, boolean isAdmin) {
        final Counter.Builder counter = Counter.build().name(name);

        if (isAdmin) {
            counter.labelNames("realm", "resource").help("Generic KeyCloak Admin event");
        } else {
            counter.labelNames("realm").help("Generic KeyCloak User event");
        }

        return counter.register();
    }

    /**
     * Count generic user event
     *
     * @param event User event
     */
    public void recordGenericEvent(final Event event) {
        final String eventName = USER_EVENT_PREFIX + event.getType().name();
        counters.get(eventName).labels(event.getRealmId()).inc();
    }

    /**
     * Count generic admin event
     *
     * @param event Admin event
     */
    public void recordGenericAdminEvent(final AdminEvent event) {
        final String eventName = ADMIN_EVENT_PREFIX + event.getOperationType().name();
        counters.get(eventName).labels(event.getRealmId(), event.getResourceType().name()).inc();
    }

    /**
     * Increase the number of currently logged in users
     *
     * @param event Login event
     */
    public void recordLogin(final Event event) {
        final String provider = event.getDetails()
                .getOrDefault("identity_provider", PROVIDER_KEYCLOAK_OPENID);

        totalLogins.labels(event.getRealmId(), provider).inc();
    }

    /**
     * Increase the number registered users
     *
     * @param event Register event
     */
    public void recordRegistration(final Event event) {
        final String provider = event.getDetails()
                .getOrDefault("identity_provider", PROVIDER_KEYCLOAK_OPENID);

        totalRegistrations.labels(event.getRealmId(), provider).inc();
    }


    /**
     * Increase the number of failed login attempts
     *
     * @param event LoginError event
     */
    public void recordLoginError(final Event event) {
        final String provider = event.getDetails()
                .getOrDefault("identity_provider", PROVIDER_KEYCLOAK_OPENID);

        totalFailedLoginAttempts.labels(event.getRealmId(), provider, event.getError()).inc();
    }

    /**
     * Write the Prometheus formatted values of all counters and
     * gauges to the stream
     *
     * @param stream Output stream
     * @throws IOException
     */
    public void export(final OutputStream stream) throws IOException {
        final Writer writer = new BufferedWriter(new OutputStreamWriter(stream));
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
        writer.flush();
    }

}
