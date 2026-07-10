# Decision brief — the DM rebuild tail (batch)

_All 11 DM procedures are now gated slices. `kit toconfirm` across the tail surfaced these 5
owner-decisions (everything else harvested VERIFIED, much of it reusing decisions you already
ratified). Ruling them takes Debt Management fully gated._

- **Status:** Proposed — for the owner (with legal / accounting / MI) to rule. Say "confirm" to accept
  all, or correct by id.

| # | Slice · id | Recommendation |
|---|---|---|
| 1 | write-off · DR-WOFF-004 (reversal-on-payment) | **Reverse the write-off and reinstate the debt as settled** — cleanest audit trail; the debt was never truly extinguished, so a late payment un-writes it off rather than creating a floating credit. (This closes the one item EVIDENCE-DM recorded as *still open today*.) |
| 2 | default-assessment · DR-DA-002 (basis + cap) | Priority order **prior-year → industry-average → formula**; cap at **prior-year × 150%** unless a documented justification. |
| 3 | objection-handling · DR-OBJ-003 (dispute SLA) | A **90-day** administrative review target (aligns with the TADAT P7-24 good-practice measure). |
| 4 | collection-mi · DR-MI-002 (KPI set) | Adopt the **12 spec KPIs (#24–35)** with standard definitions (collection efficiency, arrears-to-revenue ratio, resolution rate, recovery rate, days-since-last-action…), each with an explicit numerator/denominator/population/period — MI to sign the exact formulas. |
| 5 | collection-mi · DR-MI-003 (RAG bands) | Standard green/amber/red thresholds per KPI (e.g. collection rate > 90% green / 80–90% amber / < 80% red), MI-tuned. |

## What ruling this does
Each ruling → VERIFIED; the four remaining slices go green; with debt-case, escalation, enforcement,
instalments, debt-categorisation, contact-visit already green and payment-application gated by the F13
pilot, **Debt Management reaches 11/11 procedures gated** against the external TADAT anchor. The naïve
DM surface — the 6-vs-3 envelope, the unreconciled grain, the guessed enforcement matrix, the uncited
instalment values, the invented KPIs — is then fully replaced by a gated, cited, realism-anchored design.
