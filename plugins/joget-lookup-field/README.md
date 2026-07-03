# joget-lookup-field

A **form element** for Joget DX that watches a SelectBox and, when its value changes,
auto-populates one or more fields on the same form from a **related form record** — by
configuration only, no per-form Java. Project-neutral OSGi bundle: depends only on the Joget
form/commons API (`wflow-core`, `provided`).

Complements [`joget-form-prefill`](../joget-form-prefill): lookup-field is **field-level and
live** (reacts to a SelectBox change while editing); form-prefill is **form-level and on-load**
(pre-fills a new record when the form opens). Use lookup-field for dependent fields that update as
the user picks a value; use form-prefill to seed a new record from a launch context.

## Build

```bash
mvn -q -pl plugins/joget-lookup-field -am clean install   # → target/joget-lookup-field-8.1-SNAPSHOT.jar
```

Targets Java 17 (the plugin uses Java-17 language features); the reactor builds it alongside the
Java-11 modules without issue.

## Deploy

```bash
cp target/joget-lookup-field-8.1-SNAPSHOT.jar <joget>/wflow/app_plugins/
```

Registers one form element: `global.govstack.lookupfield.element.LookupFieldElement`.

## Configure

Drop the **Lookup Field** element on a form and set: the SelectBox field to watch, the related
form/table to look up, the match key, and the field-to-field mappings to populate. The element
ships its own template, static assets and a client-side web service for the live lookup.

## Layout

```
src/main/java/global/govstack/lookupfield/
  Activator.java                     # registers the element
  element/LookupFieldElement.java    # the form element
  element/LookupFieldResources.java  # static assets
  element/LookupFieldWebService.java # AJAX lookup endpoint
src/main/resources/
  properties/LookupFieldElement.json # element config schema
  templates/LookupFieldElement.ftl   # render template
  static/lookup-field.css            # styling
```

## Registry

Entry `joget-lookup-field` in the repo `registry.yaml` (category `foundation`). Promoted from the
Lesotho FRS project; source is project-neutral.
