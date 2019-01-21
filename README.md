[![License](https://img.shields.io/:license-Apache2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# KeyCloak Metrics SPI

A [Service Provider](http://www.keycloak.org/docs/3.0/server_development/topics/providers.html) that adds a metrics endpoint to KeyCloak. The endpoint returns metrics data ready to be scraped by [Prometheus](https://prometheus.io/).

Two distinct providers are defined:

* MetricsEventListener to record the internal KeyCloak events
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

## Usage

Just drop the jar into the _providers_ subdirectory of your KeyCloak installation.

To enable the event listener via the GUI interface, go to _Manage -> Events -> Config_. The _Event Listeners_ configuration should have an entry named `metrics-listener`.

To enable the event listener via the KeyCloak CLI, such as when building a Docker container, use these commands. (These commands assume /opt/jboss is the KeyCloak home directory, which is used on the _jboss/keycloak_ reference container on Docker Hub.)

    /opt/jboss/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user $KEYCLOAK_USER --password $KEYCLOAK_PASSWORD
    /opt/jboss/keycloak/bin/kcadm.sh update events/config -s "eventsEnabled=true" -s "adminEventsEnabled=true" -s "eventsListeners+=metrics-listener"
    /usr/bin/rm -f /opt/jboss/.keycloak/kcadm.config

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
Every single internal Keycloak event is being shared through the endpoint, with the descriptions `Generic KeyCloak User event` or `Generic KeyCloak Admin event`. Most of these events are not likely useful for the majority users but are provided for good measure. A complete list of the events can be found at [Keycloak documentation](http://www.keycloak.org/docs-api/3.2/javadocs/org/keycloak/events/EventType.html).

### Featured events
There are however a few events that are particularly more useful from a mobile app perspective. These events have been overriden by the SPI and are described more thoroughly below.

##### keycloak_logins
This counter counts every login performed by a non-admin user. It also distinguishes logins by the utilised identity provider by means of the label **provider**.

```c
# HELP keycloak_logins Total successful logins
# TYPE keycloak_logins gauge
keycloak_logins{realm="test",provider="keycloak",} 3.0
keycloak_logins{realm="test",provider="github",} 2.0
```

##### keycloak_failed_login_attempts
This counter counts every login performed by a non-admin user that fails, being the error described by the label **error**. It also distinguishes logins by the identity provider used by means of the label **provider**.

```c
# HELP keycloak_failed_login_attempts Total failed login attempts
# TYPE keycloak_failed_login_attempts gauge
keycloak_failed_login_attempts{realm="test",provider="keycloak",error="invalid_user_credentials"} 6.0
keycloak_failed_login_attempts{realm="test",provider="keycloak",error="user_not_found"} 2.0
```

##### keycloak_registrations
This counter counts every new user registration. It also distinguishes registrations by the identity provider used by means of the label **provider**.

```c
# HELP keycloak_registrations Total registered users
# TYPE keycloak_registrations gauge
keycloak_registrations{realm="test",provider="keycloak",} 1.0
keycloak_registrations{realm="test",provider="github",} 1.0
```
