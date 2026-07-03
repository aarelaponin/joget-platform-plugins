# Test Strategy

How the platform plugins are verified. Goal: prove the foundation before extending it — "compiles"
is not "works". Four layers; L1/L2/L4 run anywhere (sandbox, CI), L3 needs a live Joget on the
workstation.

## L1 — Unit tests (JVM, no Joget)

Test the pure logic, mocking the Joget API. **157 tests green across the reactor.**

| Module | Coverage |
|---|---|
| `rules-grammar` | ANTLR parser/AST suite (comparisons, operators, precedence, scoring, aggregation) |
| `joget-rules-api` | **FieldMapping** (SQL column ref, grid JOIN/correlation, grid-child resolution, fallback, sample) + **end-to-end parse→compile→SQL** (empty/valid/garbage parse; INCLUSION rule compiles to SQL referencing the mapped column) |
| `joget-form-prefill` | PrefillResolver + config (key fallback, filters, numeric pick, alias mapping, bad-field, grid parse) |
| `form-quality-runtime` | rule/gate evaluation |

**Queued (meaningful):** `form-creator-api` (request/response JSON round-trip, error util),
`wf-activator` (`PluginResponse`). **Not unit-testable** (logic lives entirely inside Joget-extending
classes — `Element`/`FilterType`/`UserviewMenu`): `joget-lookup-field`, `joget-concat-field`,
`joget-advanced-filters`, `tree-menu`, `joget-rule-editor`. These are covered by L3 only — a future
logic-extraction refactor (the `form-prefill` pattern) would make them unit-testable.

## L2 — Bundle/manifest sanity (cheap, catches "won't load")

For each built JAR, assert the OSGi manifest declares the `Bundle-Activator` and the expected plugin
classes + `properties/*.json` are present. Runs with no Joget instance. **To add** — it is the layer
that catches a plugin that compiles but fails to register.

## L3 — Reference application (the real proof)

One Joget app that instantiates **every** plugin, deployed to a live instance, then a scripted
acceptance run. **Target: `jdx7` — Joget DX 9.0.5, PostgreSQL** (`jwdb`).

Deploy = copy the 11 plugin JARs (+ pinned `joget-status-framework`) into `<jdx7>/wflow/app_plugins/`
(hot-reload), then import the reference `.jwa`. Acceptance = render checks + DB checks (`joget-db-inspect`).

### Scenario matrix (plugin → how exercised → acceptance check)

| Plugin | Exercised by | Pass check |
|---|---|---|
| `joget-form-prefill` | new record opened with `?key=` prefills fields | prefilled values present on render |
| `joget-lookup-field` | SelectBox change auto-populates dependent fields | dependent field updates live |
| `joget-concat-field` | two inputs → concatenated field | output = `a-sep-b` per config |
| `form-quality-runtime` | save with a failing rule | banner shows issue; gate blocks/warns |
| `joget-rule-editor` + `joget-rules-api` + `rules-grammar` | author a rule in the editor; API compiles it | editor loads; API returns compiled SQL |
| `joget-advanced-filters` | datalist with date-range + MDM filters | filtered rows correct |
| `tree-menu` | userview tree menu over a self-referencing table | tree renders with nesting |
| `form-creator-api` | REST call creates a form | form definition created in DB |
| `wf-activator` | tool starts a process for a named service | process instance started |

## L4 — CI + conformance

GitHub Actions (`.github/workflows/ci.yml`) runs `mvn -B verify` (L1 unit tests + L2 package) and
then `tests/manifest_smoke.py` (registry↔built-jar drift check) on every push and PR.

**Self-contained, secret-free.** Joget's build-time artifacts are not on Maven Central and Joget's
Archiva is credential-gated (returns 401), so a bare runner cannot resolve them. Instead the exact
non-Central closure — `wflow-*`, `apibuilder_api`, the Apache-2.0 `joget-status-framework`, and
Joget's bundled Enhydra Shark / Together engine (67 jars, ~15 MB) — is vendored into an in-project
file Maven repository at `build-support/m2`, declared in the reactor pom. CI therefore needs **no
Joget repository and no secrets**: `mvn clean verify` resolves everything from Central + the
in-project repo. This exact setup was proven in a clean room (empty local repo, Central only):
**208 unit tests green + manifest smoke 11/11**. Regenerate the vendored set with
`build-support/refresh.sh` after a Joget version bump. See `build-support/README.md`.

Still open (Phase 4): the provenance forbidden-strings gate; optionally a containerised Joget for L3 smoke.

## Two-track note

L1/L2/L4 are workstation/CI/sandbox. L3 runs on the workstation against `jdx7` (DX9 + Postgres),
driven via Desktop Commander — the same two-track method used for DMBB.
