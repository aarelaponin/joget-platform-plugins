# Plan — S2C-01 v1.0: a proper, comprehensive, customer-grade architecture volume

_2026-07-10. Response to the v0.8 quality review. Two admitted defects with named causes,
a target design, a production-toolchain decision, QA gates, and a staged delivery plan._

---

## 1. Diagnosis — what went wrong, precisely

**D1 · Polish regression (production toolchain).** v0.6 was XML surgery on the original
Word file — its styling survived. v0.7/v0.8 were rebuilt via a markdown round-trip; when
the original's styles broke pandoc's reference mode, the build fell back to pandoc
*defaults* (borderless "Table" style, generic cover, default spacing) with only fonts
patched. Structure verified; polish never gated. **Lesson: a customer-grade document needs
a house renderer, not a converter's defaults.**

**D2 · Content asymmetry.** The volume is a downstream book with an upstream section
inserted: §4 ≈ 5 pages of upstream against ≈ 25 pages of Part II/III downstream, for a
platform whose stated frontier — and differentiator — is the upstream. The nine dimensions
get one table; the decision inventory gets three paragraphs; assets/gates/oracles share one
subsection each. The upstream deserves a full Part with the same depth the layer stack gets.

**D3 · Obsolete figure programme.** All seven legacy figures are downstream-era. Figure 1
("the platform on one page") shows repositories and layers only — nothing left of the model.
For customer discussion the figures ARE the argument; today they argue for the old product.

**D4 · Missing document QA gate.** No page-by-page customer-readiness review was run on the
volume (unlike the BA guide, where every figure was eyeballed and corrected through three
passes). The platform's own doctrine — every artefact gets a render-and-verify gate — was
not applied to its own architecture document.

**Interim guidance:** do not show v0.8 to a customer. v0.6 remains the most *polished*
copy (content current through 1.0 close-out, minus the upstream part); v1.0 below is the
discussable volume.

## 2. Target design — the v1.0 volume

**Purpose statement (unchanged):** one standing reference, readable by every stakeholder
(new team member, client architect, reviewer, donor), plain-language culture, glossary-backed.

**New structure — upstream promoted to a full Part; balance ≈ equal page share:**

| Part | Sections | Content |
|---|---|---|
| I · Orientation | 1 Purpose & reading paths · 2 The core idea + the evidence (§2.4 as-is) · 3 The platform on one page | §3 rebuilt around the NEW Figure 1: the full knowledge→spec→code→ecosystem chain, four asset tiers + ecosystem baseline |
| II · The upstream: knowledge → specification | 4 The method & the decision inventory · 5 The nine dimensions (one subsection each, with example + "if skipped") · 6 Artifacts & playback (design folder, templates, walkthroughs, scenarios) · 7 Gates, oracles & assets (readiness, realism/anchors, register test; packs, baseline, exemplars) | expanded from METHOD + METHOD-EXT; ≈ 14–16 pages; dimension 8 gets the two-zone figure, dimension 9 the kernel/ecosystem figure |
| III · The downstream: specification → system | 8 Layer 1 (keystone) · 9 Layer 2 (projections) · 10 Layer 3 (artefacts & deploy) · 11 The loop | current §5–§8, lightly compressed |
| IV · The machinery | 12 Validation ladder & deltas · 13 Component ecosystem (catalog + UX pattern shelf + kernel contracts) · 14 Assurance (acceptance, TRACE, drift) · 15 Governance & versioning · 16 Repository strategy | current §9–§13 reorganised |
| V · Synthesis | 17 Invariants (incl. a tenth: ecosystem/one-fact-one-writer) · 18 Honest inventory · 19 Decision register · 20 Roadmap | current §14–§17 |
| Appendices | A Projection map · B Glossary · C RegBB pattern | as now |

**Figure programme (the core of customer-readiness):**

| # | Figure | Status |
|---|---|---|
| F1 | **The platform on one page — full chain**: business knowledge → decision inventory (gated) → validated model → generated, enforced application → module in the ecosystem; custody safeguards annotated; four asset tiers beneath | **NEW — the flagship** |
| F2 | Decision journey (3 leaks / 3 safeguards) | exists (BA-guide style) |
| F3 | Working cycle (one procedure at a time) | exists (BA-guide style) |
| F4 | The nine dimensions — one wheel/band graphic with landing places | NEW |
| F5 | Gates & oracles chain: slots → consistency lint → realism diff (anchor) → register test → readiness signature → loss report | NEW |
| F6 | Asset tiers: method / platform / domain packs / ecosystem baseline / instances, with dependency arrows | NEW |
| F7 | Two-zone configurability (operating vs standing, resolving index, matrix-over-registry) | NEW |
| F8 | Ecosystem & kernel: modules, four contracts, identifier spine, consumption patterns | NEW |
| F9–F13 | Layered pipeline · loop two speeds · validation ladder · component lifecycle · drift triangle | keep (legacy, still correct) |
| C-1 | RegBB channel | keep in Appendix C |

All new figures via the house diagram style (matplotlib, 300 dpi, 10-inch canvas, the
docx-diagram-style rules) — the BA guide proved the pipeline and the look.

## 3. Production toolchain decision — the polish fix

Three options were considered:

- **(a) pandoc + hand-crafted reference.docx.** Fix the reference template once. *Rejected
  as primary:* pandoc's docx writer confines all tables to one style, offers weak control
  over cover/captions, and the failure mode (silent style fallback) is exactly what burned
  v0.7/0.8.
- **(b) House renderer: markdown source → docx-js build (the BA-guide pipeline).** Full
  programmatic control of styles: bordered/shaded tables, cover layout, headers/footers,
  TOC field, caption numbering, callout boxes. Proven output quality in this programme
  (the analyst guide). The volume becomes **document-as-code** — one markdown source of
  truth plus a style module; every future version is a rebuild, not a conversion. *Chosen.*
- **(c) Continue XML surgery on the original lineage.** Preserves polish, cannot carry a
  restructure of this scale. Rejected.

The renderer is a one-time investment (`tools-docs/render_s2c.js` + `house_style.js`,
ported from the BA-guide build): heading hierarchy with the blue accent, Arial body, table
style WITH visible borders and shaded header rows, figure+caption blocks with automatic
numbering, status-glyph handling (● ◐ ○), the cover block, A4 geometry, footer with
version + page number, dirty TOC field. It is reusable for every future volume — the same
document-as-code doctrine the platform preaches.

## 4. QA gates (D4's fix — permanent, not one-off)

Adopt the cd-report-qa pattern for this volume, run after every build:

1. **Automated checks:** every §-reference resolves post-renumbering; figure numbers
   sequential and every figure cited in body; glossary covers every bolded term; status
   glyphs consistent with `1.0-readiness.md`; no orphan headings; no unresolved
   placeholders.
2. **Full render review:** convert to PDF, render EVERY page, fresh-eyes pass (subagent)
   with an explicit customer-readiness checklist — table borders visible, no text
   collisions, figures legible at print size, consistent fonts, cover correct, captions
   under figures, page breaks sane.
3. **One fix cycle,** then owner read-through.

## 5. Staged delivery

| Stage | Work | Verify (exit) | Effort |
|---|---|---|---|
| S1 | Owner ratifies this plan: structure table, figure list, renderer choice | rulings on the three tables above (annotate and return) | 30 min owner |
| S2 | House renderer + style module; render a 3-page golden sample (cover + one prose section + one table-heavy section) | owner approves the sample's look | ~1 session |
| S3 | New figures F1, F4–F8 in house diagram style | each figure eyeballed, no overlaps; F1 demonstrably covers knowledge→spec→code→ecosystem | ~1 session |
| S4 | Content: write Part II (sections 4–7) at full depth from METHOD + METHOD-EXT; compress/reorganise Parts III–V; renumber & fix references | source markdown complete; automated checks green | ~1–2 sessions |
| S5 | Full build + QA gates (§4) + one fix cycle | every page passes the customer-readiness checklist | ~0.5 session |
| S6 | Owner read-through → tag v1.0.0; archive v0.6–v0.8 per one-canonical-source | v1.0.0 is the only live copy | owner |

Dependencies: S2 ∥ S3; S4 needs neither but S5 needs all. Total: ~3–4 working sessions
after S1.

## 6. What this changes in standing practice

- **A document lint enters the toolchain**: the §4 checks become a script, run in CI like
  everything else — the volume stops being the one artefact exempt from the platform's own
  discipline.
- **PLAN v3 gains task H5**: house renderer + document QA as hygiene-tier deliverables
  (they serve every future volume, the BA guide, and the conformance pack).
- **The markdown source is canonical** (`_docs/src/S2C-01.md` + figures directory); the
  .docx is a build artefact — generated, never hand-edited. The same rule as everywhere
  else, finally applied to the architecture document itself.
