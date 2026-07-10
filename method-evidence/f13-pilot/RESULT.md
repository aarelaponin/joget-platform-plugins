# M2 elicit-mode pilot — F13-payments — result

The load-bearing grade (PLAN v3 M2): run the whole upstream method, in **elicit mode**, on a real
DMBB feature that had **no FIS** (its design decisions were never recorded), and measure how many
decisions the method forces into the open before generation versus the number that normally leak
into the build. Elicited from the owner (AL) over two interview rounds, 2026-07-10.

## The gate — GREEN
- `kit lint-decisions` — 14 decisions, **13 VERIFIED · 1 WAIVED**, schema-valid.
- `kit spec-lint --allowlist` — **0 gaps, 0 weak** across all nine dimensions.
- `kit walkthrough --strict` — clean; **every scenario step cites a decision id** (12 scenarios).
- `kit citation-check` — **100% cited-or-assumption** on the as-built realization.

## The measurement
| Measure (PLAN v3 M2) | Result |
|---|---|
| (a) Domain decisions left downstream of the gate | **0** (all 14 resolved upstream; baseline ≈3/feature, DM module ≈53) |
| Decisions the artifact (F-dmPayment) actually answered | ~2 (allocation order, full-settlement cascade) |
| Decisions the method **forced into the open** | **12** — of which **6 the spec was wholly silent on** |
| (b) Citation coverage | 100% |
| (c) Gate-to-green | 2 interview rounds |
| (e) Ecosystem capture | payment_method bound to register **R-05** (not copied); both Y/P params carry a change profile |

**The six the "comprehensive" artifact never decided** — exactly the §2.4 leak class: overpayment
handling (credit on account), payment reversal + debt/case restore, the `part_settled` state,
no-case/multi-case routing, CB/BANK pending-until-confirmation, and PaymentEngine-down behaviour.
Each would have been invented at the keyboard; the method surfaced all six as owner questions first.

## The register test earned its keep
`payment_method` (CB/BANK/CASH) looks module-local, but it is **R-05 "Payment method"** in the
ratified MDM register. Recorded as a local C-bucket copy, `spec-lint` fires:
`U-REGISTER … local (C) vocabulary duplicates ratified register entry R-05 — bind, do not copy`.
Bound to R-05 (kernel/projection) as decided, it is clean. The dimension-9 lesson, enforced.

## Friction → skills v1 (feeds M5)
1. **Elicit = harvest-then-confirm.** Pre-filling DRAFT answers from the existing artifact and having
   the owner confirm/correct was far faster than a blank interview. Skills v1: elicit mode should
   always pre-populate from any available artifact, then confirm — not start empty.
2. **"Confirm" is under-determined for genuine gaps.** A blanket confirm settles spec-implied answers
   but not either/or gaps; the loop must re-surface those with a specific recommendation each (which
   it did). This is the method working, and a v1 interview-script rule.
3. **Author via `kit instantiate`, not hand-editing.** A hand-appended block produced a duplicate
   `scenario_refs` key; `kit walkthrough --strict` caught it immediately. v1: fill the instantiated
   skeleton in place; never hand-append decisions.

## Verdict
Elicit mode is **proven** on a real feature: the method drove downstream decisions to **zero**,
surfaced the exact silent-dimension class that sank DM, and enforced the register binding. Remaining
to fully close M-series: M5 (runbook + skills v1 + a second run to second criterion #6), then A3's
1.0 tag.
