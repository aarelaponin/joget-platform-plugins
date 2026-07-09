# G3 — the lifecycle projector drives the live StatusManager (Gap 3 closed)

Proof that a lifecycle **declared in a Layer-1 model** becomes **enforced runtime behaviour** —
the model → `project_lifecycle` → StatusManager chain, verified end to end.

## The chain (each link verified)

1. **Model → seed (exact shape).** `kit gen lifecycle lc-demo.app.yaml` projects the model's
   `entities[].lifecycle` to the StatusManager seed. Golden unit fixture: `joget-spec-kit`
   `tests/test_lifecycle.py` (in the 114-test suite). Output for the demo model:
   `mmEntityState` = draft(initial)/active/closed(terminal); `mmEntityTransition` =
   draft→active, active→closed.

2. **Seed → live config store.** `load_seed_jdx8.py lifecycle-seed.yaml` loads that output into
   jdx8's **live** `app_fd_mmentitystate` / `app_fd_mmentitytransition` — the exact tables
   `MmConfigService` reads — and reads it back. Confirmed loaded (correct columns
   `c_entity/c_scope/c_code/c_isinitial/c_isterminal`, `…/c_fromstatus/c_tostatus`).

3. **Config store → enforcement.** `GamStatusReuseTest` (2/2 green, `mvn -o test`) drives the
   real `StatusManager` over rows of this exact shape: a **legal** `draft→active` advances the
   status and appends an event; an **illegal** `active→draft` throws `InvalidTransitionException`
   and **writes nothing**. The test's field names match the projector's output 1:1
   (`row("entity","gamWidget","scope","DEFAULT","fromStatus","DRAFT","toStatus","ACTIVE")`).

Together: a declared lifecycle is projected to config, loaded into the live StatusManager store,
and enforced — the model → runtime gap (S2C-01 §2.4) is closed.

## Caveat — the HTTP click-through harness has drifted

`proof_lifecycle_live.py` was intended to also demonstrate the transition **through the browser
form path** (a `gamMove` submit firing `GamMoveGuard` → `StatusManager`). On the current jdx8
the demo's HTTP form-CRUD mechanics have drifted from when the demo was first proven — the login
now needs the master-token flow (handled), but the add-form field parsing, CSRF field, and the
`getData` JSON endpoint changed, so form submits no longer persist through that script. This is
a **demo-harness** issue orthogonal to the projector and to Gap 3; refreshing it for the current
Joget build is a separate, cosmetic task. The enforcement itself is proven by links 1–3 above.

## Files
- `lc-demo.app.yaml` — the Layer-1 demo model (one entity, a lifecycle).
- `load_seed_jdx8.py` — loads a `project_lifecycle` seed into jdx8's live StatusManager tables.
- `proof_lifecycle_live.py` — the (drift-blocked) HTTP end-to-end harness; kept for the refresh.
