# Decision brief — the decisions the method has surfaced (post-1.0)

_Method asset (A4/A5 pattern). These are the open TO-CONFIRMs the gated slices raised and did **not**
default — `kit toconfirm` across registration-foundation, F13-payments, and approval-gate. Ruling them
takes those slices to fully green. Each ruling becomes a one-line ADR._

- **Status:** Proposed — for the programme owner (with data governance) to rule.
- **Purpose:** clear the 3 open decisions blocking the gated designs; unblock the naïve→gated rebuild.

## Summary

| # | Decision (slice · dimension) | Recommendation | Owner | Urgency |
|---|---|---|---|---|
| 01 | Authority bands (approval-gate · interpretation) | Adopt the DM F09 bands as the standing default | DM delegation policy | High |
| 02 | Taxpayer master-data binding (registration · ecosystem) | Master (M), Registration-authored, kernel-bound-by-reference | Data governance | Med |
| 03 | Role/authority vocabulary (approval-gate · ecosystem) | Platform/IAM (P) — org roles, not an approval-local list | Data governance | Med |

---

## D-01 · Authority bands — materiality → required authority (DR-DMBBAPPROVAL-009)
**Problem.** The approval-gate references `requiredAuthority` but never states the band values, the
exact-vs-cumulative reading, or what covers the top band. This is the DM-F09 delegation-leak class,
now surfaced before build instead of invented in it.

**Options.**
- **Adopt the DM F09 / BR-DM-022 precedent as the standing default.** DMO < €20k / SDO < €100k /
  Director ≥ €100k; **exact-category** reading (a band gets its own authority, not lower ones);
  Director-and-above is the explicit top band. *Pro:* consistent with the existing DM delegation rule;
  a known, defensible baseline. *Con:* the numbers are policy and may need legal refresh. *Impact:*
  seeds the authority matrix as a Zone-B standing definition, effective-dated, legal sign-off.
- **Bespoke bands for approvals.** *Pro:* tuned to approvals. *Con:* a second, divergent delegation
  scheme to govern. *Impact:* more config surface, drift risk.

**Recommendation.** Adopt the DM F09 bands as the standing default (exact reading, Director top band),
effective-dated under legal sign-off; operators select routes, never redefine bands (ADR-062).

**Decision:** _______________________________________________

## D-02 · Taxpayer master-data binding (DR-REG-010)
**Problem.** The registration slice predates dimensions 8–9; is the taxpayer module-local or shared,
and how is party identity bound?

**Recommendation.** **Master data (bucket M)** — one authoring module (Registration), a reconciled
golden record; **bound to the shared kernel by reference** when the kernel exists, compiling to a local
projection until then (ADR-062 / PLAN v3 B4 — not a build precondition). *Impact:* closes DR-REG-010;
the taxpayer 360 and cross-module reads bind, never copy.

**Decision:** _______________________________________________

## D-03 · Role / authority vocabulary (DR-DMBBAPPROVAL-010)
**Problem.** Are the authority/rank levels (`required_authority`) an approval-local list or shared
organisation reference?

**Recommendation.** **Platform / IAM (bucket P)** — authority levels are organisational roles used by
every module that approves; owned by IAM/platform administration, not the approval module. FLAG to data
governance to confirm the IAM role vocabulary as the system-of-record (a register P-bucket entry).
*Impact:* closes DR-DMBBAPPROVAL-010; approvals consume roles from IAM, no local role list.

**Decision:** _______________________________________________

---

## What ruling this does
Each ruling → a one-line ADR; the three TO-CONFIRMs flip to VERIFIED/waived; registration-foundation,
F13 and approval-gate are all fully green. Then the honest next program is **applying the method to the
rest of the naïve DM/registration surface**, feature by feature (harvest where docs suffice, elicit
where the knowledge is in heads) — which is the whole point of everything built to 1.0.
