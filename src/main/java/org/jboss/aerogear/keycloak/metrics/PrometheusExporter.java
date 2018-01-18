package org.jboss.aerogear.keycloak.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;

import java.io.*;

public class PrometheusExporter {
  private final static PrometheusExporter INSTANCE = new PrometheusExporter();

  private final static CollectorRegistry registry;
  static {
    registry = CollectorRegistry.defaultRegistry;
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

  public void export(OutputStream stream) throws IOException {
    Writer writer = new BufferedWriter(new OutputStreamWriter(stream));
    TextFormat.write004(writer, registry.metricFamilySamples());
    writer.flush();
  }
}
