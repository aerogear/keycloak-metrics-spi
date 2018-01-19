package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
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
  private final static PrometheusExporter INSTANCE = new PrometheusExporter();
  private final static String USER_EVENT_PREFIX = "kc_user_event_";
  private final static String ADMIN_EVENT_PREFIX = "kc_admin_event_";

  private final static CollectorRegistry registry = CollectorRegistry.defaultRegistry;
  public final static Map<String, Counter> counters = new HashMap<>();

  // We want to keep track of the logged in users separately
  public final static Gauge loggedInUsers = Gauge.build()
    .name("kc_logged_in_users")
    .help("Currently logged in users")
    .labelNames("realm")
    .register();

  static {
    // Counters for all user events
    for (EventType type : EventType.values()) {
      final String eventName = USER_EVENT_PREFIX + type.name();
      counters.put(eventName, createCounter(eventName, false));
    }

    // Counters for all admin events
    for (OperationType type : OperationType.values()) {
      final String eventName = ADMIN_EVENT_PREFIX + type.name();
      counters.put(eventName, createCounter(eventName, true));
    }
  }

  private PrometheusExporter() {
    // The metrics collector needs to be a singleton because requiring a
    // provider from the KeyCloak session (session#getProvider) will always
    // create a new instance. Not sure if this is a bug in the SPI implementation
    // or intentional but better to avoid this. The metrics object is single-instance
    // anyway and all the Gauges are suggested to be static (it does not really make
    // sense to record the same metric in multiple places)

    // Initialize the default metrics for the hotspot VM
    DefaultExports.initialize();
  }

  public static PrometheusExporter instance() {
    return INSTANCE;
  }

  // Creates a counter based on a event name
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
  public void recordUserEvent(final Event event) {
    final String eventName = USER_EVENT_PREFIX + event.getType().name();
    counters.get(eventName).labels(event.getRealmId()).inc();
  }

  /**
   * Count generic admin event
   *
   * @param event Admin event
   */
  public void recordAdminEvent(final AdminEvent event) {
    final String eventName = ADMIN_EVENT_PREFIX + event.getOperationType().name();
    counters.get(eventName).labels(event.getRealmId(), event.getResourceType().name()).inc();
  }

  /**
   * Increase the number of currently logged in users
   *
   * @param event Login or Impersonate event
   */
  public void recordUserLogin(final Event event) {
    loggedInUsers.labels(event.getRealmId()).inc();
  }

  /**
   * Decrease the number of currently logged in users
   *
   * @param event Logout event
   */
  public void recordUserLogout(final Event event) {
    loggedInUsers.labels(event.getRealmId()).dec();
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
    TextFormat.write004(writer, registry.metricFamilySamples());
    writer.flush();
  }
}
