[![License](https://img.shields.io/:license-Apache2-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# Keycloak Metrics SPI

A [Service Provider](https://www.keycloak.org/docs/latest/server_development/index.html#_providers) that adds a metrics endpoint to Keycloak. The endpoint returns metrics data ready to be scraped by [Prometheus](https://prometheus.io/).

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

There are two ways to build the project using:
 * [Gradle](https://gradle.org/)
 * [Maven](https://maven.apache.org/)

You can choose between the tools the most convenient for you. Read further how to use each of them.

### Gradle

The project is packaged as a jar file and bundles the prometheus client libraries.

```sh
$ ./gradlew jar
```

builds the jar and writes it to _build/libs_.

### Maven

To build the jar file using maven run the following command (will bundle the prometheus client libraries as well):

```sh
mvn package
```

It will build the project and write jar to the _./target_.

### Configurable versions for some packages

You can build the project using a different version of Keycloak or Prometheus, running the command:

#### For Gradle

```sh
$ ./gradlew -PkeycloakVersion="15.0.2.Final" -PprometheusVersion="0.12.0" jar
```

or by changing the `gradle.properties` file in the root of the project.

#### For Maven

```sh
mvn clean package -Dkeycloak.version=15.0.0 -Dprometheus.version=0.9.0
```

## Install and setup

### On Keycloak Widfly Distribution
> This section assumes `/opt/jboss` as the Keycloak home directory, which is used on the _jboss/keycloak_ reference container on Docker Hub.

- Drop the [jar](https://github.com/aerogear/keycloak-metrics-spi/releases/latest) into the _/opt/jboss/keycloak/standalone/deployments/_ subdirectory of your Keycloak installation.

- Touch a dodeploy file into the _/opt/jboss/keycloak/standalone/deployments/_ subdirectory of your Keycloak installation.

```bash
# If your jar file is `keycloak-metrics-spi-2.0.2.jar`
cd /opt/jboss/keycloak/standalone/deployments/
touch keycloak-metrics-spi-2.0.2.jar.dodeploy
```
- Restart the keycloak service.

### On Keycloak Quarkus Distribution

> We assume the home of keycloak is on the default `/opt/keycloak`

You will need to either copy the `jar` into the build step and run step, or copy it from the build stage. Following the [example docker instructions](https://www.keycloak.org/server/containers)
No need to add `.dodeploy`.

```
# On build stage
COPY keycloak-metrics-spi.jar /opt/keycloak/providers/

# On run stage
COPY keycloak-metrics-spi.jar /opt/keycloak/providers/

```
If not copied to both stages keycloak will complain 
```
ERROR: Failed to start quarkus
ERROR: Failed to open /opt/keycloak/lib/../providers/keycloak-metrics-spi.jar
```
The endpoint for the metrics is `<url>/<http_relative_path>/realms/<realm>/metrics`

### Enable metrics-listener event

- To enable the event listener via the GUI interface, go to _Manage -> Events -> Config_. The _Event Listeners_ configuration should have an entry named `metrics-listener`.

- To enable the event listener via the Keycloak CLI, such as when building a Docker container, use these commands.

```c
$ /opt/jboss/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user $KEYCLOAK_USER --password $KEYCLOAK_PASSWORD
$ /opt/jboss/keycloak/bin/kcadm.sh update events/config -s "eventsEnabled=true" -s "adminEventsEnabled=true" -s "eventsListeners+=metrics-listener"
$ usr/bin/rm -f /opt/jboss/.keycloak/kcadm.config
```
    
### PushGateway

If you are running keycloak in a cluster or if you are running behind a load balancer, you might have problems scraping
the metrics endpoint of each node. To fix this, you can push your metrics to a PushGateway. 

[Prometheus PushGateway](https://github.com/prometheus/pushgateway)

You can enable pushing to PushGateway by setting the environment variable ```PROMETHEUS_PUSHGATEWAY_ADDRESS``` in the keycloak
instance. The format is host:port or ip:port of the Pushgateway.

#### **Grouping instances**
The default value for the grouping key "instance" is the IP. This can be changed setting the environment variable ```PROMETHEUS_GROUPING_KEY_INSTANCE```
to a fixed value. Additionaly, if the value provided starts with the prefix ```ENVVALUE:```,
the string after the ```:``` will be used to get the value from the environment variable with that name.
For example, with the next setting:
```
PROMETHEUS_GROUPING_KEY_INSTANCE=ENVVALUE:HOSTNAME
```
```instance``` will have the value of the environment variable ```HOSTNAME```

#### **Grouping instances by cluster**
if you have multiple KeyCloak clusters on the same runtime then you might like to groups instances by cluster's name.

That's the purpose of the environment variable ```PROMETHEUS_PUSHGATEWAY_JOB```.
The default *job* value is *keycloak* for all the instances.

For example for all the instances of a KeyCloak cluster #1 you can set:
```c
PROMETHEUS_PUSHGATEWAY_JOB="keycloak-cluster1"
```
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

##### keycloak_login_attempts

This counter counts every login attempt performed by a non-admin user. It also distinguishes logins by the utilised
identity provider by means of the label **provider** and by client with the label **client_id**..

```c
# HELP keycloak_login_attempts Total number of login attempts
# TYPE keycloak_login_attempts counter
keycloak_login_attempts{realm="test",provider="keycloak",client_id="account"} 3.0
keycloak_login_attempts{realm="test",provider="github",client_id="application1"} 2.0
```

##### keycloak_logins
This counter counts every login performed by a non-admin user. It also distinguishes logins by the utilised identity provider by means of the label **provider** and by client with the label **client_id**..

```c
# HELP keycloak_logins Total successful logins
# TYPE keycloak_logins counter
keycloak_logins{realm="test",provider="keycloak",client_id="account"} 3.0
keycloak_logins{realm="test",provider="github",client_id="application1"} 2.0
```

##### keycloak_failed_login_attempts
This counter counts every login performed by a non-admin user that fails, being the error described by the label **error**. It also distinguishes logins by the identity provider used by means of the label **provider** and by client with the label **client_id**.

```c
# HELP keycloak_failed_login_attempts Total failed login attempts
# TYPE keycloak_failed_login_attempts counter
keycloak_failed_login_attempts{realm="test",provider="keycloak",error="invalid_user_credentials",client_id="application1"} 6.0
keycloak_failed_login_attempts{realm="test",provider="keycloak",error="user_not_found",client_id="application1"} 2.0
```

##### keycloak_client_logins
This counter counts every client login.  

```c
# HELP keycloak_client_logins Total successful client logins
# TYPE keycloak_client_logins counter
keycloak_client_logins{realm="test",provider="keycloak",client_id="account"} 4.0
keycloak_client_logins{realm="test",provider="github",client_id="application2"} 7.0
```

##### keycloak_failed_client_login_attempts
This counter counts every client login performed that fails, being the error described by the label **error**.  
```c
# HELP keycloak_failed_client_login_attempts Total failed client login attempts
# TYPE keycloak_failed_client_login_attempts counter
keycloak_failed_client_login_attempts{realm="test2",provider="keycloak",error="invalid_client_credentials",client_id="application2"} 5.0
keycloak_failed_client_login_attempts{realm="test2",provider="keycloak",error="client_not_found",client_id="application2"} 3.0
```

##### keycloak_refresh_tokens
This counter counts every refresh token.

```c
# HELP keycloak_refresh_tokens Total number of successful token refreshes
# TYPE keycloak_refresh_tokens counter
keycloak_refresh_tokens{realm="test3",provider="keycloak",client_id="account"} 1.0
keycloak_refresh_tokens{realm="test3",provider="github",client_id="application3"} 2.0
```

##### keycloak_refresh_tokens_errors
This counter counts every refresh token that fails.

```c
# HELP keycloak_refresh_tokens_errors Total number of failed token refreshes
# TYPE keycloak_refresh_tokens_errors counter
keycloak_refresh_tokens_errors{realm="test3",provider="keycloak",error="invalid_token",client_id="application3"} 3.0
```

##### keycloak_registrations
This counter counts every new user registration. It also distinguishes registrations by the identity provider used by means of the label **provider** and by client with the label **client_id**..

```c
# HELP keycloak_registrations Total registered users
# TYPE keycloak_registrations counter
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

##### keycloak_code_to_tokens
This counter counts every code to token.

```c
# HELP keycloak_code_to_tokens Total number of successful code to token
# TYPE keycloak_code_to_tokens counter
keycloak_code_to_tokens{realm="test4",provider="keycloak",client_id="account"} 3.0
keycloak_code_to_tokens{realm="test4",provider="github",client_id="application4"} 1.0
```

##### keycloak_code_to_tokens_errors
This counter counts every code to token performed that fails, being the error described by the label **error**. 

```c
# HELP keycloak_code_to_tokens_errors Total number of failed code to token
# TYPE keycloak_code_to_tokens_errors counter
keycloak_code_to_tokens_errors{realm="test4",provider="keycloak",error="invalid_client_credentials",client_id="application4"} 7.0
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

#### Metrics URI
The URI can be added to the metrics by setting the environment variable ```URI_METRICS_ENABLED``` to `true`. 
This will output a consolidated realm URI value to the metrics. The realm value is replaced with a generic `{realm}` value
```c
# HELP keycloak_request_duration Request duration
# TYPE keycloak_request_duration histogram
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="50.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="100.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="250.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="500.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="1000.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="2000.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="10000.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="30000.0",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/3p-cookies/step2.html",le="+Inf",} 2.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="50.0",} 0.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="100.0",} 0.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="250.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="500.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="1000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="2000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="10000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="30000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="admin/{realm}/console/whoami",le="+Inf",} 1.0
```

If the quanitiy of metrics is too high they can also be filtered to specific values using the ```URI_METRICS_FILTER``` e.g `token,clients`. 
This is a comman delimited value of keywords to search and display the required URIs. 

```c
# HELP keycloak_request_duration Request duration
# TYPE keycloak_request_duration histogram
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="50.0",} 0.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="100.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="250.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="500.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="1000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="2000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="10000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="30000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/{realm}/protocol/openid-connect/token",le="+Inf",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="50.0",} 4.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="100.0",} 5.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="250.0",} 6.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="500.0",} 6.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="1000.0",} 6.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="2000.0",} 6.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="10000.0",} 6.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="30000.0",} 6.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/master/console",uri="",le="+Inf",} 6.0
keycloak_request_duration_count{code="200",method="GET",resource="admin,admin/master/console",uri="",} 6.0
keycloak_request_duration_sum{code="200",method="GET",resource="admin,admin/master/console",uri="",} 274.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="50.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="100.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="250.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="500.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="1000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="2000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="10000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="30000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="+Inf",} 1.0
keycloak_request_duration_count{code="200",method="GET",resource="admin,admin/serverinfo",uri="",} 1.0
```

To remove the consolidated realm URI, set ```URI_METRICS_DETAILED``` to `true`

```c
# HELP keycloak_request_duration Request duration
# TYPE keycloak_request_duration histogram
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="50.0",} 0.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="100.0",} 0.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="250.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="500.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="1000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="2000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="10000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="30000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="POST",resource="realms,realms/master/protocol/openid-connect",uri="realms/master/protocol/openid-connect/token",le="+Inf",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="50.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="100.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="250.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="500.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="1000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="2000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="10000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="30000.0",} 1.0
keycloak_request_duration_bucket{code="200",method="GET",resource="admin,admin/serverinfo",uri="",le="+Inf",} 1.0
keycloak_request_duration_count{code="200",method="GET",resource="admin,admin/serverinfo",uri="",} 1.0
keycloak_request_duration_sum{code="200",method="GET",resource="admin,admin/serverinfo",uri="",} 19.0
```

## External Access

To disable metrics being externally accessible to a cluster. Set the environment variable 'DISABLE_EXTERNAL_ACCESS'. Once set enable the header 'X-Forwarded-Host' on your proxy. This is enabled by default on HA Proxy on Openshift.

## Grafana Dashboard

You can use this dashboard or create yours https://grafana.com/dashboards/10441
