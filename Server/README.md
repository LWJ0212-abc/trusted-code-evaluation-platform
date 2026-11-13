# Server Development Dependencies

The Compose stack starts Keycloak, its dedicated MySQL database, the separate
business MySQL database, and RustFS for S3-compatible source/result objects.
The Spring Boot Server remains a host process so it can be run and debugged
from IntelliJ IDEA or Maven. This is a local development setup, not a
production deployment template.

## Configure

Copy the environment template and replace every password placeholder:

```powershell
Copy-Item .env.example .env
```

To reuse locally available images, set `KEYCLOAK_IMAGE`, `MYSQL_IMAGE`, and
`RUSTFS_IMAGE` in `.env` to exact tags shown by `docker image ls`. Compose uses
a matching local tag first and pulls only when it is missing.

Default image index digests resolved on 2026-11-09:

| Image | Tag | Index digest |
|---|---|---|
| `quay.io/keycloak/keycloak` | `26.4.0` | `sha256:5f3fb534cde6bf006d79f5912473e5d2c828c707cdfc52e16972803aca9d43dd` |
| `mysql` | `8.4` | `sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902` |

## Start Dependencies

From this directory:

```powershell
docker compose --env-file .env up -d
docker compose ps
docker compose logs -f keycloak
```

Keycloak is available at `http://127.0.0.1:8765`. The Keycloak database is
available on `127.0.0.1:3308`; the separate business database is on
`127.0.0.1:3309`. RustFS exposes its S3 API at `http://127.0.0.1:19000` and
its development console at `http://127.0.0.1:19001`.

Create the configured source and result buckets before running submission
integration tests. The application uses the business database and RustFS;
it never writes business tables into the Keycloak schema.

Start the Java Server separately from IntelliJ or run `mvnw.cmd spring-boot:run`.

## Stop and Reset

Stop the containers without deleting data:

```powershell
docker compose down
```

To reset only this development Keycloak database, stop the stack and remove
the named volume explicitly:

```powershell
docker compose down
docker volume rm tcep_keycloak_mysql_data
```

Removing that volume permanently deletes local Keycloak data. It does not
remove unrelated Docker volumes.
