# joget-form-prefill

A **configurable form LOAD binder** for Joget DX. It pre-populates a *new* form from a
related record — by configuration only, with no per-form Java. Project-neutral: the bundle
depends only on the Joget form/commons API (`wflow-core`, `provided`), so the same JAR drops
into any Joget 8.1/9.x instance and any app.

It replaces two architecturally weak patterns: URL-parameter prefill (`?x=` → field default
`#requestParam.x#`, single value, ids leaked in links) and hand-written per-form load binders
(a new Java class + rebuild for every form). Here, one bundle is configured per form.

## How it works

On load, if the form is opening a **new** record and prefill is enabled, the binder:

1. resolves a **key** from an ordered list of sources (first non-empty wins);
2. looks up the source record via **`FormDataDao`** — `find` for the primary record (a match on
   `id`, the row's own key, reads that row with `load`), optional `load` for related records
   (**no raw SQL**);
3. applies post-find **filters** (eq/ne), orders (numeric or lexical, descending), picks the first;
4. **maps** fields onto this form (from the key, a primary field, an `alias.field` of a related
   record, or constants) and returns a synthetic row.

Opening an **existing** record (edit/view) defers entirely to the default load — untouched.
Any failure falls back to the default load; it never breaks the render.

## Build

```bash
mvn -q -pl plugins/joget-form-prefill -am clean install   # 17 unit tests, JAR to target/ + ~/.m2
```

## Deploy

Copy the JAR into the instance's plugin folder (hot-reloads in ~10s), or upload via
**Manage Plugins**:

```bash
cp target/joget-form-prefill-1.1.0.jar <joget>/wflow/app_plugins/   # and remove the older joget-form-prefill-*.jar
```

It registers two plugins: `com.fiscaladmin.joget.formprefill.FormPrefillLoadBinder` and, since 1.1.0,
`com.fiscaladmin.joget.formprefill.GridRowsPrefillBinder` (below).

## Configure (set the binder as a form's Load Binder)

Properties (all but the lookup essentials are optional):

| Property | Meaning |
|---|---|
| `enabled` | master on/off (default true) |
| `onlyOnAdd` | only prefill a new record (default true) |
| `keySources` | ordered `{source, name}` — `source` ∈ `requestParam`, `loginUser`, `currentField` |
| `formId` | lookup form id (its definition maps columns → fields) |
| `table` | lookup table (blank = same as `formId`) |
| `matchField` | the field that must equal the key |
| `filters` | extra `{field, op, value}` — `op` ∈ `eq` (default), `ne` |
| `orderBy` / `orderNumeric` / `pickFirst` | which row to take when several match |
| `related` | ordered `{formId, table, keyFrom, alias}` — `dao.load` by an id on the primary record |
| `mappings` | `{from, to}` — `from` is `key`, a primary field, or `alias.field`; `to` is a field on this form |
| `constants` | `{to, value}` — literal values |

> **Field names are Joget form field ids, not database columns.** Use the form's field ids,
> not the lowercased Postgres columns (`c_...`). Getting this wrong silently prefills nothing.

### Generator opt-in (spec-driven pipeline)

A spec-driven form generator can accept `loadBinder: prefill` plus a `prefill:` block in a
form spec and emit the binder + config automatically:

```yaml
form:
  id: <thisForm>
  loadBinder: prefill
  prefill:
    keySources: [{source: requestParam, name: partyId}]
    formId: <lookupForm>
    matchField: partyId
    filters:
      - {field: recordType, op: eq, value: OPEN}
      - {field: state, op: ne, value: CLOSED}
    orderBy: amount
    orderNumeric: true
    related: [{formId: <relatedForm>, keyFrom: id, alias: rel}]
    mappings:
      - {from: key, to: partyId}
      - {from: id, to: sourceRecordId}
      - {from: 'rel.total', to: totalAmount}
```

## Proven

Proven in production public-sector implementations across multiple Joget instances,
databases and domains — the *same JAR*, reused by configuration only (case-management
launch-with-context and master-data lookups on both DX 8.x and DX 9.x).

## Tests

`mvn test` runs 17 pure unit tests over `PrefillResolver` + `PrefillConfig` with an in-memory
`DataAccess` fake — key fallback, no-key, no-match, eq/ne filters, numeric pick-largest,
alias mapping, constants, bad-field rejection, and grid parsing — and over the `FormDataDao`
adapter (`DaoAccessTest`, `FormDataDao` faked with Mockito): a match on `id` reads the row by
its key (found, not found, blank key), any other match field is read through `customProperties`.
No Joget runtime needed.

1.0.1 — a match on `id` no longer queries `customProperties.id` (which cannot be resolved, so the
form opened empty); it reads the row by its key.

## Layout

```
src/main/java/com/fiscaladmin/joget/formprefill/
  FormPrefillLoadBinder.java   # the Joget adapter (extends WorkflowFormBinder)
  PrefillResolver.java         # pure prefill logic (no Joget deps)
  PrefillConfig.java           # config model + parser
  DataAccess.java              # the FormDataDao seam (faked in tests)
  Activator.java               # registers the one plugin
```

## Registry

This plugin is entry `joget-form-prefill` in the repo `registry.yaml`. Its **config contract**
(the property keys above) is the public API that spec-driven generators target — changing a
key is a breaking (major) version bump.

## 1.1.0: a grid's lines carried from the record (GridRowsPrefillBinder)

A second plugin in the same bundle (joget-spec-kit METHOD-2026-10-24-01): a grid's LOAD binder for a request
whose lines are the rows of the record its form is opened on. On a record that exists, and on a submission, it
loads the grid's own lines exactly as the platform's `MultirowFormBinder` does (the platform's binder itself,
with the same `formDefId` and `foreignKey`). On a NEW record whose own load holds no line, it reads the key of
the record from the address (`keyParam`) and returns one line for each row of `rowsTable` whose
`parentField` holds it and that meets `where` (HQL over `e.customProperties`), each line referring to its
row by `refField` (the row's `refColumn`, `id` by default) and taking the values `map` names. A carried
line has no `id`: it is a new line, given its key by the grid's store binder (the platform's) when the request
is saved. The rows are read through `FormDataDao` - no raw SQL. The library's `gen_forms` writes it on a grid
whose spec carries `rowsFrom`. The pure part, `GridRows`, has its own tests (`GridRowsTest`).
