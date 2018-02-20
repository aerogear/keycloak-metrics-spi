package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PrometheusExporter {

    private final static Logger logger = Logger.getLogger(PrometheusExporter.class);

    private final static String USER_EVENT_PREFIX = "keycloak_user_event_";
    private final static String ADMIN_EVENT_PREFIX = "keycloak_admin_event_";
    private final static String PROVIDER_KEYCLOAK_OPENID = "keycloak";

    private final static PrometheusExporter INSTANCE = new PrometheusExporter();

    private final UserEventChain userEventChain = new UserEventChain();

    // package private by on purpose
    final Map<EventType, Counter> userEventCounters = new HashMap<>();
    final Map<OperationType, Counter> adminEventCounters = new HashMap<>();

    private PrometheusExporter() {
        // The metrics collector needs to be a singleton because requiring a
        // provider from the KeyCloak session (session#getProvider) will always
        // create a new instance. Not sure if this is a bug in the SPI implementation
        // or intentional but better to avoid this. The metrics object is single-instance
        // anyway and all the Gauges are suggested to be static (it does not really make
        // sense to record the same metric in multiple places)


        // Counters for all user events
        for (EventType type : EventType.values()) {
            Counter counter = this.userEventChain.createCounter(type);
            if (counter == null) {
                final String eventName = USER_EVENT_PREFIX + type.name();
                counter = doCreateCounter(eventName, false);
            }
            userEventCounters.put(type, counter);
        }

        // Counters for all admin events
        for (OperationType type : OperationType.values()) {
            final String operationName = ADMIN_EVENT_PREFIX + type.name();
            adminEventCounters.put(type, doCreateCounter(operationName, true));
        }

        // Initialize the default metrics for the hotspot VM
        DefaultExports.initialize();
    }

    public static PrometheusExporter instance() {
        return INSTANCE;
    }

    public void recordEvent(Event event) {
        final EventType eventType = event.getType();
        if (eventType == null) {
            logger.warnf("No eventType given by Keycloak event! Event: %s", event.toString());
            return;
        }

        final String realmId = event.getRealmId();
        if (realmId == null || realmId.length() == 0) {
            logger.warnf("No realmId given by Keycloak event! Event: %s", event.toString());
            return;
        }

        boolean handled = userEventChain.handleEvent(event);
        if (!handled) {
            // treat as generic
            userEventCounters.get(eventType).labels(realmId).inc();
        }
    }

    public void recordAdminEvent(AdminEvent event) {
        final OperationType operationType = event.getOperationType();
        if (operationType == null) {
            logger.warnf("No operationType given by Keycloak event! Event: %s", event.toString());
            return;
        }

        final String realmId = event.getRealmId();
        if (realmId == null || realmId.length() == 0) {
            logger.warnf("No realmId given by Keycloak event! Event: %s", event.toString());
            return;
        }

        final ResourceType resourceType = event.getResourceType();
        if (resourceType == null) {
            logger.warnf("No resourceType given by Keycloak event! Event: %s", event.toString());
            return;
        }

        // treat as generic
        adminEventCounters.get(operationType).labels(realmId, resourceType.name()).inc();
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

    /**
     * Creates a counter based on a event name
     */
    private static Counter doCreateCounter(final String name, boolean isAdmin) {
        final Counter.Builder counter = Counter.build().name(name);

        if (isAdmin) {
            counter.labelNames("realm", "resource").help("Generic KeyCloak Admin event");
        } else {
            counter.labelNames("realm").help("Generic KeyCloak User event");
        }

        return counter.register();
    }

    private static class UserEventChain {
        private List<UserEventHandler> handlers;

        UserEventChain() {
            this.handlers = new ArrayList<>();

            // LOGIN handler
            this.handlers.add(
                new UserEventHandler() {
                    private Counter counter;

                    @Override
                    public boolean handles(EventType eventType) {
                        return EventType.LOGIN == eventType;
                    }

                    @Override
                    public Counter createCounter() {
                        this.counter = Counter.build()
                            .name("keycloak_logins")
                            .help("Total successful logins")
                            .labelNames("realm", "provider")
                            .register();
                        return counter;
                    }

                    @Override
                    public void handleEvent(Event event) {
                        final String provider = event.getDetails()
                            .getOrDefault("identity_provider", PROVIDER_KEYCLOAK_OPENID);

                        counter.labels(event.getRealmId(), provider).inc();
                    }
                });

            // REGISTER handler
            this.handlers.add(
                new UserEventHandler() {

                    private Counter counter;

                    @Override
                    public boolean handles(EventType eventType) {
                        return EventType.REGISTER == eventType;
                    }

                    @Override
                    public Counter createCounter() {
                        counter = Counter.build()
                            .name("keycloak_registrations")
                            .help("Total registered users")
                            .labelNames("realm", "provider")
                            .register();

                        return counter;
                    }

                    @Override
                    public void handleEvent(Event event) {
                        final String provider = event.getDetails()
                            .getOrDefault("identity_provider", PROVIDER_KEYCLOAK_OPENID);

                        counter.labels(event.getRealmId(), provider).inc();
                    }
                });

            // LOGIN_ERROR handler
            this.handlers.add(
                new UserEventHandler() {

                    private Counter counter;

                    @Override
                    public boolean handles(EventType eventType) {
                        return EventType.LOGIN_ERROR == eventType;
                    }

                    @Override
                    public Counter createCounter() {
                        counter = Counter.build()
                            .name("keycloak_failed_login_attempts")
                            .help("Total failed login attempts")
                            .labelNames("realm", "provider", "error")
                            .register();

                        return counter;
                    }

                    @Override
                    public void handleEvent(Event event) {
                        final String provider = event.getDetails()
                            .getOrDefault("identity_provider", PROVIDER_KEYCLOAK_OPENID);

                        counter.labels(event.getRealmId(), provider, event.getError()).inc();
                    }
                });
        }

        private Counter createCounter(EventType eventType) {
            for (UserEventHandler handler : handlers) {
                if (handler.handles(eventType)) {
                    return handler.createCounter();
                }
            }

            return null;
        }

        private boolean handleEvent(Event event) {
            for (UserEventHandler handler : handlers) {
                if (handler.handles(event.getType())) {
                    handler.handleEvent(event);
                    return true;
                }
            }

            return false;
        }
    }

    private interface UserEventHandler {
        boolean handles(EventType eventType);

        Counter createCounter();

        void handleEvent(Event event);
    }

}
