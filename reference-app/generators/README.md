# Reference generators

Project-neutral reference implementations of the Joget artefact generators, kept here in
the canonical plugin library so that spec-to-code projectors (joget-spec-kit) can
round-trip against a clean oracle.

## Why these exist

Per-project delivery repos each carry their own generator that forks the neutral shape and
adds project policy — category buckets, role maps, per-form overrides, theme JavaScript, a
project-namespaced uuid seed. Those forks produce valid Joget JSON but embed project content,
so a projector round-tripped against them would inherit that content. These reference
generators keep ONLY the project-neutral structural core (the JSON shapes, extracted from a
production-proven delivery generator) and leave the policy out.

A projector proves itself by: model the artefact in Layer 1 → project to this generator's
input spec → run the generator → compare JSON. Because the generator is neutral and
deterministic (uuid5 over a neutral namespace), the comparison is stable and carries no
project residue.

## Contents

- `gen_userview.py` — userview definition JSON from a neutral `userview:` spec (categories
  as authored, GroupPermission from an optional per-category `role`, CrudMenu / DataListMenu
  / FormMenu, a clean Dx8TrimedaTheme with PWA/push disabled and no project JS/CSS).
  `test_gen_userview.py` covers determinism, structure, and zero-project-residue.
- `gen_dashboards.py` — enterprise `SqlChartMenu` charts from a neutral `dashboard:` spec
  (server-side SQL → Apache ECharts, `keyName`/`value` axes; the native way, not Chart.js in
  an HtmlPage). `test_gen_dashboards.py` covers the SqlChartMenu shape, server-side-SQL
  binding, determinism, and zero-project-residue.
- `gen_forms.py` — form definition JSON from a form `spec.yml` (Output-C shape): sections,
  columns, the element library (text/textarea/select/radio/checkbox/date/hidden/fileupload/
  id_generator/subform/grid), read-only inference, `FormOptionsBinder` lookups, and the
  optional `FormPrefillLoadBinder` (a catalog component) via a spec `prefill:` block.
- `gen_datalists.py` — datalist JSON: a companion `list_<formId>` per form spec, or a custom
  `datalist:` spec (`jdbc` or form-row binder), sortable columns, `text|select` filters,
  optional row-action/drill links. Per-project list policy (auto-filters, the drill base URL)
  is spec-driven / env-overridable — no project content baked in.
- `gen_workflow.py` — XPDL 1.0 `package.xpdl` + the `packageDefinition` fragment (form /
  plugin / participant maps) from a `WF-*.spec.yml`; custom tools via the MultiTools wrapper.
- `build_jwa.py` — packages generated forms/datalists/userviews (+ workflow, + a data API
  builder) into an importable `.jwa` app zip.

`gen_forms`/`gen_datalists` take a **spec dir**; `gen_workflow` takes `<wf_spec.yml> <out_dir>
<app_id>`; `build_jwa` takes `<generated_dir> <app_id> <app_name> <out.jwa>`; the rest take
`<spec.yml> <out_dir>`.

The four added generators were extracted from a production-proven delivery generator and
neutralised; neutrality is grep-checked and behaviour is proven by an **equivalence test**
(neutral output byte-identical to the source generator on a real app's specs).
