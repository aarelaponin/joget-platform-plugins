# Review remarks — PLAN-consolidation-and-upstream.md

_Written 2026-07-08. Consolidated review of the executable plan (and S2C-01 v0.5 where it
governs the plan). One remark per issue; each ends with a **proposed edit** so the plan can
be amended mechanically. Verdict up front: the plan is sound, honest, and correctly ordered —
"prove by doing one real thing, then extract the method" is right. The remarks below are
holes in *verify criteria* and one topology correction, not disagreements with the shape._

---

## R0 — The product is the method, not the tax solution (topology correction)

The product is **LLM-assisted public-sector digital transformation delivery — sector-neutral**.
Tax administration is the *first testbed*, chosen because the author can judge realism there.
Tomorrow the same method runs health, land, licensing. This changes the topology in §1:

- `ta-ref-arch` is **evidence-and-instance content**, not a solution product. C1 (version it,
  private, client-data .gitignore) stands, but its role in the MANIFEST must read
  "testbed/program instance," never "the product."
- **The plan's two-tier split (kit / plugins) needs a third tier: domain reference packs.**
  G6 currently homes the registration skeleton in "plugins (kit)" — ambiguous, and both are
  wrong: the kit ships zero domain content (its own invariant 1), and the plugin shelf is
  runtime Java, not domain knowledge. The tax skeleton, fragment library, domain checklist,
  anchor sets and the gold exemplar form a **domain reference pack** (`ref-pack-tax-registration`
  or similar): versioned, citable, reusable across country programs, *replaceable* by a health
  or land pack without touching the method.
- Consequence for the upstream skills (R6): skills must be **sector-neutral method** — they
  take a domain pack as *input data*. A skill that hardcodes "VAT" has leaked testbed into
  product — same category error as a kit shipping project names.

**Proposed edit:** §1 topology table gains a row: *domain reference packs — the anchor sets,
skeletons, fragments, checklists, exemplars per sector; consumed by the method skills; tax
registration is the first.* G6's repo column changes to the new pack. MANIFEST (C0) records
the tier of every asset: method / platform / domain pack / instance.

## R1 — G10 cannot pass as written (dependency hole: schema 0.2)

G10's verify demands a **Taxpayer-360 view** and **VAT with threshold + effective dates**.
No task delivers the machinery: G2 covers lifecycle only; schema 0.2 (effective-dated
attribute primitive, 360/detail composite, procedure-as-first-class or its documented
mapping) sits in the S2C-01 §16 register with **no G-task owning it**. As written, G10 gets
met the naive way — a "360" that is a plain form, effective dates as unenforced text fields.

**Proposed edit:** new task **G8.5** (kit): *schema 0.2 minimal + projector support, scoped
to exactly what the G9 design requires — effective-dating and the 360 view first; nothing
speculative.* Depends: G9 design draft (so scope is demand-driven); blocks G10. Verify:
the G9 model expresses effective-dated VAT registration and a 360 view **in schema**, and
the projectors emit their realization (not a silent flatten).

## R2 — G6's skeleton has no oracle (the load-bearing asset is self-reviewed)

The skeleton is the single most load-bearing domain asset, and its only gate is a signature
from the same person who curated the references. The averaging failure that produced the
naive model can recur at skeleton-authoring time.

**Proposed edit:** G6 verify becomes mechanical + attested: (a) **extractive with citations** —
every skeleton procedure cites its evidencing DBM block + KG process + UA annex (the S0
DRAFT→VERIFIED rule applies to the skeleton itself); (b) **bidirectional reconstruction** —
the skeleton must map every KG-13 and UA-5 item to a skeleton procedure and every skeleton
procedure back to ≥1 source item, mechanically checked; (c) the signature then attests only
the residual judgment (naming, granularity, what is invariant vs parameter).

## R3 — G7 proves detection of missing procedures, not shallow ones

The negative control (naive model → gaps) and positive control (skeleton → clean) are good,
but they only prove the diff catches *absence*. The historical failure was also *shallowness*:
VAT present but as a checkbox. "What a miss looks like" (PROBLEM doc Part D) is still unpinned.

**Proposed edit:** add a third control to G7's verify: a **deliberately subtly-wrong model**
(VAT as a procedure but not effective-dated; lifecycle present but missing reactivation) must
FAIL the diff. Pin the diff granularity explicitly in MAPPING form: level 1 procedure
existence · level 2 step/lane structure · level 3 lifecycle states · level 4 data slots
(effective dates, thresholds, SoT bindings). State which levels gate and which advise.

## R4 — Two Annex-B mechanisms fell out of G8

(a) The **interrogation protocol** (questions-first; unanswered → TO-CONFIRM register routed
to the human) is not in G8 — it is what converts silent simplification into a visible open
question ("is VAT a field or a procedure?"). (b) The **mandatory loss report** on the
design→L1 compile is nowhere — without it, whatever the schema cannot express is silently
flattened again: the Gap-3 pattern one level up.

**Proposed edit:** G8 verify adds: *the S0 skill emits a TO-CONFIRM register and blocks S1
on unanswered blocking questions; the compile-to-L1 step emits a loss report; a non-empty
loss report without corresponding named schema gaps fails the gate.*

## R5 — Tie the realism gate to the runtime proof (per-procedure scenarios)

G10 says "acceptance scenarios assert transitions in SQL," but nothing forces scenario
*coverage* of the design: a procedure can pass the realism gate and ship untested.

**Proposed edit:** G9 verify adds: *every in-scope skeleton procedure yields ≥1 acceptance
scenario asserting its lifecycle path (given/when/then over the declared transitions).*
This makes the design↔reference axis and the runtime proof meet in one artifact.

## R6 — G8/C6 concrete shape: six sector-neutral upstream skills

Institutionalise each upstream artifact as one skill owning its template, quota, check
script and stop rule (the established kit pattern). Lean v0 scaffolds now, hardened during
G9 — per the plan's own extract-from-the-slice principle. All six are sector-neutral; the
domain pack (R0) is their input.

| Skill | Stage | Owns | Stop rule |
|---|---|---|---|
| s2c-evidence-harvest | S0 | evidence questionnaire w/ quotas, citations, DRAFT→VERIFIED tracker, TO-CONFIRM register | DRAFT evidence cannot feed S1 |
| s2c-procedure-catalogue | S1a | skeleton instantiation: in/out/adapted per procedure + citation + parameters | skipping a procedure = explicit out-of-scope entry (→ waiver) |
| s2c-use-case-model | S1b | one procedure per pass, evidence-only context; UC spec + BPMN-light; required slots (triggers, lanes, outcome→transition, exception loop) | a UC without outcome→lifecycle mapping is incomplete |
| s2c-domain-model | S2 | fragment composition; every attribute names its source; lifecycle-or-waiver per long-lived entity; parameterisation table | unsourced attribute = structurally incomplete |
| s2c-realism-gate | S3 | wraps `kit diff-reference`; waiver checklist; slot checks; human sign-off; LLM critique advisory-only | no generation before signed gate |
| s2c-compile-to-l1 | S4 entry | design→`.app.yaml` + **loss report**; inexpressibles → named schema gaps | non-empty loss report without schema-gap entries fails |

**Proposed edit:** C6/G8 reference this table as the skill inventory; the skills live in the
kit (method tier), their templates cite the domain pack by version.

## R7 — Process items

1. **C1 is blocked on one human decision** (remote: private SSH vs local-only) — the only
   human action gating the whole C-series; close it first.
2. **Single-reviewer bottleneck:** G6/G7/G9 all gate on one person. Acceptable now; the
   method's stated audience (low-exposure teams) eventually needs the reviewer role
   teachable — the checklists ARE that training material; write them as if for a successor.
3. **Fix the artifact vocabulary once** (glossary entry in S2C-01 Appendix B): *procedure*
   (skeleton unit) · *procedure catalogue* (S1a) · *use-case model / UCM* (S1b set) ·
   *use case* (one procedure's spec) · *domain model* (S2) · *evidence pack* (S0) ·
   *loss report* (compile). The plan and skills must use these names verbatim — naming
   drift across artifacts is the same defect procurement-qa exists to catch.
4. **Horizon (not for this plan):** the real product test of R0 is a second *domain*
   (health/land), not the second procedure of G11. Record as a §16-style open item so it
   shapes naming now but costs nothing yet.

---

## Summary of proposed plan amendments

| Target | Change |
|---|---|
| §1 topology | + domain-reference-pack tier; ta-ref-arch = testbed instance; MANIFEST records tier per asset |
| G6 | home = domain pack; verify = citations per procedure + bidirectional reconstruction + attested residual |
| G7 | + subtly-wrong third control; diff granularity levels 1–4 pinned, gate-vs-advise stated |
| G8 | + interrogation/TO-CONFIRM; + loss-report rule; skill inventory per R6 |
| **G8.5 (new)** | schema 0.2 minimal (effective-dating, 360) + projector support, demand-scoped by G9; blocks G10 |
| G9 | + per-procedure acceptance-scenario coverage rule |
| C0 | MANIFEST records method/platform/domain-pack/instance tier |
| C1 | flag as the single human decision blocking the C-series |
| C6 | reference the R6 skill table |
| Glossary | fix the upstream artifact vocabulary (R7.3) |
