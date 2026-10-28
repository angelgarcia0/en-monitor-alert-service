# en-monitor-alert-service

EnergyMonitor alert microservice: raises the threshold, consumption-limit, connectivity and
linked-device alerts of a home from the readings and the device events, lets the members of the
home list, read and delete them, and announces each new alert to the notification service. Spring
Boot 4, Java 21, hexagonal architecture.

It is being extracted from the `alert` module of `energy-monitor-backend`, following the same steps
as the security, device, measurement, home and notification services.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL from the `infra` repository: the schema is shared with `energy-monitor-backend` and the
  security, device, measurement, home and notification services.

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env in both files
./mvnw spring-boot:run          # http://localhost:8088/actuator/health
```

On Windows PowerShell use `.\mvnw.cmd` instead of `./mvnw`.

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`.
