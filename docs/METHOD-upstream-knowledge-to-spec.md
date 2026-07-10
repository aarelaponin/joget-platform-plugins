# Method — the platform upstream: knowledge → build-ready spec

_Written 2026-07-09; aligned 2026-07-10 with S2C-01 v0.9 (the inventory is **Layer 0 — the
knowledge layer**; record schema per M0; threshold per owner ruling). The upstream half of the
chain of custody, designed from the DM evidence (EVIDENCE-DM-forced-slot-extraction.md) and
the registration failure. Governing principle unchanged: prove on a slice, then harden._

> **Proven in practice (M5, 2026-07-10).** The whole method now has shipped tooling and two green
> runs: the tax-registration foundation slice (harvest) and DMBB F13-payments (elicit) — both drove
> downstream decisions to zero on the identical, unchanged tooling. The step-by-step is
> `joget-spec-kit/docs/RUNBOOK-upstream.md`; the six S0→S2 skills are at v1; ADR-063 records the
> extraction and the two-run evidence.

---

## 1. What the upstream manufactures

Not a document — a **decision inventory with custody**. The upstream's output is the set of
domain decisions a build needs, each one: made (or explicitly waived / routed to a human),
sourced (citation to evidence or a recorded designer answer), landed (mapped to its L1 block),
and pointed at its enforcement target. The spec prose is a *view* of this inventory, not the
artifact itself. A slice is build-ready when its inventory has zero unaccounted decisions —
that is the whole definition.

Each decision record carries: `id · dimension · question · answer · source (doc-citation |
designer-answer | ASSUMPTION) · status (DRAFT | VERIFIED | TO-CONFIRM | WAIVED) · landing
place (L1 block) · enforcement point (projector target) · scenario refs`, plus — since the
dimension-8/9 extension (METHOD-EXT, PLAN v3 M0) — a `change profile` (zone, Y/P/N class,
change owner, governance) and a `sharing scope` (reference/master/config/platform bucket with
its register citation). This is the record shape S2C-01 §4.1 carries; where earlier drafts
treated WAIVER as a source kind, it is now a status.

## 2. Two intake modes, one machinery

Knowledge arrives in two forms; the templates and inventory are identical, only the source
differs:

- **Harvest mode** — knowledge in documents (reference processes, prior specs, laws).
  The questionnaire is answered by extraction, every answer cites document + section,
  DRAFT until verified. This is registration's mode (KG/DBM/UA material).
- **Elicit mode** — knowledge in heads (designer, client officer, domain expert).
  The same questionnaire is answered by structured interview; a designer's answer is a
  citable decision (recorded, attributed, dated). This is DM's mode.

Most real slices are mixed. The rule that keeps both honest: **a question no source answers
becomes a TO-CONFIRM item routed to the human — never a silent default.** The registration
failure (VAT-as-checkbox) and the DM week are both, at root, silent defaults.

## 3. Organize vertically, interrogate on six dimensions

Work runs **one procedure/slice at a time** (never horizontal dimension phases — that is how
questions hide until implementation). Within a slice, the interrogation covers the six
dimensions the DM evidence proved are where build-blocking content lives — none of which is
FR/UC/BR prose:

| # | Dimension | Forced slot (must be filled, waived, or TO-CONFIRMed) | Lands in | Enforced by |
|---|---|---|---|---|
| 1 | Behavioural / state | Full state machine per long-lived entity: states, transitions, triggers, roles, edge transitions (late settlement, rejection-from-submit…) | `entities[].lifecycle` | lifecycle projector → status-framework |
| 2 | Computation | Formula over named fields for every amount, metric, KPI, flag | `queries`, rules, computed attrs | generated rules/queries |
| 3 | Interpretation | Every rule table normalised to data; reading stated (cumulative/exact); full range coverage (no implied C6) | vocabularies / config entities | seed rows |
| 4 | Ownership / recompute | Per attribute: source system, snapshot-vs-live, who may write | `canonical_ref`, SoT bindings | binders, interface config |
| 5 | Failure / edge | Per external dependency: behaviour when down, stale, or empty | `interfaces`, guards | adapter config, guards |
| 6 | Grain / keys | Per case type: consolidation grain, dedup key, line-vs-header decisions — reconciled against the rule register | entity design | table structure |

The classical dimensions (domain entities, roles, integrations, rules-as-such) remain — the
spec already tends to cover them; the six above are where it goes silent.

### 3b. The seventh dimension — work & workspace (UX)

UX was absent from the six because it was absent from the answer key: the DM build's UX
defects were fixed in a *separate post-hoc remediation pass* rather than recorded as design
decisions — which is itself the evidence that UX was ungoverned. The registration failure
was equally a UX failure: "one form and a flat list" is a workspace no officer can do a
day's work in. UX splits into two tiers with different owners:

**Tier 1 — elicited per application (this dimension's slots).** Business decisions only the
designer/officers can make: persona registry (roles, daily volumes, environment, expertise
— the DM spec's per-UC frequencies like "~20–50/day" are exactly this raw material, never
converted to decisions); per persona, what defines "my work" and its priority order; per
list, the columns officers actually scan and the filters they actually use; per form, what
is prefilled vs verified vs typed; bulk operations above which volume; empty and error
states; navigation per role. **If skipped:** officers drown in undifferentiated lists and
the real workflow moves to Excel — the system is "delivered" and unused.

| # | Dimension | Forced slot | Lands in | Enforced by |
|---|---|---|---|---|
| 7 | Work & workspace | Persona registry w/ volumes; per-persona worklist definition (scope, priority, default sort); per-list column/filter set; per-form prefill/default/verify split; bulk-op thresholds; empty/error states; role navigation map | `roles`, `lists`, `forms.prefill`, `navigation`, `dashboards` | userview/datalist/form generators + the UX pattern shelf |

**Tier 2 — platform-invariant (the UX pattern shelf, NOT elicited).** How a worklist
behaves, 360-view composition, wizard/confirmation patterns, status-badge and RAG
conventions, validation-message templates, terminology lexicon, accessibility defaults.
Decided once, baked into the *generators* — the exact analogue of the plugin shelf, with
the same rule the DM UX remediation already proved: a usability defect is fixed in the
pattern/generator, never on the one screen, so every app (and every sector) inherits it.

**The UX oracle.** Unlike domain realism, UX has a cheap real oracle: actual users
performing tasks on the generated preview. Because generation is cheap, a usability
walkthrough (3–5 real officers, task completion, click counts, time-to-find) runs **per
slice**, not at the end. Findings route like everything else: workspace decisions → the
decision inventory; pattern defects → the shelf. The walkthrough sign-off is the UX row
of the readiness gate.

## 4. The slice pipeline

```
pick slice (one procedure / case type / feature)
  S0  harvest + elicit  → evidence pack + open-question register (TO-CONFIRM)
  S1  procedure & UC    → skeleton instantiation (pack mode) or cited enumeration (greenfield)
  S2  six-dimension templates per procedure → decision inventory fills
  ⟲  PLAYBACK each round: generated readbacks — lifecycle diagram, scenario walkthrough
      in the designer's own terms, clickable preview. Designers correct what they see;
      they cannot enumerate what is missing in the abstract.
  GATE readiness (mechanical + human):
      • zero open blocking slots; every slot filled / WAIVED / answered TO-CONFIRM
      • consistency lint green (§5)
      • pack mode: reference-diff clean or signed waivers
      • every in-scope procedure has ≥1 acceptance scenario asserting its lifecycle path
      • UX row (§3b): dimension-7 slots complete; usability walkthrough on the generated
        preview passed with real users (task completion + click budget), findings routed
      • designer signs the pre-populated checklist (LLM critique pre-populates, never gates)
  COMPILE to L1 + mandatory loss report (inexpressibles → named schema gaps, never flattened)
```

Scenarios deserve emphasis: **write them during elicitation, not after.** "A taxpayer pays
two days late while a bank confirmation is pending — what happens?" is simultaneously the
best interrogation device, the record of the answer, and the runtime acceptance test. One
artifact, three custody roles.

## 5. The consistency lint (the oracle when no pack exists)

DM had no reference pack, and the general case won't either. Cross-view consistency is the
internal oracle, and it is mechanical: every UC step reads/writes a declared entity; every
declared state is reachable and asserted by at least one scenario; every rule names its
enforcement point in some UC; every screen serves a UC; every parameter has value + unit +
owner + seed row; every entity is touched by some procedure (no orphans). Weak specs fail
these checks before they fail completeness. Where a pack exists, the external reference-diff
runs *in addition*.

## 6. Custody's second direction: the citation column

The upstream ends at a build-ready spec, but the DM evidence showed ≥9 decisions the spec
HAD were re-derived or diverged downstream. So the upstream method owns one downstream
obligation: the FIS template gains a **citation-or-assumption column** — every parameter,
state set, and rule in an FIS names the spec/decision ID it implements, or logs an explicit
ASSUMPTION that flows back into the inventory. Divergence becomes a diff, not archaeology.
This is one column and it closes an entire failure class.

## 7. Division of labour and stop rules

- **The machine** checks: slot completeness, lint, diffs, citation coverage. Never judges.
- **The LLM** drafts, extracts, generates questions and readbacks, pre-populates checklists.
  Never gates its own work; never fills a slot without a source.
- **The designer** answers TO-CONFIRMs, corrects playbacks, signs the gate. The checklists
  are written as training material — they are how the reviewer role becomes teachable.

Stop rules (absolute): DRAFT evidence feeds nothing downstream; an unanswered *blocking*
TO-CONFIRM stops the slice; generation before a signed gate is a method violation; a
non-empty loss report without named schema gaps fails the compile.

## 8. Validating the upstream itself

The method is trusted only against answer keys, per the governing principle:

1. **Pilot now, on live work:** apply the six templates + the citation column to the next
   DMBB feature built anyway. Measure: domain decisions made downstream of the gate
   (DM baseline ≈ 53 per module; the pilot's number is the method's first real grade).
2. **Retrospective check:** the registration slice (G9) runs pack-mode; its gate must
   surface the known misses (VAT-as-procedure, lifecycle, effective dates) or the method
   is wrong regardless of how principled it reads.
3. **The standing metric:** downstream decisions made after the readiness gate — baseline
   ≈ 53 per module, **target single digits, held over two consecutive modules** = the
   upstream is reliable (owner ruling 2026-07-10, matching S2C-01 §7.4; zero remains the
   asymptote, not the gate). Nothing else counts as done.

## 9. What the upstream deliberately is not

No big-design-up-front; no "ideal spec" (readiness is checkable, ideal is not); no
horizontal dimension phases; no BPMN beyond what the reference material itself carries; no
new prose deliverables — the decision inventory and its views replace volume with custody;
and no domain content in the method tier — packs carry it, versioned and replaceable.

---

**Compression:** the upstream is a decision factory with two intake doors (documents, heads),
one interrogation core (six evidence-derived dimensions + slots), playback instead of review,
a checkable readiness gate instead of an "ideal spec," and one exported obligation downstream
(the citation column). Its reliability is measured by a single number the DM build already
gave a baseline for: how many domain decisions still get made after the gate.
