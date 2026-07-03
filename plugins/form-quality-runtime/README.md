# form-quality-runtime

A **generic, form-id-driven quality validation engine** for Joget DX. It reads validation rules
and gates declared in configuration (keyed by form id) and enforces them on save, surfacing a
quality banner on the form. Project-neutral: depends only on the Joget form/commons API plus
`joget-status-framework` (a platform artifact) and gson.

## How it works

For a given form, the engine loads its declared rule set, evaluates the rules against the submitted
data, and blocks or warns per the configured gate. A companion **Quality Banner** form element
renders the current quality state inline on the form.

## Build

```bash
mvn -q -pl plugins/form-quality-runtime -am clean install
```

Depends on `global.govstack:joget-status-framework` (pinned; install it to `~/.m2` or resolve from
GitHub Packages).

## Deploy

```bash
cp target/form-quality-runtime-8.1-SNAPSHOT.jar <joget>/wflow/app_plugins/
```

## Registry

Entry `form-quality-runtime` in the repo `registry.yaml` (category `foundation`). Promoted from the
Lesotho FRS project; source is project-neutral (example rule/form names genericised).
