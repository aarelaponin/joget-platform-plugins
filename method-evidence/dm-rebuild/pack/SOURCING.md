# DM anchor — sourcing status and the one honest decision (ADR-044)

This DM anchor pack (v0.1) is **spec-grounded**: its procedure skeleton is cited to the real,
present MTCA DM requirements spec (`08-Debt_Management-Requirements_v1.1.md` — 58 DM-FR, 65 BR-DM,
20 WF-FR, 21 RPT-FR) and the DMBB feature set (F01–F14). Reconstruction closes internally: every
feature maps to a procedure, every procedure is sourced and reached.

## Why this is only v0.1
The registration anchor was **external** — cited to the IMF-DBM Annex 6 blocks, the Kyrgyzstan
13-process BPMN set, and the Ukraine STS. That externality is what let it detect procedures the *spec
itself* omitted (the §2.4 point: "a denominator you authored cannot detect a procedure you never
modelled"). A spec-grounded anchor is weaker: it catches **build-vs-spec** drift (e.g. the debt-case
envelope built as 3 of its 6 WF-FR-001 states — a real, caught divergence) but not **spec-vs-reality**
gaps.

## The one decision for the owner (do not let me fabricate this)
Upgrading the DM anchor to a genuinely external denominator requires **verified** external references.
Per ADR-044, I will not invent IMF-DBM debt-block ids or KG enforcement processes. The options:

1. **Owner supplies the DM primary sources** — as you did for registration (the IMF-DBM material, a
   country BPMN set for debt collection). I encode them; reconstruction upgrades to external.
2. **Authorise a bounded, cited web-sourcing session** — TADAT POA-7/8/9 (payments, arrears, disputes)
   and the IMF debt-management functional model — verify each reference exists and is publishable, then
   cite it (same discipline as ADR-044's publishability check).
3. **Proceed on the spec-grounded v0.1** — accept that DM coverage measures build-vs-spec, not
   spec-vs-reality, for now.

Until one of these is chosen, DM features gate on the **decision** gate (spec-lint / citation /
walkthrough — already running) and on this spec-grounded realism anchor; the external upgrade is a
named, non-fabricated TO-CONFIRM.
