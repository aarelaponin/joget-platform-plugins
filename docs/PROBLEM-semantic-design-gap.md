# The semantic design gap — problem statement + analysis

_Companion to RESUME-NOTE.md. Written 2026-07-08. This is a strategy/methodology
document, not a build task. It records the core unsolved problem in the LLM-driven
tax-administration delivery pipeline and an analysis of it, so the thinking is not lost._

---

## Part A — Problem statement of record (author: Aare, 2026-07-08, verbatim)

We have demonstrated that an LLM-driven pipeline can reliably carry a specification
through the mechanical stages of delivery — one validated model → generated platform
artefacts → an app deployed, running, and populated on a real Joget DX9 instance, with
traceability and a repeatable one-command deploy. That half works.

The unsolved problem is the semantic stage that precedes it: turning a requirements
specification into a realistic use-case model and domain/data model. Today this is done
in a single, unreviewed LLM pass that optimises for "validates and generates" rather than
for fidelity to how the domain actually operates. The result is systematically naïve and
over-simplified — it collapses a real problem into the first, shallowest structure that
will compile.

Taxpayer registration is the worked example of the failure. In reality it is a family of
distinct procedures (initial registration per party type; tax-type registrations such as
VAT as separate, threshold-driven, effective-dated procedures; amendments;
inactivation/reactivation; deregistration; forced liquidation; enforced/event-driven
registration), governed by source-of-truth data rules and a real lifecycle — as evidenced
by the Kyrgyzstan process set (~13 BPMN processes) and the Ukraine/IMF-DBM functional
model. What was produced instead was a single intake flow, a flat list, VAT as a checkbox,
and a thin entity model with no Taxpayer-360, no obligations, and no lifecycle. It is not
usable.

The problem is therefore methodological, not incidental: there is no rigour, grounding, or
adversarial review on the design stages where realism is actually won or lost. Concretely,
the LLM (a) cannot yet derive a realistic use-case/process model from requirements, and
(b) within a use case cannot yet derive a realistic entity/data model — and nothing
currently catches this before code is generated.

The direction of the fix (planned, not yet built) is to move the same
verify/round-trip/traceability discipline that governs the mechanical half upstream onto
the design: explicit, evidence-grounded, LLM-assisted design artefacts (use-case/process
model with BPMN, then domain model) plus realism gates — coverage, domain-expert critique,
and reference-diff against real process libraries — that must pass before anything is
generated. The open questions are what "proven realistic" must formally mean, and which
external reference (IMF DBM / TADAT / GovStack) the realism check should be anchored to.

---

## Part B — Analysis (author: Claude, 2026-07-08)

**Bottom line:** the diagnosis is right in its main claim. I'd sharpen the mechanism in
three places, push back on two of the three proposed gates, and add one structural cause
the statement under-weights. (Note: the LLM that collapsed registration is the same kind of
system writing this analysis — so treat every "an LLM will…" below as self-implicating.)

### B1. Why the mechanical half works and the semantic half doesn't — the real mechanism

The downstream stages have a **crisp external oracle**: does it compile, deploy, pass
acceptance tests. The design stage has **no oracle**, so "validates and generates" becomes
the only objective available — and an LLM optimises exactly the objective it is given. This
is Goodhart's law, not laziness. Compounding it: an LLM **regresses to the modal, textbook
representation** of a concept. "Taxpayer registration" resolves to the tutorial version; the
irreducible domain content (13 procedures, thresholds, effective-dating, source-of-truth
ownership) is precisely the long-tail expert knowledge that gets averaged away. The failure
is therefore over-determined — no forcing function **and** a prior that pulls toward the
generic. Adding "rigour" without manufacturing an oracle just yields more confident
naïveté.

### B2. Push-back on the proposed gates

Of the three gates (coverage, domain-expert critique, reference-diff), **only reference-diff
has a real external oracle.**

- **Domain-expert critique done by an LLM persona is the model grading itself** — it will
  pass its own work. LLM-grading-LLM reproduces the blind spot that created the problem.
- **Coverage is only as good as the enumeration you started from** — it cannot discover a
  procedure you never modelled.
- **Reference-diff against a real process library is the load-bearing gate**, and a **cheap,
  structured *human* domain review** is the other irreducible one. Don't try to replace the
  human with a synthetic expert; instead make the human's job cheap — have the diff
  pre-populate a checklist the human signs.

### B3. The under-weighted cause: meta-model impoverishment (not just a bad pass)

What got generated modelled registration as a **transaction** (an intake flow) rather than
as **lifecycle management of a long-lived registered party and its tax-type registrations,
with obligations and effective-dated status**. That is a category error at the *meta-model*
level. If the abstractions the generator can express are "form + flat list + checkbox + thin
entity," then no amount of upstream review will produce Taxpayer-360 — the target vocabulary
literally cannot hold it. So part of the fix is giving the design stage a **richer ontology
to instantiate**: party / registration / tax-type-registration / obligation / effective-dated
status / source-of-truth binding / event-driven transition. Otherwise you review harder and
still emit the shallow thing, because that is all the pipeline can say.

### B4. The point that actually serves the stated goal ("configurable into any country")

Realism and configurability are in tension unless the design **explicitly separates two
layers**:

- the **country-invariant procedural skeleton** — every tax admin registers a party, adds
  tax-type registrations, amends, inactivates/reactivates, deregisters, enforces; and
- the **country-parameterised policy** — VAT threshold, which registrations are mandatory,
  effective-dating rules, forced-liquidation triggers, party-type taxonomy.

Kyrgyzstan's ~13 BPMN processes are *evidence of the skeleton plus one country's parameters*.
Reference-diff against them naïvely → you overfit to Kyrgyzstan; stay generic → today's
naïveté. **The real product thesis is: model the invariant skeleton as the use-case/domain
core, expose the policy as configuration, and make the realism gate check both** — coverage
of the skeleton *and* that every country-specific rule is a parameter, not a hardcode. That
dual check is the actual test that "realistic AND reconfigurable" has been met.

### B5. Anchoring is a stack, not a choice

- **TADAT** — outcome/maturity assessment; too coarse to diff a *design* against. Good for
  "are the functional areas covered" at POA level; useless for "is this process right."
- **IMF DBM / the Ukraine functional model** — the natural anchor for the use-case/process
  layer (the closest thing to a functional/process reference).
- **GovStack** — anchors the *interface and data-exchange* contracts (and the Registration
  Building Block), not domestic procedure semantics.
- **Concrete country BPMN sets** (Kyrgyzstan) — the "gold" you diff instances against.

Different layers; you need several, not one.

### B6. What "proven realistic" could concretely mean (a first definition to attack)

A design is realistic-enough when:

1. **Bidirectional coverage** — every procedure in the anchored reference process set maps to
   a modelled use case, and every modelled use case maps back (no missing procedures, no
   invented ones).
2. **Lifecycle fidelity** — every long-lived entity carries the lifecycle states and
   effective-dating the reference implies (not a flat record).
3. **Data-ownership explicitness** — source-of-truth / master-data rules are stated per field.
4. **Parameterisation** — every country-specific policy is a configuration point, not a
   hardcode (the B4 check).
5. **Human sign-off** — a domain reviewer signs a structured checklist that (1)–(4)
   pre-populate.

(1)–(4) are diffable against a reference (machine-checkable); (5) is the irreducible human
gate. Traceability today runs requirements→artefacts; this adds the missing **second
traceability axis: design ↔ external reference process set.** Tracing to the requirements doc
faithfully is worthless if realism is lost at requirements→design.

### B7. The counterweight (don't over-correct)

Do **not** swing into big-design-up-front BPMN — that reintroduces the cost the pipeline
exists to remove. The asset you have is that **generation is cheap**, which makes it
affordable to run the design against reality *many times*. So the fix is a **tight
adversarial loop** — draft design → reference-diff → structured human spot-critique →
regenerate — that fails fast and cheaply, preserving the speed advantage. The gate is a
fast filter, not an exhaustive one-time model.

---

## Part C — One-line compression

You are currently tracing requirements→artefacts faithfully, but realism is lost at
requirements→design. The fix is a **second traceability axis (design ↔ external reference
process set)** with a **real oracle on it (reference-diff + a cheap structured human gate)**,
plus a **meta-model rich enough to express a lifecycle** (not just forms + flat lists). The
author's instinct is correct; the flagged open question — "what does *proven-realistic* mean,
anchored to what" — is not a loose end, it is the whole problem.

## Part D — Suggested next step (not yet started)

Turn Part B into a concrete design for the upstream stage: (i) the meta-model/ontology the
design artefacts instantiate; (ii) the realism-gate checks B6(1)–(5) as executable rules;
(iii) how the reference-diff actually works (what reference is encoded, how a candidate
design is compared to it, what a "miss" looks like). Open the work by picking the anchor for
the process layer (recommendation: IMF DBM functional model for the skeleton, a country BPMN
set as the instance gold) and defining bidirectional coverage precisely.
