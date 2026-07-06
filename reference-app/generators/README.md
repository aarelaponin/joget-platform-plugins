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

Usage: `gen_userview.py <spec.yml> <out_dir>`.
