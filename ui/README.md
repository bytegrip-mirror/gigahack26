# Gigahack 26 — front end

SvelteKit 2 / Svelte 5 in SPA mode. `@sveltejs/adapter-static` emits `build/`
with an `index.html` fallback, nginx serves it, and every route is client-side.

```sh
pnpm install
pnpm run dev            # http://localhost:5173, expects the API on :8080
pnpm run check          # svelte-check
pnpm run build          # static site into build/
```

The API base is inlined at build time from `PUBLIC_API_URL` (see
`vite.config.ts`), defaulting to `http://localhost:8080`:

```sh
PUBLIC_API_URL=https://hackapi.pistonpunk.com pnpm run build
```

`src/lib/api.ts` holds the fetch client and the shared types; `src/app.css`
holds the design tokens.
