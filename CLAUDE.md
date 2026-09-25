# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A two-store starter for Gigahack 26. A Spring Boot API writes each pitch to
PostgreSQL and MongoDB and reads them back merged; a static SvelteKit SPA serves
the board. Jenkins builds both images and deploys four containers on the host's
`web` Docker network behind Traefik.

## Commands

Local stack, one command, no database setup:

```sh
docker compose up --build          # board on :3000, API on :8080
docker compose down                # keep data
docker compose down -v             # drop the volumes too
```

Backend (from `backend/`):

```sh
./mvnw test                                    # 13 tests
./mvnw test -Dtest=SubmissionServiceTest       # one class
./mvnw test -Dtest=SubmissionServiceTest#createWritesTheRowToPostgresAndTheDocumentToMongo
./mvnw spring-boot:run                         # needs Postgres and Mongo reachable
```

Frontend (from `frontend/`):

```sh
npm run check                      # svelte-check, must be 0 errors
npm run build                      # static site into build/
PUBLIC_API_URL=http://localhost:8080 npm run build
npm run dev                        # :5173
```

Deploy: push to `main`. The GitHub webhook triggers `Gigahack26/gigahack26` on
`jen.pistonpunk.com`, which runs the root `Jenkinsfile` (~30s warm).

## Architecture

**Two stores, one write path.** `SubmissionService.create` saves a `Submission`
row through JPA, then a `SubmissionDetail` document through Spring Data MongoDB,
keyed by the Postgres id. `list()` reads the rows, then fetches the matching
documents in one `findBySubmissionIdIn` call and merges them into
`SubmissionResponse`. A pitch is only complete when both writes land; there is no
distributed transaction and no reconciliation. `GET /api/status` is what tells you
whether each store is actually reachable.

**The API is the only thing that talks to a database.** The browser never sees
Postgres or Mongo; the SPA calls `/api/**` cross-origin. That makes
`CorsConfig` load-bearing — it is pinned by `CorsConfigTest`, and the allowed
origins come from `CORS_ALLOWED_ORIGINS` (the pipeline sets it to the board's
origin, compose sets it to both local ports).

**Config is environment-first.** Every value in
`backend/src/main/resources/application.yml` is a `${ENV:default}` placeholder, so
the same image runs locally and in production. The frontend has no runtime
config at all: Vite inlines `PUBLIC_API_URL` into the bundle at build time
(`__API_URL__` in `vite.config.ts`), which is why the Jenkinsfile passes it as a
build arg and why changing the API host needs a rebuild, not a restart.

**Deploy shape.** The Jenkinsfile builds `gigahack26-api` and `gigahack26-ui`,
recreates the Postgres and Mongo containers and waits for each to accept
connections, then recreates the two app containers with Traefik labels for
`hackapi.pistonpunk.com` and `hack.pistonpunk.com`. Secrets come from
folder-scoped Jenkins credentials, never from the repo.

## Every change has to hold the infrastructure together

This is the standing rule for this repo: **a change is not finished when the
feature works — it is finished when both the deployed path and the local path
still work.** The two are wired separately, so touching one and not the other is
the default failure mode.

When a change introduces or alters any of these, update all of the places that
know about it:

| Change | Update |
| --- | --- |
| New environment variable | `application.yml` placeholder, `Jenkinsfile` `docker run -e`, `docker-compose.yml`, `.env.example`, README table |
| New backing service | `docker-compose.yml` (with a healthcheck), `Jenkinsfile` deploy stage that waits for readiness, the `web` network, README |
| New API route or origin | `CorsConfig` mapping if outside `/api/**`, `CorsConfigTest`, the frontend client in `src/lib/api.ts` |
| New build-time value for the SPA | `vite.config.ts` define, `frontend/Dockerfile` build arg, `Jenkinsfile` `--build-arg` |
| New port or domain | Traefik labels in the `Jenkinsfile`, `docker-compose.yml`, README |
| New backend dependency | `pom.xml`, and check the Dockerfile's build stage still resolves it |

Verify with the gates CI runs, not just the one you touched: `./mvnw test` and
`npm run check`, then `docker compose up --build` for the local path, then a push
for the deployed path and a real request against the live host. The pipeline no
longer has a post-deploy check, so **a green build does not mean the app works** —
`hackapi.pistonpunk.com/api/status` must report both stores `up`, and a POST must
land.

## Gotchas that have already cost time

- **Spring Boot 4 moved the Mongo keys.** `spring.data.mongodb.*` is deprecated at
  *error* level and no longer bound; connection settings live under
  `spring.mongodb.*` (`uri`, `representation.uuid`, host/port/credentials).
  `spring.data.mongodb.auto-index-creation` kept the old prefix. A stale key is
  ignored silently — the driver falls back to `localhost:27017` and the app
  crash-loops behind a 502. Check new keys against
  `META-INF/spring-configuration-metadata.json` in the Boot jar before assuming a
  property name is live.
- **`spring.mongodb.representation.uuid: standard` is required.** Driver 5.8
  refuses to encode a `UUID` field without it, so writes fail with
  `CodecConfigurationException` while reads keep working.
- **MongoDB is pinned to 7.0.** 8.0–8.3.8 exit during startup on Linux kernel
  6.19–7.0.13 (TCMalloc vs the `rseq` ABI, SERVER-121912), and this host is in
  that range. Do not bump the tag without checking the kernel.
- The board is a client-rendered SPA: nginx falls back to `index.html` for unknown
  paths, so new routes need no server config, but anything that must be
  server-rendered needs `ssr` re-enabled in `src/routes/+layout.ts`.

## Conventions

- No comments in code. Names and structure carry the meaning.
- Commits: imperative subject, a body that says what broke or why, no
  `Co-Authored-By` trailer.
- Design tokens for the board live in `frontend/src/app.css`; keep to the charcoal
  and single-lime system and the `Inter` / `IBM Plex Mono` pairing.
