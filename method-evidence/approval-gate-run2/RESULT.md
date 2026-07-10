# Run 2 — DMBB Approval-gate — runbook-driven, harvest mode (the G11 second run)

The formal "second consecutive run following the runbook, zero method edits" (PLAN v3 M5 / S2C-01
§7.4 criterion #6). Deliberately a **different feature shape** from F13-payments — approval requests,
authority bands, chain/quorum routing, delegation, separation-of-duties — to test that the method
generalises rather than fitting one feature.

## Zero method edits
Every step used the *identical* shipped tooling as the foundation slice and F13, with **no change to
any schema, template, tool or test**: `kit instantiate` (9 slots) → harvest from the F-cmApproval*
specs → `kit lint-decisions` → `kit spec-lint --allowlist` → `kit walkthrough --strict` →
`kit toconfirm`. The method handled a new shape unchanged — the stability the G11 test asks for.

## Gate state
- `lint-decisions` — 10 decisions (**8 VERIFIED · 2 TO_CONFIRM**), schema-valid.
- `spec-lint` — 1 gap (`U-INTERPRET authority_matrix`) + 1 weak (`U-FORMULA materiality`): both are the
  *open owner decisions*, correctly surfaced, not defaulted.
- `walkthrough --strict` — clean (8 scenarios, every step cites a decision).
- `toconfirm` — **2** owner-decisions surfaced.

## Measure — 2 decisions surfaced (single-digit; the leak class)
The spec was authoritative on the machinery (lifecycle + SoD, delegation-routes-not-authority,
exactly-once effect, grain, the inbox, route kinds) — all VERIFIED. It was silent on exactly the two
things that leak:
1. **The authority bands** (`materiality → required authority`; values, exact-vs-cumulative, top band).
   The DM F09/BR-DM-022 precedent (DMO<€20k / SDO<€100k / Director) is proposed but the values and
   reading are an owner ruling — the *same* interpretation-and-bands leak class the DM forced-slot
   evidence documented.
2. **The role/authority vocabulary** — is `required_authority` module-local or shared org/IAM
   reference data? A register-FLAG-class question routed to data governance.

Both were surfaced as owner questions before any generation, instead of invented at the keyboard.

## Verdict
Criterion #6 now rests on **three runs** — foundation (harvest), F13 (elicit), approval-gate (harvest)
— on unchanged tooling, each driving downstream decisions to single digits and surfacing the exact
silent-dimension class that sank Debt Management. The method is stable and general. The 2 surfaced
decisions are the next owner-facing brief (authority bands + role vocabulary).
