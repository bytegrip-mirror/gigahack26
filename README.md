# Gigahack 26

Starter monorepo for Gigahack 26: a Spring Boot API over PostgreSQL and MongoDB,
a static SvelteKit front end, and a Jenkins pipeline that deploys both to
`hack.pistonpunk.com`.

The wired-up feature is a pitch wall: anyone posts what they are building, the
API writes the row to PostgreSQL and the long-form detail to MongoDB, and the
board reads both back merged.

## Layout

| Path | What it is |
| --- | --- |
| `backend/` | Spring Boot 4.1 API, Java 21, Maven |
| `frontend/` | SvelteKit 2 + Svelte 5, static SPA build served by nginx |
| `Jenkinsfile` | Build, test, and deploy pipeline |
| `docker-compose.yml` | Local stack: postgres, mongo, api, web |

## API

| Route | Purpose |
| --- | --- |
| `GET /api/submissions` | Every pitch, newest first, relational row merged with its document |
| `POST /api/submissions` | Create a pitch: `{"author","title","body","tags":[]}` |
| `GET /api/status` | Service name plus live `up`/`down` probes for PostgreSQL and MongoDB |
| `GET /actuator/health` | Container health for orchestrators |

PostgreSQL holds `submissions` (id, author, title, created_at) through JPA.
MongoDB holds `submission_details` (submissionId, body, tags, createdAt), joined
in `SubmissionService`. A pitch is only complete when both writes land.

## Local development

Whole stack in containers:

```sh
docker compose up --build
```

Then `http://localhost:3000` for the board and `http://localhost:8080/api/status`
for the API.

The credentials in `docker-compose.yml` are throwaway defaults for local work
only, and both databases are published on loopback. Set `POSTGRES_PASSWORD` and
`MONGO_PASSWORD` to override them. Nothing in the pipeline reads this file: the
deployed stack gets a fresh random password per store from the Jenkins
credential store.

Running the two halves directly, with `docker compose up postgres mongo` for the
datastores:

```sh
cd backend && ./mvnw spring-boot:run     # :8080
cd frontend && npm install && npm run dev  # :5173
```

## Configuration

The API reads everything from the environment, with local defaults:

| Variable | Default | Used for |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP port |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/gigahack26` | JDBC URL |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `gigahack` / `gigahack` | JDBC credentials |
| `MONGO_URI` | `mongodb://localhost:27017/gigahack26` | Mongo connection string |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins allowed on `/api/**` |

The front end bakes its API base into the bundle at build time. Vite reads
`PUBLIC_API_URL` and inlines it, defaulting to `http://localhost:8080`, so
`npm run build` needs no environment and the pipeline passes the real one:

```sh
PUBLIC_API_URL=https://hackapi.pistonpunk.com npm run build
```

## Deployment

Jenkins job `Gigahack26/gigahack26` on `jen.pistonpunk.com` runs the `Jenkinsfile`
from `main`. On every push it:

1. runs `mvn test` (13 tests) and `svelte-check` in throwaway containers,
2. builds the `gigahack26-api` and `gigahack26-ui` images,
3. recreates the `gigahack26-db` (postgres:16-alpine) and `gigahack26-mongo`
   (mongo:7.0) containers on the `web` network, waiting for each to accept
   connections,
4. recreates the API and UI containers with Traefik labels for
   `hackapi.pistonpunk.com` and `hack.pistonpunk.com`,
5. fails the build unless `/api/status` reports both stores `up` and the UI
   serves its app shell.

Folder-scoped credentials the job expects: `gigahack26-ssh` (checkout),
`gigahack26-db-password`, `gigahack26-mongo-password`.

MongoDB is pinned to 7.0 rather than 8.x on purpose. MongoDB 8.0 through 8.3.8
refuse to start on Linux kernel 6.19–7.0.13 — TCMalloc violating the `rseq` ABI
([SERVER-121912](https://jira.mongodb.org/browse/SERVER-121912)) — and the host
here is affected. 7.0 is the only line that starts on every kernel.

CORS is pinned to `https://hack.pistonpunk.com` in production, so the browser
calls from the board to the API cross-origin and only from that origin.
