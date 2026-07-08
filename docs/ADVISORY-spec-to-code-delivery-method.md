# Advisory — Making spec-to-code-on-Joget a robust, actionable delivery method

_Review date: 2026-07-08. Based on: ta-ref-arch RESUME-NOTE + PLAN-LLM-assisted-realistic-modelling,
joget-platform-plugins RESUME-NOTE + PROBLEM-semantic-design-gap, the joget-spec-kit schema/tools/ADRs,
CAD-RGBB, PLUGIN-INVENTORY, and a CMBB FIS with its acceptance tests._

---

## 1. Assessment: you have more than you think, in the wrong places

Across the two workstreams you have four proven assets, each living in a different corner:

1. **The mechanical pipeline** (spec-kit): validated L1 model → deterministic projectors → real Joget JSON → one-command DX9 deploy, 113 tests, delta registers, golden fixtures. Proven.
2. **The runtime semantics shelf** (platform-plugins): status-manager (config-driven state machine), event-chain (tamper-evident log), decision-approval, SLA, case-ops, tenant — 17 modules, extraction recipe run 6×, live reuse proven on a second app.
3. **The FIS discipline** (CMBB debt build): per-feature spec with FR→acceptance-criterion→realisation→test traceability, SQL-level acceptance assertions, scripted runners, full 24-green regression. This is the strongest *process* artifact in either track.
4. **The DRAFT→VERIFIED evidence discipline** (from your ARMS/PAERA work): claims are draft until verified against an authoritative source.

The questionable results are not because any of these is weak — it's because **no single delivery path uses all four**. The registration app was built with asset 1 only: no FIS, no acceptance tests derived from the domain, no lifecycle runtime, no evidence gate. The debt app uses 2+3 but not the kit. The method you want is largely an integration problem, not a research problem.

## 2. Sharpening the diagnosis: three gaps, not one

Your PROBLEM doc names the requirements→design gap. The code shows the semantic loss actually leaks at **three** points, and fixing only the upstream one will still produce naive apps:

**Gap 1 — Requirements→design realism (the one you named).** Correct as stated: single unreviewed pass, no oracle, regression to the textbook model. The PLAN's S0–S3 addresses this — but gates alone only *catch* naiveté after the fact; **Annex B** specifies the mechanisms that *prevent* it inside S0–S2. One correction to the analysis, though: the failure was *not* primarily meta-model impoverishment — schema 0.1.6 already has lifecycle as a first-class construct (states, transitions, guards, effects; ADR-001) and source-of-truth machinery (canonical_ref, vocabularies, md_lookup, interfaces). The model that got built simply didn't use them.

**Gap 2 — Design→model fidelity is ungated.** CAD-RGBB is deep: 13 features, two full state machines, a disciplined plugin budget, UC-REG-01–11. Yet the L1 model alongside it has 10 entities, 1 process — and **passed coverage at 91.3%**. That number is the tell: coverage was measured against the requirements register (a thin enumeration), so the gate certified fidelity to the wrong denominator. This is Goodhart in your own pipeline: any coverage metric whose denominator the same LLM pass produced is self-grading. The PLAN's reference-diff fixes the denominator; make sure it also gates the **CAD→L1** step, not just requirements→CAD.

**Gap 3 — Model→runtime silently degrades.** Even a perfectly realistic model would today emit forms + flat lists: the projectors validate `lifecycle` and catalog bindings (via the registry-mirror) but **generate no status-manager/event-chain configuration** — no `mmEntityState`/`mmEntityTransition` seed rows, no Activator bindings. Lifecycle currently only counts toward coverage. Meanwhile rich constructs sit in the totality register and are *refused* by projectors. So the pipeline's expressive floor truncates whatever realism survives design. The register-list-showed-0-rows incident (prefill binder referenced but plugin not installed) is the same class of failure: the generator emitted a dependency the deploy never verified.

**Conclusion:** the fix is a pincer — realism gates upstream (your PLAN) **and** closing the generation gap downstream so the model's semantics actually run. Doing only S0–S3 would produce beautifully reviewed designs that still deploy as forms + lists.

## 3. The recommended method (one path, both repos)

Converge on a single staged pipeline. Stages produce named artifacts; gates are checkable and fail loudly. Nothing below is new invention — each row names where it already exists.

| Stage | Artifact | Gate | Exists today in |
|---|---|---|---|
| S0 Evidence harvest | Domain Evidence Pack, every item cited, DRAFT→VERIFIED | no DRAFT items feed S1 | ARMS/PAERA verify pattern |
| S1 Procedure model | Procedure catalogue + per-procedure spec (BPMN-light: lanes/gateways only where the reference has them) | **G-R1 reference-diff**: bidirectional map vs anchor set; gaps become signed waivers | PLAN §2–3 (to build) |
| S2 Domain model | Entities + lifecycles + effective-dating + SoT bindings + parameterisation table | **G-R2**: lifecycle-fidelity + every country-specific rule is a parameter + human checklist sign-off | PLAN §4, CAD §2.3 |
| S3 CAD | Component architecture, plugin budget, feature decomposition | **G1** (exists): CAD gate checklist, plus new check: L1 model implements the CAD (procedure-by-procedure) | CAD-RGBB |
| S4 FIS per feature | Traceability matrix, business-rule enforcement points, config parameters, generation order, acceptance tests with SQL assertions | architecture go/no-go per FIS §3 | CMBB features (adopt into kit) |
| S5 Generate + deploy | L1 model → projectors → .jwa + **plugin config seeds** → deploy_dx9 | scripted acceptance run + regression green; deploy verifies required plugins installed | spec-kit + run_regression.sh |

Two cross-cutting rules that make it survivable for a low-exposure team:

- **Second traceability axis** (your Part C): design ↔ external reference set, maintained as a diffable file in the repo, alongside the existing requirements→artifacts TRACE.
- **Source-first, always**: no on-instance patch without the same change landing in spec/generator first. The five live patches on jdx7 that a clean regenerate would not reproduce are exactly the debt that kills this method at handover. Make "clean regenerate == live" a standing gate, not a loose thread.

## 4. Answers to the PLAN's five open decisions

**#1 BPMN depth — foundation first.** First-registration (one party type), VAT registration, and the inactivate/reactivate/deregister lifecycle. That trio forces every hard primitive (separate procedures, threshold+effective dates, state machine, Taxpayer-360, worklists) without BDUF. Expand procedure-by-procedure per slice; generation being cheap is your asset — keep the loop tight (your own B7).

**#2 External anchor — a stack, not a choice** (your B5 is right; commit to it): IMF DBM functional blocks = the skeleton anchor; the Kyrgyzstan 13-process set = the instance gold you diff against; GovStack RegBB = interface contracts only (already your ADR-011 seam); drop TADAT for design-diffing. Encode DBM blocks + the KG procedure list as a machine-readable reference set inside the kit so the diff is a tool, not an essay.

**#3 What "proven realistic" means — waivers, not percentages.** A percentage invites Goodhart. The bar: (a) every anchor procedure maps to a modelled procedure or to an explicit signed waiver with a reason; (b) every modelled procedure maps back to evidence (no inventions); (c) lifecycle-fidelity and parameterisation checks pass mechanically; (d) you sign the pre-populated waiver/checklist. LLM domain-critique runs as an *advisory* input that pre-populates the checklist — never as the gate (your B2: it grades its own work).

**#4 First build scope — the foundation slice of #1**, taken all the way through S5 including status-manager-backed lifecycle live on the instance. One realistic slice end-to-end beats a full catalogue on paper: it is also the forcing function for the Gap-3 build items below.

**#5 Where artifacts live — split by neutrality.** Method (stage templates, gate definitions, checklists, the reference-diff tool, the encoded DBM skeleton) → the kit, versioned. Instances (evidence pack, TA registration catalogue, BPMN, domain model, country parameters) → the program repo. Same split you already run for generators vs models. The KG/UA-derived reference sets contain client-adjacent material — keep the *instance gold* in the program repo, ship only the neutral DBM skeleton with the kit.

## 5. Build backlog (ordered; each item is small and independently verifiable)

1. **Close Gap 3 first — lifecycle projector.** New projector emitting status-manager + event-chain configuration from `entities[].lifecycle`: `mmEntityState`/`mmEntityTransition` seed rows, carrier-form bindings, and the consumer Activator binding block. The registry-mirror contract already defines the config keys; this is the missing half of an existing bridge. Acceptance: the facility-permit example deploys with a *running* state machine and a verifiable event chain. **Full elaboration in Annex A.**
2. **Deploy-time dependency gate.** `deploy_dx9.py` verifies every catalog-bound plugin is installed on the target before import (the prefill/0-rows incident becomes impossible). Fold the four other jdx7 on-instance patches back to source; prove clean regenerate == live.
3. **Reference-diff tool** (`kit diff-reference`): machine-readable anchor sets + bidirectional gap report + pre-populated waiver checklist. This is G-R1/G-R2's engine and the single highest-leverage new component in the PLAN.
   **3b. Reference skeleton + stage templates (Annex B, M1/M6)** — the encoded DBM procedure skeleton and the required-slot templates for S1/S2. Same anchor-set encoding work as item 3; build them together.
4. **Schema 0.2, minimal:** effective-dated attribute primitive (CAD already depends on it; schema can't say it), procedure as first-class (or a documented mapping: procedure = feature + process + worklist), party-subtype idiom, and a 360/detail composite view. Only what the foundation slice needs — resist a big meta-model release.
5. **Port the FIS as a kit template** with its acceptance-test format and scripted-runner convention. This is how the debt track's rigor becomes method rather than tribal practice.
6. **Run the foundation slice** (decision #4) through the full S0–S5 path as the method's proving run; write the runbook from what actually happened, not in advance.

## 6. Operating rules for the agent sessions themselves

The two Cowork tracks went sideways in characteristic ways worth encoding as session rules: semantic artifacts are never produced and consumed in the same un-gated pass (a gate artifact must exist between them); agents never patch a live instance except via source + regenerate + redeploy; every session ends by reconciling "clean regenerate == live" and logging any delta as explicit debt; and coverage numbers produced by the same session that produced the denominator are advisory, never a gate. Consider consolidating both RESUME-NOTEs' conventions into the kit's rules/ so every future session inherits them — you already do exactly this for platform deltas (D-001…065), which is why the mechanical half stopped surprising you.

---

**Compression:** you diagnosed one gap; there are three. Gate the design against an external reference (your PLAN, with waivers not percentages), gate CAD→L1 against the CAD itself, and close the generation gap so lifecycle actually runs — the plugins and the contract for it already exist. Then fuse the FIS/acceptance discipline from the debt track into the kit. One method, one proving slice, both repos.

---

## Annex A — The lifecycle projector (backlog item 1) in detail

**The two halves that already exist.** On the model side, the schema already lets an author declare a full state machine. The facility-permit example does it today (`examples/facility-permit.app.yaml:122`): six states, transitions like `submit: draft→submitted, trigger: user, roles: [applicant], effects: [audit, set_attr]`. On the runtime side, `joget-status-manager` is a config-driven state machine engine: at runtime `StatusManager.transition()` looks up allowed `from→to` moves in two config tables (`mmEntityState`, `mmEntityTransition`), refuses illegal moves, writes the status, and appends a hash-chained row to the `joget-event-chain` log so the history is tamper-evident. It is proven live on two apps (debt on jdx9, GAM on jdx8).

**The missing piece.** Nothing translates the first into the second. The kit *validates* the lifecycle block and *counts it in coverage*, but no projector emits anything from it — no tool in the kit mentions `mmEntityState`/`mmEntityTransition` at all (verified by grep). So when taxRegistration deployed, "status" was just a text column: any value could be written into it, in any order, by anyone, with no audit. The lifecycle the model declared evaporated between YAML and instance.

**What the projector does.** A new tool (`project_lifecycle.py`, peer of `project_forms.py`) reads `entities[].lifecycle` and emits three artifacts:

1. **Seed rows for the mm\* config tables** — the mechanical translation. From the facility-permit example: `states[]` → six `mmEntityState` rows (with initial/terminal flags); `transitions[]` → rows like `(submit, draft, submitted, roles=applicant)` in `mmEntityTransition`. These load at deploy time like the existing `seed` block, and the state machine is live.
2. **Carrier-form bindings** — which forms/tables the engines write to. The config keys are already contractually defined in `contracts/registry-mirror.yaml`: `joget-event-chain: [eventFormId, caseFormId]`, `joget-status-manager: [caseFormId, taskFormId, docFormId]`. The projector fills them from the model instead of a human remembering them.
3. **The Activator binding block** — the startup wiring a consumer currently does by hand in Java (`CaseEventWriter.setDefaultEventFormId(...)` etc.). One design decision: generate Activator source for the app's bespoke bundle, or ship a small generic binder plugin that reads bindings from a config form so purely-generated apps need zero Java. Recommendation: the latter, for the low-exposure target audience.

**Why this is "close Gap 3 first":**

- **It is the cheapest half of the realism fix.** The contract already exists (the registry-mirror is literally a mirror of the plugin registry, with drift detection), the plugins are proven, and the projector pattern is established. Days of work, not a research question — versus S0–S3, which needs design.
- **It makes the upstream gates worth passing.** Built the other way round, a reviewed, realistic design still deploys as forms + flat lists — the pipeline flattens it. Close this first and every lifecycle the design stage produces becomes *running, enforced* behavior for free.
- **It is exactly what "professional grade" means for the target audience.** A team with low software exposure writes declarative YAML; the platform guarantees that a deregistered taxpayer cannot silently become active, that transitions are role-gated, and that the audit chain is cryptographically verifiable — properties a government reviewer cares about, delivered without anyone writing enforcement code.
- **It fuses the two workstreams.** The debt-track plugin shelf stops being a parallel effort and becomes the generation target of the kit — one method, both investments compounding.
- **It upgrades acceptance testing.** The `acceptance.scenarios` block is defined "over entities and lifecycles" — with a real state machine, transitions can be asserted in SQL (the CMBB test discipline) and `ChainVerifyService` runs as a standing integrity check.

**The acceptance criterion decoded:** regenerate and deploy the kit's golden example (facility-permit) and prove three things live — a legal transition executes through StatusManager; an illegal one (e.g. `draft→issued`) is refused and writes nothing; and the event chain re-verifies end-to-end. That becomes a golden CI fixture so the capability cannot silently regress — the same pattern as the existing round-trip proof.

---

## Annex B — Robust S0–S2: mechanisms that prevent naive design (not just gates that catch it)

**Why a comprehensive requirements spec still yields a naive design.** Three mechanisms, all observed in the registration run:

1. **Invention regresses to the mode.** Asked to *produce* structure, an LLM emits the modal, textbook shape of the concept and back-fits the requirements onto it. A comprehensive spec doesn't help, because requirements are consumed as *constraints to satisfy*, not *structure to derive from* — "support VAT registration" is satisfiable by a checkbox. Requirement granularity never forces design granularity.
2. **Whole-spec context averages away the long tail.** One pass over 74 FRs produces one generic app. The domain content that matters (thresholds, effective-dating, ex-officio triggers) is exactly what averaging removes.
3. **Authoring directly in the target vocabulary optimises for the target.** The design was written straight into L1 — a language of forms, lists, processes — so "what compiles" quietly became the objective, and anything without an obvious L1 construct (effective-dating, procedure families) was silently dropped rather than surfaced.

Each mechanism has a structural counter. Seven, concretely:

**M1 — Invert the task: instantiate, don't invent.** Ship the country-invariant procedural skeleton (the DBM five blocks expanded into a procedure template catalogue: first-registration per party type, tax-type registration, maintenance, inactivation/reactivation, deregistration, enforced registration, …) as a machine-readable asset in the kit. S1 is then re-defined: *for each skeleton procedure, decide in-scope / out-of-scope / adapted for this country, citing evidence, and fill its parameters.* The LLM maps and specialises; it never free-generates the catalogue. Collapse-to-one-flow becomes structurally impossible — the unit of work is per-procedure, and skipping one requires an explicit out-of-scope entry (which G-R1 turns into a signed waiver). This is the design-time analogue of the plugin shelf: reuse over invention.

**M2 — A pattern library of model fragments.** Encode the recurring TA design patterns as composable S2 fragments: *registered-party lifecycle*, *tax-type registration as effective-dated obligation*, *source-of-truth-bound attribute set*, *establishment/representative satellite entities*, *enforced-registration trigger*. The domain modeler composes and specialises fragments instead of drafting entities from priors. A fragment carries its own required structure (an obligation fragment *has* effective/ceasing dates and a threshold basis — they can't be omitted, only parameterised).

**M3 — Interrogation before modelling.** Before any S1/S2 drafting, a dedicated pass generates the questions the requirements do *not* answer, keyed to the skeleton and the domain checklist: "Are amendments separate procedures per party type? What triggers ex-officio registration? Which register owns legal status?" Unanswered questions become TO-CONFIRM items routed to you — the same discipline as your DRAFT→VERIFIED. This converts silent simplification into a visible open question: the registration failure would have surfaced here as "is VAT a field or a procedure?" instead of being decided by a prior.

**M4 — Evidence quotas in S0.** The harvest is not "summarise the references" but a fixed per-domain questionnaire whose every answer must cite a source (document + section): triggers, party types, authoritative fields and their owning registers, thresholds, lifecycle states, reversal rules, certificate variants. An unanswerable slot is a named evidence gap, never an omission. S1/S2 may only consume VERIFIED evidence-pack items — the ARMS/PAERA rule applied to design inputs.

**M5 — One procedure per pass, evidence-only context.** S1 models each procedure in a separate pass whose context is *that procedure's* evidence (the KG BPMN, the DBM block, the relevant Tax-Code rules) — not the whole requirements spec, and never the L1 schema or generator docs. Every step must trace to an evidence line. This defeats averaging (mechanism 2) and keeps the modeler blind to "what compiles" (mechanism 3).

**M6 — Completeness by construction: required-slot templates.** S1/S2 artifacts are templates with mandatory slots, checkable mechanically before any gate: every procedure has triggers, actor lanes, outcome→lifecycle-transition mapping, and an exception path (missing-data loop); every long-lived entity has a lifecycle *or* a signed "stateless" waiver; **every attribute has a source column** (taxpayer-supplied / primary-register / derived — not optional); every code-like field binds to a vocabulary; every country-specific value sits in the parameterisation table. The FIS already proves this pattern works downstream — this is the same idea moved upstream: an artifact that *cannot be structurally complete while semantically naive*.

**M7 — Design in design vocabulary; compile to L1 with a mandatory loss report.** S1/S2 are written in procedure/lifecycle/obligation vocabulary, not in L1. A separate deterministic-as-possible "compile" step maps the design onto L1 constructs and **must emit a loss report**: every design element it could not express (effective-dating, subtype, 360 view) becomes a named schema gap feeding schema 0.2 — silent flattening is forbidden. Additionally, ship one fully worked, human-reviewed gold exemplar of S0→S2 (the registration foundation slice, once done properly) in the kit: LLMs imitate exemplar structure far more reliably than they follow instructions, and the exemplar doubles as the calibration standard for reviewers.

**Packaging — how this becomes operational rather than aspirational.** Encode S0–S2 as Cowork skills (your established pattern: joget-req-analyst, joget-component-architect, the FIS loop), each skill owning its templates, its questionnaire, its check script, and its stop rule ("do not proceed with DRAFT evidence"). The skeleton, fragments and exemplar live in the kit as versioned assets (decision #5's split). The human reviews small per-procedure artifacts as they emerge — ten minutes each — not one monolithic model at the end.

**Relation to the gates:** M1–M7 make S0–S2 *produce* realistic artifacts; G-R1/G-R2 then verify against the anchors. Both are needed, but your intuition is right about the causal order — with M1/M2/M6 in place the gates should mostly pass; a gate that keeps failing means the skeleton or fragments are wrong, which is a method bug to fix once, not a per-project fight.
