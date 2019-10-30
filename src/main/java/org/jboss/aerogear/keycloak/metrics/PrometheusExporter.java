package org.jboss.aerogear.keycloak.metrics;

import static com.google.common.base.Strings.nullToEmpty;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import org.jboss.logging.Logger;
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

    private final static Logger logger = Logger.getLogger(PrometheusExporter.class);

    // these fields are package private on purpose
    final Map<String, Counter> counters = new HashMap<>();
    final Counter totalLogins;
    final Counter totalFailedLoginAttempts;
    final Counter totalRegistrations;
    final Counter totalRegistrationsErrors;
    final Counter responseErrors;
    final Histogram requestDuration;

    private PrometheusExporter() {
        // The metrics collector needs to be a singleton because requiring a
        // provider from the KeyCloak session (session#getProvider) will always
        // create a new instance. Not sure if this is a bug in the SPI implementation
        // or intentional but better to avoid this. The metrics object is single-instance
        // anyway and all the Gauges are suggested to be static (it does not really make
        // sense to record the same metric in multiple places)

        // package private on purpose
        totalLogins = Counter.build()
            .name("keycloak_logins")
            .help("Total successful logins")
            .labelNames("realm", "provider", "client_id")
            .register();

        // package private on purpose
        totalFailedLoginAttempts = Counter.build()
            .name("keycloak_failed_login_attempts")
            .help("Total failed login attempts")
            .labelNames("realm", "provider", "error", "client_id")
            .register();

        // package private on purpose
        totalRegistrations = Counter.build()
            .name("keycloak_registrations")
            .help("Total registered users")
            .labelNames("realm", "provider", "client_id")
            .register();

        totalRegistrationsErrors = Counter.build()
            .name("keycloak_registrations_errors")
            .help("Total errors on registrations")
            .labelNames("realm", "provider", "error", "client_id")
            .register();

        responseErrors = Counter.build()
            .name("keycloak_response_errors")
            .help("Total number of error responses")
            .labelNames("code", "method", "route")
            .register();

        requestDuration = Histogram.build()
            .name("keycloak_request_duration")
            .help("Request duration")
            .buckets(50, 100, 250, 500, 1000, 2000, 10000, 30000)
            .labelNames("method", "route")
            .register();

        // Counters for all user events
        for (EventType type : EventType.values()) {
            if (type.equals(EventType.LOGIN) || type.equals(EventType.LOGIN_ERROR) || type.equals(EventType.REGISTER)) {
                continue;
            }
            final String counterName = buildCounterName(type);
            counters.put(counterName, createCounter(counterName, false));
        }

        // Counters for all admin events
        for (OperationType type : OperationType.values()) {
            final String counterName = buildCounterName(type);
            counters.put(counterName, createCounter(counterName, true));
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
        final String counterName = buildCounterName(event.getType());
        if (counters.get(counterName) == null) {
            logger.warnf("Counter for event type %s does not exist. Realm: %s", event.getType().name(), nullToEmpty(event.getRealmId()));
            return;
        }
        counters.get(counterName).labels(nullToEmpty(event.getRealmId())).inc();
    }

    /**
     * Count generic admin event
     *
     * @param event Admin event
     */
    public void recordGenericAdminEvent(final AdminEvent event) {
        final String counterName = buildCounterName(event.getOperationType());
        if (counters.get(counterName) == null) {
            logger.warnf("Counter for admin event operation type %s does not exist. Resource type: %s, realm: %s", event.getOperationType().name(), event.getResourceType().name(), event.getRealmId());
            return;
        }
        counters.get(counterName).labels(nullToEmpty(event.getRealmId()), event.getResourceType().name()).inc();
    }

    /**
     * Increase the number of currently logged in users
     *
     * @param event Login event
     */
    public void recordLogin(final Event event) {
        final String provider = getIdentityProvider(event);

        totalLogins.labels(nullToEmpty(event.getRealmId()), provider, nullToEmpty(event.getClientId())).inc();
    }

    /**
     * Increase the number registered users
     *
     * @param event Register event
     */
    public void recordRegistration(final Event event) {
        final String provider = getIdentityProvider(event);

        totalRegistrations.labels(nullToEmpty(event.getRealmId()), provider, nullToEmpty(event.getClientId())).inc();
    }

    /**
     * Increase the number of failed registered users attemps
     *
     * @param event RegisterError event
     */
    public void recordRegistrationError(final Event event) {
        final String provider = getIdentityProvider(event);

        totalRegistrationsErrors.labels(nullToEmpty(event.getRealmId()), provider, nullToEmpty(event.getError()), nullToEmpty(event.getClientId())).inc();
    }

    /**
     * Increase the number of failed login attempts
     *
     * @param event LoginError event
     */
    public void recordLoginError(final Event event) {
        final String provider = getIdentityProvider(event);

        totalFailedLoginAttempts.labels(nullToEmpty(event.getRealmId()), provider, nullToEmpty(event.getError()), nullToEmpty(event.getClientId())).inc();
    }

    /**
     * Record the duration between one request and response
     *
     * @param amt    The duration in milliseconds
     * @param method HTTP method of the request
     * @param route  Request route / path
     */
    public void recordRequestDuration(double amt, String method, String route) {
        requestDuration.labels(method, route).observe(amt);
    }

    /**
     * Increase the response error count by a given method and route
     *
     * @param code   The returned http status code
     * @param method The request method used
     * @param route  The request route / path
     */
    public void recordResponseError(int code, String method, String route) {
        responseErrors.labels(Integer.toString(code), method, route).inc();
    }

    /**
     * Retrieve the identity prodiver name from event details or
     * default to {@value #PROVIDER_KEYCLOAK_OPENID}.
     *
     * @param event User event
     * @return Identity provider name
     */
    private String getIdentityProvider(Event event) {
        String identityProvider = null;
        if (event.getDetails() != null) {
            identityProvider = event.getDetails().get("identity_provider");
        }
        if (identityProvider == null) {
            identityProvider = PROVIDER_KEYCLOAK_OPENID;
        }
        return identityProvider;
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

    private String buildCounterName(OperationType type) {
        return ADMIN_EVENT_PREFIX + type.name();
    }

    private String buildCounterName(EventType type) {
        return USER_EVENT_PREFIX + type.name();
    }

}
