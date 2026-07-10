# Decision brief — A4 · Ratify the Master & Reference Data Register (MTCA-MDR-001)

_Method asset. Pattern proven by `joget-spec-kit/docs/1.0-structural-decisions.md` (ADR-045…054)._

- **Status:** Ratified — all three sub-decisions ruled **Accepted** by the owner 2026-07-10;
  registered as ADR-061. (Frozen; rationale archive behind its ADR.)
- **Owner of the decision:** Aare (programme owner), with the data-governance office as standing owner
  of the residual questions.
- **Purpose:** Give **dimension 9 (ecosystem & shared meaning)** its allowlist. The register
  (MTCA-MDR-001) is *inventory-complete with classifications proposed, not ratified*. Ratifying moves
  the R/M/C/P buckets and dispositions from "proposed" to authoritative, so the machine-readable
  allowlist (E1) has authority and the M1 **spec-time register test** (a local vocabulary that
  duplicates an R-classified entry fails lint) can resolve against it. Unblocks E1 and the M1 register
  test.
- **Key point:** most of the register is uncontroversial and ready to ratify now. The value of this
  brief is separating the **rows to ratify** from the **FLAG rows** that must become owned questions,
  not silent merges — the chain-of-custody rule applied to the register itself.

## Summary

| # | Decision | Recommendation | Needs code now? | Urgency |
|---|---|---|---|---|
| 01 | Adopt the four-bucket (R/M/C/P) classification + dispositions as the dim-9 allowlist | Accept; ratify all non-FLAG rows | No | Strategic |
| 02 | The six FLAG / edge rows → standing TO-CONFIRMs with named owners (not auto-merged) | Accept the list; assign owners + a date | No | High |
| 03 | Home of the machine-readable `allowlist.yaml` (E1) | Org-tier folder in the evidence repo | Yes (E1) | Med |

---

## D-A4-01 · Adopt the register as the dimension-9 allowlist

**Problem.** Dimension 9 "rests on the organisation's register as an allowlist" (S2C-01 §5): a list two+
modules need with one meaning is shared reference data (R), bound to the kernel by reference; a cross-
system entity is master data (M); a module-only list stays federated config (C); cross-cutting machinery
is platform (P). MTCA-MDR-001 already inventories every catalogue/master/config list across the 13
module specs, buckets each, names a candidate system-of-record, and counts cross-module footprint — but
its classifications are *proposed*. Until ratified, the E1 allowlist has no authority and the M1 register
test has nothing to resolve against.

**Options.**
- **Ratify all non-FLAG rows now; buckets + dispositions authoritative.** *Pro:* the register is
  thorough and evidence-based (footprint-counted, reconciled to TA-RDM `ref_*`); the vast majority
  (R-04 currency, R-07 NACE, R-09 country, R-16/17 accounting vocab, the M/P rows) are not in dispute.
  *Con:* commits SoR assignments before the shared kernel (RMDBB) exists. *Impact:* the register becomes
  the dim-9 allowlist; E1 generates `allowlist.yaml` from it; kernel-bound decisions still compile to
  local projections until B4 (no build precondition).
- **Wait for CAD-MDBB / the kernel.** *Pro:* ratify once, with the component design. *Con:* blocks E1
  and the M1 register test indefinitely; the register is the *input* to CAD-MDBB, not the reverse.
  *Impact:* dimension 9 stays un-authored.

**Recommendation.** **Ratify all non-FLAG rows now.** The register is the binding baseline dimension 9
needs; SoR assignments are "candidate → binding" for the CENTRALISE rows, realised as import adapters for
the external-authority sources (ISO, legislation, BNS, Treasury). The kernel is not a precondition
(S2C-01 §5; PLAN v3 B4).

**Decision:** Accepted (owner, 2026-07-10) → ADR-061.

---

## D-A4-02 · The FLAG / edge rows become standing TO-CONFIRMs (not silent merges)

**Problem.** Six rows carry a semantic question that must **not** be resolved by a blind merge — merging
them would repeat the §2.4 silent-default failure at the register level. They are exactly the cases where
"code lists that diverge the day after go-live" hide:

- **R-13 · risk-vocabulary family** — refund band ≠ audit tier ≠ enforcement category C1–C5. *FLAG: do
  not merge blindly*; centralise only the genuinely shared scoring vocabulary.
- **R-14 · reason-code family** — 7 modules, N independent `md*Reason` forms. *FLAG → pattern, not
  merge*: one catalogue partitioned by domain+context, governed centrally, scoped locally.
- **R-03 · document type** — CENTRALISE but *FLAG the DMS boundary*: the DMS taxonomy and the `ref_`
  list must reconcile to one owner.
- **R-08 · service/contact/payment channel** — *FLAG then centralise*: confirm one taxonomy with a
  channel-class vs two related lists.
- **R-01/R-02/R-06/R-15 · split facets** — SPLIT: code/list facet → MDBB, rates/periods/rules stay in
  the owning domain. The split line itself needs sign-off.
- **R-12 · org unit / tax office** — master-ref *hybrid* (an entity that behaves as a shared dimension);
  its home (MDM vs RDM) is the edge.

**Options.**
- **Convert each FLAG to a standing TO-CONFIRM with a named owner + a target date;** ratify everything
  else. *Pro:* honours chain of custody — the hard questions become visible owner-facing items, not
  defaults; unblocks the ~37 clean rows immediately. *Con:* the register is "ratified with residue"
  rather than fully closed. *Impact:* the six become `TO_CONFIRM` records owned by the data-governance
  office; the M1 register test treats a FLAG row as "resolve or FLAG", never as a merge target.
- **Rule each FLAG now.** *Pro:* fully closed register. *Con:* these need domain analysis (risk,
  correspondence, DMS) the owner may not want to settle in one sitting; premature merges are expensive to
  unwind. *Impact:* higher risk of a wrong merge.

**Recommendation.** **Convert the six to owned TO-CONFIRMs; ratify the rest.** This is the register-level
form of the method's spine — an unmade decision becomes a visible owner item, not a silent merge.

**Decision:** Accepted (owner, 2026-07-10) → ADR-061.

---

## D-A4-03 · Home of the machine-readable allowlist (E1)

**Problem.** E1 generates `allowlist.yaml` (id, name, bucket, disposition, SoR, register_ref) from the
ratified register, plus the FLAG rows as TO-CONFIRMs and the kernel-contract stubs. It is org-tier input,
not sector-pack-tier — where should it live so both the tax pack and future sector packs cite it?

**Recommendation.** **An organisation-tier folder in the evidence repo** (e.g. `org/mdm/allowlist.yaml`),
above the `tax/` sector folder. It is domain/organisation content (invariant #1: not the kit), and the
M1 register test resolves against it cross-repo, exactly like the realism anchor. The owner may relocate
it later without affecting the tools (they take a path).

**Decision:** Accepted (owner, 2026-07-10) → ADR-061.

---

## What ratifying this brief does

Each ruling becomes a one-line ADR (proposed ADR-060 for the batch). The register becomes the dimension-9
allowlist; E1 builds `allowlist.yaml` from the ratified rows; the six FLAG rows become owned TO-CONFIRMs;
and the M1 spec-time register test gains something authoritative to resolve against. Nothing here needs
platform code before E1/M1 — D-A4-01 and D-A4-02 are governance rulings; D-A4-03 sets a path.
