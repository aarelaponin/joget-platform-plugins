# Documentation

This folder is the home for the platform's important documents. The rule is simple:

**Canonical docs live here as Markdown, and render on GitHub — with their figures.**
Diagrams are committed as PNGs under `img/` (regenerable from Graphviz sources in `img/src/`), and
the Markdown embeds them, so anyone browsing the repo on GitHub sees the full content and pictures
in the browser — no download needed.

**Polished binary renders (DOCX / PDF) are NOT committed.** They are delivery artifacts, published
as **GitHub Release assets** when a version is cut (and git-ignored in the tree). This keeps the
repo diff-able and light while still giving stakeholders a formatted document to circulate.

## Index

| Document | What it is |
|---|---|
| [VISION.md](VISION.md) | The vision — why plugins become a platform, what it is, how it works (with figures) |
| [CONSOLIDATION-PLAN.md](CONSOLIDATION-PLAN.md) | The program plan — phases, status, taxonomy, roadmap, risks, decisions |
| [MIGRATION-BACKLOG.md](MIGRATION-BACKLOG.md) | Per-plugin migration list, tiers, provenance rules |
| [DAS-EXTRACTION-PLAN.md](DAS-EXTRACTION-PLAN.md) | How the approval service comes out of the case bundle (effects-coupling inversion) |
| [registry.schema.yaml](registry.schema.yaml) | Schema for `../registry.yaml` entries |

## Figures

`img/*.png` are the rendered diagrams (committed so GitHub shows them). `img/src/*.dot` are the
Graphviz sources. Regenerate any figure with:

```bash
dot -Tpng -Gdpi=160 img/src/<name>.dot -o img/<name>.png
```

## Producing a delivery render (DOCX/PDF)

Build from the Markdown when a formatted copy is needed for circulation, then attach it to a GitHub
Release rather than committing it:

```bash
# example: pandoc VISION.md -o VISION.docx   (then upload as a Release asset)
```
