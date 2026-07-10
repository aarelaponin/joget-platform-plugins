# UX pattern shelf (v0)

Dimension 7 (work & workspace) splits in two (S2C-01 §5): the *per-application* half is elicited
into the decision inventory; the *platform-invariant* half — how a worklist behaves, how a
360-view is composed, badge and message conventions — lives **here**, baked into the generators.
Every application inherits professional interaction design, and **a usability defect is fixed once,
in the pattern, never per screen** (ADR-050's maturity ladder applies: a pattern is `stable` once
proven in >=2 contexts, else `provisional`).

## The rule
Generators consume these patterns; screens are outputs. When QA finds a usability defect, the fix
goes to the pattern (and the generator that emits it), then everything regenerates — never a
hand-edit to a generated screen (invariant #2). This shelf was **seeded by harvesting the
DMBB-UX-QA remediation** (FIS DMBB-UX-QA, tests T-23.1..5 green on jdx9), which corrected lists
that were sortless, filterless, drill-less and carried an orphan child-list menu.

## Pattern format
Each `pattern-*.yaml`: `id`, `name`, `applies_to` (datalist|userview|form|dashboard), `rule`
(what every generated artefact must do), `realised_by` (the generator + mechanism), `maturity`,
`source`, and `deferred` (honest scope).

## Shelf
| Pattern | Applies to | Maturity |
|---|---|---|
| pattern-sortable-columns | datalist | stable |
| pattern-typed-filters | datalist | stable |
| pattern-summary-to-detail-drill | datalist | stable |
| pattern-detail-to-record-drill | datalist | stable |
| pattern-no-orphan-child-list | userview | stable |
