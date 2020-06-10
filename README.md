[![License](https://img.shields.io/:license-Apache2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# Keycloak Metrics SPI

A [Service Provider](https://www.keycloak.org/docs/4.8/server_development/index.html#_providers) that adds a metrics endpoint to Keycloak. The endpoint returns metrics data ready to be scraped by [Prometheus](https://prometheus.io/).

Two distinct providers are defined:

* MetricsEventListener to record the internal Keycloak events
* MetricsEndpoint to expose the data through a custom endpoint

The endpoint lives under `<url>/auth/realms/<realm>/metrics`. It will return data for all realms, no matter which realm
you use in the URL (you can just default to `/auth/realms/master/metrics`).

## License 

 See [LICENSE file](./LICENSE)

## Running the tests

```sh
$ ./gradlew test
```

## Build

The project is packaged as a jar file and bundles the prometheus client libraries.

```sh
$ ./gradlew jar
```

builds the jar and writes it to _build/libs_.

### Configurable versions for some packages

You can build the project using a different version of Keycloak or Prometheus, running the command:

```sh
$ ./gradlew -PkeycloakVersion="4.7.0.Final" -PprometheusVersion="0.3.0" jar
```

or by changing the `gradle.properties` file in the root of the project.

## Usage

Just drop the jar into the _providers_ subdirectory of your Keycloak installation.

To enable the event listener via the GUI interface, go to _Manage -> Events -> Config_. The _Event Listeners_ configuration should have an entry named `metrics-listener`.

To enable the event listener via the Keycloak CLI, such as when building a Docker container, use these commands. (These commands assume /opt/jboss is the Keycloak home directory, which is used on the _jboss/keycloak_ reference container on Docker Hub.)

    /opt/jboss/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user $KEYCLOAK_USER --password $KEYCLOAK_PASSWORD
    /opt/jboss/keycloak/bin/kcadm.sh update events/config -s "eventsEnabled=true" -s "adminEventsEnabled=true" -s "eventsListeners+=metrics-listener"
    /usr/bin/rm -f /opt/jboss/.keycloak/kcadm.config
    
### PushGateway

If you are running keycloak in a cluster or if you are running behind a load balancer, you might have problems scraping
the metrics endpoint of each node. To fix this, you can push your metrics to a PushGateway. 

[Prometheus PushGateway](https://github.com/prometheus/pushgateway)

You can enable pushing to PushGateway by setting the environment variable ```PROMETHEUS_PUSHGATEWAY_ADDRESS``` in the keycloak
instance. The format is host:port or ip:port of the Pushgateway.

## Metrics

For each metric, the endpoint returns 2 or more lines of information:

* **# HELP**: A small description provided by the SPI.
* **# TYPE**: The type of metric, namely _counter_ and _gauge_. More info about types at [prometheus.io/docs](https://prometheus.io/docs/concepts/metric_types/).
* Provided there were any values, the last one recorded. If no value has been recorded yet, no more lines will be given.
* In case the same metric have different labels, there is a different line for each one. By default all metrics are labeled by realm. More info about labels at [prometheus.io/docs](https://prometheus.io/docs/practices/naming/).

Example:
```c
# HELP jvm_memory_bytes_committed Committed (bytes) of a given JVM memory area.
# TYPE jvm_memory_bytes_committed gauge
jvm_memory_bytes_committed{area="heap",} 2.00802304E8
jvm_memory_bytes_committed{area="nonheap",} 2.0217856E8
```

### JVM performance
A variety of JVM metrics are provided

### Generic events
Every single internal Keycloak event is being shared through the endpoint, with the descriptions `Generic Keycloak User event` or `Generic Keycloak Admin event`. Most of these events are not likely useful for the majority users but are provided for good measure. A complete list of the events can be found at [Keycloak documentation](https://www.keycloak.org/docs-api/4.8/javadocs/org/keycloak/events/EventType.html).

### Featured events
There are however a few events that are particularly more useful from a mobile app perspective. These events have been overriden by the SPI and are described more thoroughly below.

##### keycloak_logins
This counter counts every login performed by a non-admin user. It also distinguishes logins by the utilised identity provider by means of the label **provider** and by client with the label **client_id**..

```c
# HELP keycloak_logins Total successful logins
# TYPE keycloak_logins gauge
keycloak_logins{realm="test",provider="keycloak",client_id="account"} 3.0
keycloak_logins{realm="test",provider="github",client_id="application1"} 2.0
```

##### keycloak_failed_login_attempts
This counter counts every login performed by a non-admin user that fails, being the error described by the label **error**. It also distinguishes logins by the identity provider used by means of the label **provider** and by client with the label **client_id**.

```c
# HELP keycloak_failed_login_attempts Total failed login attempts
# TYPE keycloak_failed_login_attempts gauge
keycloak_failed_login_attempts{realm="test",provider="keycloak",error="invalid_user_credentials",client_id="application1"} 6.0
keycloak_failed_login_attempts{realm="test",provider="keycloak",error="user_not_found",client_id="application1"} 2.0
```

##### keycloak_registrations
This counter counts every new user registration. It also distinguishes registrations by the identity provider used by means of the label **provider** and by client with the label **client_id**..

```c
# HELP keycloak_registrations Total registered users
# TYPE keycloak_registrations gauge
keycloak_registrations{realm="test",provider="keycloak",client_id="application1"} 1.0
keycloak_registrations{realm="test",provider="github",client_id="application1"} 1.0
```

##### keycloak_registrations_errors
This counter counts every new user registration that fails, being the error described by the label **error**. It also distinguishes registrations by the identity provider used by means of the label **provider** and by client with the label **client_id**..

```c
# HELP keycloak_registrations_errors Total errors on registrations
# TYPE keycloak_registrations_errors counter
keycloak_registrations_errors{realm="test",provider="keycloak",error="invalid_registration",client_id="application1",} 2.0
keycloak_registrations_errors{realm="test",provider="keycloak",error="email_in_use",client_id="application1",} 3.0
```

##### keycloak_request_duration
This histogram records the response times per http method and puts them in one of nine buckets:

* Requests that take 50ms or less
* Requests that take 100ms or less
* Requests that take 250ms or less
* Requests that take 500ms or less
* Requests that take 1s or less
* Requests that take 2s or less
* Requests that take 10s or less
* Requests that take 30s or less
* Any request that takes longer than 30s

The response from this type of metrics has the following format:

```c
# HELP keycloak_request_duration Request duration
# TYPE keycloak_request_duration histogram
keycloak_request_duration_bucket{method="PUT",le="50.0",} 0.0
keycloak_request_duration_bucket{method="PUT",le="100.0",} 0.0
keycloak_request_duration_bucket{method="PUT",le="250.0",} 0.0
keycloak_request_duration_bucket{method="PUT",le="500.0",} 0.0
keycloak_request_duration_bucket{method="PUT",le="1000.0",} 1.0
keycloak_request_duration_bucket{method="PUT",le="2000.0",} 2.0
keycloak_request_duration_bucket{method="PUT",le="10000.0",} 2.0
keycloak_request_duration_bucket{method="PUT",le="30000.0",} 2.0
keycloak_request_duration_bucket{method="PUT",le="+Inf",} 2.0
keycloak_request_duration_count{method="PUT",} 2.0
keycloak_request_duration_sum{method="PUT",} 3083.0
```

This tells you that there have been zero requests that took less than 500ms. There was one request that took less than 1s. All the other requests took less than 2s.

Aside from the buckets there are also the `sum` and `count` metrics for every method. In the above example they tell you that there have been two requests total for this http method. The sum of all response times for this combination is 3083ms.

To get the average request duration over the last five minutes for the whole server you can use the following Prometheus query:

```c
rate(keycloak_request_duration_sum[5m]) / rate(keycloak_request_duration_count[5m])
```

##### keycloak_response_errors
This counter counts the number of response errors (responses where the http status code is in the 400 or 500 range).

```c
# HELP keycloak_response_errors Total number of error responses
# TYPE keycloak_response_errors counter
keycloak_response_errors{code="500",method="GET",} 1
```

## Grafana Dashboard

You can use this dashboard or create yours https://grafana.com/dashboards/10441
