# agent_progress — the configurability and credibility pass

Tracking file for this goal. Updated as work lands, not at the end.

Status: **phases 1-2 backend landed; UI and later phases in progress**.

---

## The goal, restated as work

| # | Asked for | Status |
| --- | --- | --- |
| 1 | Remove `postgres up · mongo up` and `local-rubric-v1` from the footer | todo |
| 2 | Drafts must read organically — no `[1]` citation markers | todo |
| 3 | Everything configurable, including brand voice and talk style | todo |
| 4 | Questions panels are messy and hard to read | todo |
| 5 | Classification must use real algorithms and LLM classifiers, not fixed cue lists | todo |
| 6 | Service lines must be configurable | todo |
| 7 | Validate against the hackathon challenge; build everything except third-party integrations | todo |
| 8 | Bottom-left: next scheduled refresh | todo |
| 9 | Progress tracked in `agent_progress.md` | this file |
| 10 | Service line as a free-text string in the question editor → needs a picker | todo |
| 11 | Shows `data.initiative` instead of a human title | todo |
| 12 | Switching service line makes the list vanish and come back; it does not switch | todo |
| 13 | Loading spinners where they are missing | todo |

---

## Phase 1 — the reported defects

- [x] **The footer.** Store-health line and engine name removed; the rail now
      carries the refresh countdown instead. `/api/status` still reports both, for
      whoever operates the deployment.
- [x] **Service line switching.** Reproduced: `chooseLine` set `meta = null` before
      fetching, which empties the only list the rail renders from — hence the list
      vanishing and returning. The rail now keeps its list and swaps the payload,
      and shows a spinner on the line being loaded.
- [x] **Keys shown as titles.** Questions carry a human `title`, seeded for all 11
      shipped questions and derived from the text for anything created. Backend done;
      the UI still renders keys in two places and is being changed.
- [ ] **Free-text service lines.** The question editor accepts any string, so it can
      create a service line no board is scoped to. Bind it to the real set.
- [ ] **Missing spinners.** Audit every panel that loads and give it a progress state.

## Phase 2 — service lines as data

- [x] `service_line` table: key, name, description, value proposition, order, active.
- [x] `GET/POST/PATCH/DELETE /api/service-lines`.
- [x] Boards read the service-line list from the table, falling back to the questions
      when the table is empty.
- [x] Deleting is refused while questions reference the line, naming the count.
- [ ] Pickers in the UI read from the table — pending.

## Phase 3 — one settings surface

- [ ] A single **Settings** area with sections rather than one long page.
- [ ] **Brand voice**: tone, formality, sentence length, how we address the reader,
      the sign-off, the language, and phrases never to use.
- [ ] Voice is data the outreach writer reads, not a hardcoded register.

## Phase 4 — classification that can be defended

- [ ] The language model is the **primary** classifier wherever a key is present;
      the local rubric engine is the explicit offline fallback and says so.
- [ ] Every signal records which engine produced it; the workspace reports the
      measured strike rate for quotes not found in their capture.
- [ ] Cue lists remain the fallback's configuration and stay editable, but stop
      being the product's answer to "how does it decide".

## Phase 5 — outreach that reads like a person wrote it

- [ ] No `[1]` markers in the body. Citations stay available to the rep, because a
      rep may want them and a prospect must not see them.
- [ ] Grounded generation: the model writes from the ledger's own passages under the
      configured voice. A claim with no passage behind it cannot appear, because the
      passages are the only input.
- [ ] Deterministic composition remains for when no model is configured.

## Phase 6 — scheduled refresh

- [ ] A scheduler refreshes scores on a configured interval.
- [ ] `/api/meta` reports the next run.
- [ ] Shown bottom-left in the rail, where the store-health line used to be.

## Phase 7 — against the challenge

- [ ] Walk the brief line by line; list the gaps and close them.

---

## Open items

- The reference image (`image.png`) is not on this machine and was never reviewed.
  Everything here comes from the written description.
- Third-party integrations (HubSpot and any other CRM, email or enrichment
  provider) are deliberately left as recorded intents, per the goal.
