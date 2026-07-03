# DAS extraction plan — freeing the Decision & Approval Service from cmbb-plugins

**Goal:** turn the approval service into a platform plugin (`joget-decision-approval`) that
knows nothing about debt, write-off or enforcement — while DMBB keeps working unchanged.

## The one real obstacle: the effects coupling

Today, inside the `cmbb-plugins` bundle, `service/ApprovalEffects.java` holds a **registry that
maps `actionType → DecisionEffect`** and the effect bodies call DMBB services directly:

```
ACTION_INSTALMENT       -> ReliefService.apply(...)
ACTION_WRITEOFF         -> WriteOffService.applyApproved(...)
ACTION_ENFORCE / _JUDICIAL -> EnforcementActionService.executeApproved(...)
```

`ApprovalService.request(...)` routes on the authority band (`mmAuthority`): below the band it
auto-executes the effect; above it, it pends a `cmApproval` and the effect fires on approval.
The routing, banding, chain/quorum, SoD, delegation, deadline sweep and inbox are all **generic**.
The only project-coupled part is *which Java runs when a decision is approved* — and that is
exactly the thing that must be inverted.

## The inversion: a DecisionEffect SPI, consumer-registered

The platform ships the port; consumers provide the adapter.

```
joget-decision-approval  (PLATFORM — no project imports)
  ApprovalService           routing, banding, chain/quorum, SoD, deadline, delegation, idempotency
  AuthorityResolver         reads mmAuthority
  MatrixValidator           authority-matrix validation
  ApprovalInbox(+Binder)    per-user inbox
  Gate/Delegate/Sweep/AuthorityMatrix engines
  ── SPI ──
  interface DecisionEffect { void execute(String recordId, String actor, Instant now); }
  interface DecisionEffectRegistry {
      void register(String actionType, DecisionEffect effect);
      DecisionEffect get(String actionType);      // throws if unknown
  }
  DecisionEffects  (default registry impl; was ApprovalEffects, minus the hardcoded bodies)
```

```
DMBB (cmbb-plugins — the CONSUMER)
  at Activator start-up, registers its own effects against the platform registry:
     effects.register("INSTALMENT",        (id,actor,now) -> reliefService.apply(id,actor,now));
     effects.register("WRITEOFF",          (id,actor,now) -> writeOffService.applyApproved(id,actor,now));
     effects.register("ENFORCE_ACTION",    (id,actor,now) -> enforcementService.executeApproved(id,actor,now));
     effects.register("ENFORCE_JUDICIAL",  (id,actor,now) -> enforcementService.executeApproved(id,actor,now));
```

`ApprovalService.request(...)` no longer names any DMBB service — it calls
`registry.get(actionType).execute(...)`. The arrow now points the right way: DMBB depends on the
platform SPI; the platform depends on nothing.

## Registration mechanism (OSGi-friendly, no framework magic needed)

Simplest reliable option first: a **static registry** in the platform bundle that the consumer's
`Activator` populates on start (both bundles are in the same Joget runtime). It mirrors how DMBB
already wires effects at startup, so it is a small change, not a re-architecture. If we later want
looser coupling, promote the registry to an OSGi service (`BundleContext.registerService`), but do
not pay that cost until a second consumer needs it.

**Idempotency & unknown-action guards stay in the platform:** the existing `liveRequest`
idempotency guard moves as-is; `registry.get` throws on an unregistered `actionType`, so a
mis-seeded matrix fails loudly instead of silently.

## What moves, what stays

| Class | Destination |
|---|---|
| ApprovalService, AuthorityResolver, MatrixValidator | platform (verbatim, namespace scrubbed) |
| ApprovalInbox, ApprovalInboxBinder | platform |
| ApprovalGateEngine, ApprovalDelegateEngine, ApprovalSweepEngine, AuthorityMatrixEngine | platform |
| ApprovalEffects | split → platform `DecisionEffects` (registry) + the SPI interfaces |
| the 3 effect bodies (Relief/WriteOff/Enforcement calls) | **stay in DMBB**, re-expressed as registered `DecisionEffect`s |
| ReliefService, WriteOffService, EnforcementActionService | stay in DMBB (they are the domain) |
| `mmAuthority` form/seed | shared config contract — platform defines the shape, DMBB seeds the bands |

## Namespace

`com.fiscaladmin.mtca.cmbb.service.Approval*` → `com.fiscaladmin.joget.approval.*`.
Mechanical rename; update `Activator` registration + the `properties/*.json` plugin descriptors
for the moved engines.

## Sequencing (depends on items 5 & 6 first)

`joget-decision-approval` uses the status machine and the event log, so promote in this order
(see MIGRATION-BACKLOG): **joget-event-chain → joget-status-manager → joget-decision-approval**.

## Acceptance for the extraction

- [ ] Platform module builds green with **zero** imports of `com.fiscaladmin.mtca.*`.
- [ ] Unit tests for routing/banding/chain/quorum/SoD/delegation move with the platform and pass.
- [ ] DMBB registers its three effects at start-up; `run_t30` + `run_t31` pass unchanged.
- [ ] A throwaway second consumer registering one dummy effect drives an approval end-to-end —
      proving the SPI works without DMBB (the reuse proof).
- [ ] `cmbb-plugins` no longer contains `ApprovalService`; it pins `joget-decision-approval` instead.


---

## Execution log & the two-increment split (added during Phase 2)

The recon confirmed the inversion was already structural: `DecisionEffect` was an inner
interface of `ApprovalService`, and `ApprovalService` took the `Map<String,DecisionEffect>`
registry via its constructor — it names no domain service. `ApprovalEffects` (cmbb) is just
the factory that wires the three domain bodies. Two hidden couplings were found and resolved:

1. **`ApprovalEffects.service(dao)` is a platform→consumer back-reference.** The four engines
   (`ApprovalGateEngine`, `ApprovalDelegateEngine`, `ApprovalSweepEngine`, and — via
   `MatrixValidator` — `AuthorityMatrixEngine`) call the cmbb factory to build a configured
   `ApprovalService`. **Fix:** ship a static `DecisionEffects` registry in the platform; the
   engines read `DecisionEffects.snapshot()`; cmbb's Activator calls
   `DecisionEffects.register(actionType, effect)` for its three effects at start-up.
2. **`DecisionService`/`AuthorityResolver` borrow `DeadlineService.prop/parseLong`** — static
   helpers on a cmbb domain service that stays put. **Fix:** a small platform `Rows` helper
   (`prop`/`parseLong`/`parseDouble`); ported classes use it.

### Increment 3a — SHIPPED (this repo, `com.fiscaladmin.joget.approval`)
- `DecisionEffect` (top-level SPI) + `DecisionEffects` (static registry: register/get/snapshot/clear).
- `Rows` (row-property + numeric helper; de-couples from `DeadlineService`).
- `DecisionService` (authority rank / collegial quorum / reasoned-grounds decision logic), ported.
- 13 unit tests. Module builds green in the reactor; provenance clean.

### Increment 3b — REMAINING (next session)
Port, scrubbed, onto the same package + `Rows`:
- **`ApprovalService`** (~674 lines) — the router. Promote its inner `DecisionEffect` to use the
  top-level SPI; its constructor keeps taking `Map<String,DecisionEffect>` (fed by
  `DecisionEffects.snapshot()`). It also uses `StatusManager` + `MmConfigService` (already in
  joget-status-manager) and `CaseEventWriter` (event-chain).
- **`AuthorityResolver`**, **`MatrixValidator`**, **`ApprovalInbox`** — supporting services
  (use `Rows`, `DecisionService`).
- **`ApprovalInboxBinder`** (Joget FormBinder/element) + the four `DefaultApplicationPlugin`
  engines: `ApprovalGateEngine`, `ApprovalDelegateEngine`, `ApprovalSweepEngine`,
  `AuthorityMatrixEngine` — with their `properties/*.json` descriptors and a Bundle-Activator;
  the engines call `DecisionEffects.snapshot()` (NOT the cmbb factory). registry.yaml `registers:`
  gains these classes; L2 manifest-smoke then checks them.

### cmbb-side (part of the DMBB re-point task)
- `ApprovalEffects` becomes an **Activator-time registrar**: at start-up it calls
  `DecisionEffects.register("INSTALMENT_PLAN", …reliefService…)`, `("WRITE_OFF", …writeOff…)`,
  `("ENFORCE_ACTION"/"ENFORCE_JUDICIAL", …enforcement…)`. Its `service(dao)`/`registry(dao)`
  factory methods are removed once the engines read the platform registry.
- cmbb keeps `DeadlineService` (and its `prop/parseLong`), the three effect bodies
  (`ReliefService`/`WriteOffService`/`EnforcementActionService`), and re-points
  `ClosePhase` to the platform `DecisionService`.
