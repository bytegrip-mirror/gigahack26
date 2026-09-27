# What the API serves, and what it caches

The read path is thin on purpose: the expensive work happened at ingest, and
serving is looking things up.

## The two calls that matter

| Call | Returns |
|---|---|
| `GET /api/companies?serviceLine=…&filter=…` | the board — one row per company: score, band, gate reason, the top signals **with their spans** |
| `GET /api/evidence/{id}/capture` | the whole stored page, plus its length |

The second is the one that proves the product. Every claim on the board carries
an evidence id and a character range; opening it fetches the capture and
highlights that range. Nothing between the claim and the page except a
`substring`.

## Caching, and the one cache

There is exactly **one** Redis cache in the codebase: `"board"`, keyed on

```
serviceLine | filter | sort | query | stage | region
```

with a **60-second TTL**. `BoardRow` is `Serializable` precisely because it is
what goes in.

Sixty seconds is the right number here. It is long enough that a room full of
people clicking the same board does not re-score anything, and short enough that
a stale number cannot survive a demo.

### What evicts it

Recording a lead, pushing to a CRM, marking a competitor, changing an ICP
profile, changing a service line, changing the policy questions, changing
settings, re-scoring, and the scheduled refresh — all of them drop the whole
cache, because all of them change what the board would say.

### Two sharp edges

**Self-invocation does not cache.** `BoardService` has three `board(...)`
overloads, and the two shorter ones call the annotated one through `this`. Spring
proxies the bean, not the call, so those two never populate the cache. It still
works — they delegate to the cached method — but it is worth knowing when
reading the timings.

**The corpus reload does not evict.** `POST /api/admin/corpus/reload` re-reads
the data files but does not clear the board cache, so after a reload the board
can serve the previous payload for up to the TTL. Harmless in a demo; wrong in
production, and the kind of thing worth fixing before it is.

### Redis going down is not an outage

`CacheConfig` installs a cache error handler that logs and continues. If Redis is
unreachable, every request simply computes what it needs. The cache is an
optimisation and is treated like one.

## The other cache is not Redis

`classification_cache` in Postgres is a *different* thing with the same name in
spirit: it stores every model answer — including its misses — keyed by document
hash × question × engine, with a hit count and a last-hit time. It never expires
in code, because a document does not change: if the model answered this question
about this text once, the answer is still that answer.

That permanent record is also where the two numbers on the Reading screen come
from — how many pairs the model read, and how many of its quotes we could not
verify.

## Readiness and the rest

- `GET /api/status` reports whether the seeder has finished. The API is *not*
  ready during a first ingest, and says so rather than serving half a board.
- `GET /api/telemetry` backs the Reading screen: documents, coverage, blind
  spots, the model's strike rate, the cache counters, and when the refresh last
  ran.
- Admin endpoints (`/api/admin/*`) are disabled unless `ADMIN_ENABLED=true`. They
  are for rebuilding the corpus and the overlay, not for the demo.
