# Local PostgreSQL with Docker + DBeaver

This guide walks through running a PostgreSQL database locally in Docker and connecting to it from DBeaver, so you can browse tables, run queries, and inspect data while developing.

## Prerequisites

- **Docker Desktop** installed and running — https://www.docker.com/products/docker-desktop/
- **DBeaver Community Edition** installed — https://dbeaver.io/download/
- Port `5432` free on your machine (or pick a different host port — see Troubleshooting)

---

## Option A — Quick start with `docker run`

Run this single command to start Postgres 16 with a named volume so your data survives container restarts:

```bash
docker run -d \
  --name local-postgres \
  -e POSTGRES_DB=appdb \
  -e POSTGRES_USER=app \
  -e POSTGRES_PASSWORD=app \
  -p 5432:5432 \
  -v local-postgres-data:/var/lib/postgresql/data \
  postgres:16-alpine
```

Verify it's running:

```bash
docker ps --filter name=local-postgres
docker logs local-postgres
```

You should see `database system is ready to accept connections` in the logs.

---

## Option B — `docker-compose.yml`

Easier to start/stop and to extend later. Create a file called `docker-compose.yml` in your project root with:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: local-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: appdb
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d appdb"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

Start it:

```bash
docker compose up -d
docker compose ps          # should show "healthy"
docker compose logs -f postgres
```

> **Tip:** For real projects, move the credentials into a `.env` file (e.g. `POSTGRES_PASSWORD=...`) and reference them with `${POSTGRES_PASSWORD}` in the compose file. Add `.env` to `.gitignore`.

---

## Connecting from DBeaver

1. Open DBeaver.
2. **Database → New Database Connection** (or click the plug icon in the top-left).
3. Select **PostgreSQL** and click **Next**.
4. Fill in the connection settings:
   - **Host:** `localhost`
   - **Port:** `5432`
   - **Database:** `appdb`
   - **Username:** `app`
   - **Password:** `app` (tick "Save password" for local dev)
5. Click **Test Connection…**
   - If DBeaver prompts to download the PostgreSQL JDBC driver, click **Download** — it's a one-time setup.
   - You should see "Connected (xxx ms)".
6. Click **Finish**.

The new connection appears in the **Database Navigator** on the left. Expand it to see schemas → `public` → Tables, etc. Right-click the database for SQL Editor or to create tables visually.

---

## Common commands

| Action | `docker run` version | `docker compose` version |
|---|---|---|
| Start | `docker start local-postgres` | `docker compose up -d` |
| Stop | `docker stop local-postgres` | `docker compose stop` |
| Tail logs | `docker logs -f local-postgres` | `docker compose logs -f postgres` |
| Open psql shell | `docker exec -it local-postgres psql -U app -d appdb` | `docker compose exec postgres psql -U app -d appdb` |
| Remove container (keep data) | `docker rm -f local-postgres` | `docker compose down` |
| Remove container **and wipe data** | `docker rm -f local-postgres && docker volume rm local-postgres-data` | `docker compose down -v` |

Inside `psql`, useful meta-commands: `\l` (list databases), `\dt` (list tables), `\d <table>` (describe), `\q` (quit).

---

## Troubleshooting

**Port 5432 is already in use** — you likely have another Postgres running (Homebrew, another container). Either stop it, or expose a different host port: change `-p 5432:5432` to `-p 5433:5432` (or `"5433:5432"` in compose), then connect DBeaver to port `5433`.

**Container exits immediately** — check `docker logs local-postgres`. Most common cause: the named volume holds data from a previous run with a different password. Fix by wiping the volume (see "Remove container and wipe data" above) — only do this in local dev.

**DBeaver: "FATAL: password authentication failed"** — the volume already has a database initialized with different credentials. Postgres only honors `POSTGRES_*` env vars on **first** startup. Wipe the volume to reinitialize.

**DBeaver: "Connection refused"** — container isn't running, or healthcheck hasn't finished yet. Run `docker ps` to confirm, wait a few seconds, retry.

**Reset everything** — `docker compose down -v` (compose) or remove the container and volume manually. Next `up` starts from a clean database.
