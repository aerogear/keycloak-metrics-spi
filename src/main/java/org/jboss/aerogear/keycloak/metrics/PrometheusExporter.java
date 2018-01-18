package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import org.keycloak.events.Event;

import java.io.*;

public class PrometheusExporter {
  private final static PrometheusExporter INSTANCE = new PrometheusExporter();
  private final static Gauge loggedInUsers;
  private final static Counter failedLoginAttempts;

  private final static CollectorRegistry registry;

  static {
    registry = CollectorRegistry.defaultRegistry;

    // Gauge to record logged in users over time
    loggedInUsers = Gauge.build()
      .name("logged_in_users")
      .help("Logged in Users")
      .labelNames("realm")
      .register();

    failedLoginAttempts = Counter.build()
      .name("failed_login_attempts")
      .help("Failed login attempts")
      .labelNames("realm")
      .register();
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

  public void recordUserLogin(Event event) {
    loggedInUsers.labels(event.getRealmId()).inc();
  }

  public void recordUserLogout(Event event) {
    loggedInUsers.labels(event.getRealmId()).dec();
  }

  public void recordFailedLogin(Event event) {
    failedLoginAttempts.labels(event.getRealmId()).inc();
  }

  public void export(OutputStream stream) throws IOException {
    Writer writer = new BufferedWriter(new OutputStreamWriter(stream));
    TextFormat.write004(writer, registry.metricFamilySamples());
    writer.flush();
  }
}
