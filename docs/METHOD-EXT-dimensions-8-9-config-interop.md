# Method extension — configurability and ecosystem interoperability as upstream dimensions

_Written 2026-07-10. Integrates two owner artefacts into the knowledge-to-spec framework:
the DMBB Configurability Approach Spec (CP-1…10, two zones, INV-1…5) and the MTCA MDM
Register + Architecture Design (four buckets, four contracts, eight interoperability rules,
two mechanical conformance tests). Extends METHOD-upstream-knowledge-to-spec.md; feeds
PLAN v3 (M0/M3/V) and S2C-01 §4._

---

## 0. The reframe both sources force

The platform is not building standalone applications; it is building **modules of a
public-sector ecosystem** that must (a) keep working when policy changes — without
programming — and (b) mean the same thing as their neighbour modules. Both properties are
**decided at design time**, which makes them upstream capture concerns, not build concerns:

- **Configurability is the *change profile* of a decision.** The inventory today records a
  decision's answer, source, landing place and enforcement point. The configurability spec
  adds the missing axis: *who may change this answer later, at what cadence, under what
  governance* (CP-1 "partition by reason-to-change", CP-7 "governance follows the change
  source"). A captured value without a captured change-owner is only half a decision.
- **Interoperability is the *ownership scope* of a decision.** Dimension 4 today asks "which
  system owns this fact" as an open question per application. The MDM Register converts it
  into a **look-up**: every list and entity is classified R (shared reference) / M (master) /
  C (module config) / P (platform admin), with the Register as the allowlist. "No module
  defines shared vocabulary" (interop rule 8) becomes checkable at *spec* time — moving the
  ADD's own CI "register test" to the cheapest rung of the validation ladder.

Evidence check: this is not speculative. Re-reading the DM answer key, a large share of the
~53 improvised decisions were precisely these two axes handled implicitly — the instrument
catalogue and strategy admin the build invented ARE Zone B / Zone A (CP-6); the "cumulative
vs exact" Table-4 reading is structurally answered by matrix-cells-over-instrument-registry;
`dm_policy_admin` was CP-7 governance improvised on the spot; the risk score consumed from
outside is CP-5 / one-fact-one-writer. The DMBB build discovered the two-zone model
empirically; the spec now makes it method.

## 1. Two new interrogation dimensions (7 → 9)

### Dimension 8 — Policy & change (configurability)

For every rule, parameter, threshold, matrix, template and code list the interviews already
capture, the templates force a **change profile**:

| Slot | Question | Source of the rule |
|---|---|---|
| Operate-vs-define | Is this a lever someone pulls to manage the work, or a definition that is then selected? → Zone A / Zone B | CP-2 |
| Configurability class | Y (UI-configurable) / P (parameters only) / N (fixed — a change request, not configuration) | CP-9 |
| Change class | operating-policy / legal-instrument / channel / reporting / classification-context | §4.2 |
| Change owner + cadence | who changes it, how often, on what trigger (risk findings vs legislation vs channels vs MI needs) | CP-1, CP-7 |
| Governance | approval path; approval-gated? impact preview required (e.g. re-categorisation of live cases)? | §8 |
| Reference discipline | value defined once (Zone B), referenced by name; cross-seam values tagged `[operational]` | CP-3/4, §4.3 |
| Config effective-dating | is the configuration itself versioned with validity windows and rollback? | §8 |

**If skipped:** every policy change becomes a programming change request — the precise
failure the platform exists to remove; or worse, an ungoverned edit to a live matrix.

**Interview forms it takes:** "When the ministry changes the fee, who types the new number,
and who must approve before it takes effect?" · "If the threshold moves, what happens to
cases already categorised under the old one?" · "Is this something a manager tunes monthly,
or something legislation sets?"

### Dimension 9 — Ecosystem & shared meaning (interoperability / MDM)

For every entity, attribute, code list, and cross-module interaction:

| Slot | Question | Source of the rule |
|---|---|---|
| Bucket classification | R / M / C / P against the Register; FLAGged entries stop at TO-CONFIRM until their semantic question is resolved | Register §1, §7 |
| Fact ownership | which module writes this fact (one fact, one writer); everything else reads | ADD rule 1 |
| Binding | shared vocabulary bound to the kernel by reference — never embedded or copied | ADD principle 1, rule 8 |
| Consumption pattern | per reference domain: synchronous validate (boundary) / cached projection + change feed (screens) / frozen snapshot (immutable documents) | ADD §5.6 |
| Temporal contract | code + as-of date stored, never the label; historical positions reproducible | ADD §5.5 |
| Interaction contract | queries / commands / events only; events are facts, not instructions; schema versions declared on both sides | ADD rules 2, 3, 6 |
| Identifier spine | every cross-module message carries taxpayer id + tax type + period + idempotency + correlation keys | ADD rule 5 |
| Degradation | behaviour per unavailable dependency — merges with dimension 5's failure slots | ADD rule 7 |
| Crosswalks | mappings to external schemes named and owned (the `map` contract), never an analyst's spreadsheet | ADD §5.8 |

**If skipped:** "excellent connectivity and no interoperability" — modules exchange bytes
and disagree on meaning; N copies of tax-type lists diverge the day after go-live.

## 2. Decision-record extension (PLAN v3 · M0 delta)

Two optional-but-forced-by-dimension blocks on the record schema:

```yaml
change_profile:            # required for dimensions 2,3,8 decisions; waivable as N-fixed
  zone: A|B
  class: operating|legal|channel|reporting|classification
  configurability: Y|P|N
  owner: "DM management"
  cadence: frequent|occasional|rare
  governance: {approver: "...", impact_preview: true|false}
ecosystem:                 # required for every vocabulary/entity/interface decision
  bucket: R|M|C|P
  register_ref: "R-01"     # citation into the Register (the allowlist)
  binding: kernel|local
  consumption: sync|projection|snapshot
```

## 3. The ecosystem baseline — a new upstream input asset

The pack tier gains a sibling: the **ecosystem baseline** — organisation-level,
cross-module, consumed by *every* module's slice (where a domain pack is per-sector and a
slice is per-module):

- the **MDM Register** (ratified) — the R/M/C/P allowlist the dimension-9 classification
  cites; its §7 edge cases (split facets, the risk-vocabulary trap, reason-code
  pattern-not-merge, document-type boundary, org-unit home) are standing TO-CONFIRMs owned
  by the data governance office, not re-decided per module;
- the **canonical model** (TA-RDM) — the boundary vocabulary (`canonical_ref` already
  lands it in the schema);
- the **kernel contracts** — resolve / validate / subscribe / map (+ resolve_party) as
  catalog components with config contracts in the registry mirror;
- the **eight interoperability rules + conformance declaration** — the CAD template section
  every module inherits.

Tier rule (extends invariant 1): the method ships none of this; the *slots that demand it*
are method; the Register and canonical model are organisation assets; TA-RDM canon may ship
in the sector domain pack.

## 4. Landing places and enforcement (the custody chain, extended)

| Captured decision | Lands in (L1) | Enforced by |
|---|---|---|
| R-bucket vocabulary | `vocabularies` gains `binding: mdm:<domain>` (consume-by-reference; no local rows) | generators emit kernel-backed selects (projection pattern); **spec-time register test**: a local vocabulary matching an R-entry fails validation |
| Zone-B definitions (instruments, fees, templates-refs, SLAs) | config entities (`md*`/`mm_*`) with zone/class metadata | seed + generated admin console per zone; resolving index generated (CP-8: views, never authoritative copies) |
| Zone-A policy (matrix, escalation, allocation) | config entities referencing Zone-B **by id** | INV-1…5 as validate-time lint (dangling ref, restated value, matrix cell on unavailable instrument) |
| Governance of config change | a *case type*: config change with approval + impact preview + effective window | the existing case machinery (status-manager/approval) — configuration change becomes a governed lifecycle, same custody as any case |
| Events / queries / commands / spine | `interfaces` (+ event schema versions, idempotency/correlation keys) | emitters + the conformance declaration generated into the CAD |
| Degradation | dimension-5 slots per dependency | guards / adapter config |

The deep point: **CP-10 ("invariants are machine-checkable") and the ADD's two conformance
tests are the same move this platform already made everywhere else** — they slot into the
validation ladder as new L-rules, and the DM configurability pipeline's INV-1 catch
(`garnishment` vs `bank garnishing`) is exactly a citation-check finding.

## 5. Gate additions

Readiness gate gains three rows: every code list carries a bucket classification with a
Register citation or a FLAG-TO-CONFIRM; every Y/P parameter carries a complete change
profile (owner, cadence, governance); the interoperability conformance declaration is
complete (facts owned, interactions, degradation, spine). The realism gate is untouched —
but the **Register becomes a second external denominator**: `kit diff-reference` gets a
sibling check diffing a slice's vocabularies/entities against the Register (the "register
test" at authoring time).

## 6. PLAN v3 deltas

- **M0**: decision-record schema gains `change_profile` + `ecosystem` blocks (§2).
- **M3**: two new dimension templates (dim-policy-change.yaml, dim-ecosystem.yaml) phrased
  from §1's interview forms; the DMBB configurability spec's §11 worked example is the gold
  exemplar for dimension 8.
- **M2 pilot**: the elicit-mode pilot scores the two new dimensions too — how many of its
  decisions carry change profiles and bucket classifications without prompting.
- **V (schema v0.2)**: add `vocabularies[].binding` (kernel reference), zone/class metadata
  on config entities, event schema versions + spine keys on `interfaces`; new L-rules for
  INV-1…5 and the spec-time register test.
- **New (post-pilot): RMDBB alignment** — the kernel's four contracts enter the registry
  mirror as catalog components when CAD-MDBB lands; config-change-as-case-type built on the
  existing case machinery.

## 7. Open decisions for the owner (small, and they gate the rest)

1. **Ratify the Register's classifications** (it is v0.1 "proposed, not ratified") — the
   dimension-9 allowlist has authority only once its rows are VERIFIED; its §7 edge cases
   become standing TO-CONFIRMs with named owners.
2. **Ratify the two-zone model as an ADR** — the configurability spec is written to be
   citable; one ADR adopts CP-1…10 + INV-1…5 as platform rules (they already conform to the
   decision-brief discipline).
3. **Dimension count and naming** — accept 9 dimensions (7 + policy-change + ecosystem), or
   fold dimension 8 into an extended parameters discipline. Recommendation: 9 — binding
   time and ownership scope are orthogonal axes the DM evidence shows leak independently.
4. **Sequencing** — dimension templates cost days (M3); kernel-binding schema support is
   v0.2 scope; the RMDBB itself follows its own ADD/CAD path and is NOT a precondition for
   capturing the decisions correctly now (captured `binding: kernel` decisions compile to
   local projections until the kernel exists — the loss report records the deferral).

---

**Compression:** configurability and interoperability are not new pipeline stages — they are
two missing *columns on every decision*: who may change this later (binding time), and who
else must mean the same thing (ownership scope). The owner's two artefacts already supply
the classification frameworks and the machine-checkable rules; the method's job is to mount
them as dimensions 8 and 9, cite them from the decision record, and enforce them through
the same slots → citations → generators custody chain as everything else.
