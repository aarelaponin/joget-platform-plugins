#!/usr/bin/env python3
"""Deterministic Joget DX form JSON generator (P5).

Converts joget-form-gen input YAML specs (Output C format) into importable
Joget DX 8.x form definition JSON, using the exact element schemas from the
joget-form-gen skill (references/element-schemas.md). Same spec -> same JSON.

Usage: gen_forms.py <spec_dir> <out_dir>
"""
import json
import os
import re
import sys

import yaml

V_NONE = {"className": "", "properties": {}}

# An engine/state-machine-owned field carries one of these phrases in its label
# (the spec authors' own annotation). Such a field is displayed but never editable.
_MANAGED_LABEL = re.compile(
    r"\b(set by|managed by|managed\)|copied from|on submit|by engine|by rollup|by the engine)\b", re.I)


def ro(field):
    """Read-only flag. A field is managed (read-only — displayed, never user-editable)
    when it sets `managed: true`/`readonly: true`, inherits a section-level `managed`,
    OR its label is annotated as engine-owned (e.g. "(set by engine)", "(managed by
    AllocationEngine)", "(copied from case)"). `managed: false` forces it editable."""
    if field.get("managed") is False or field.get("readonly") is False:
        return ""
    if field.get("readonly") or field.get("managed"):
        return "true"
    return "true" if _MANAGED_LABEL.search(field.get("label", "")) else ""


def validator(field):
    req = bool(field.get("required"))
    numeric = bool(field.get("storeNumeric"))
    if not req and not numeric:
        return dict(V_NONE)
    return {"className": "org.joget.apps.form.lib.DefaultValidator",
            "properties": {"mandatory": "true" if req else "",
                           "type": "double" if numeric else "", "message": ""}}


def static_options(opts):
    return [{"value": o["value"], "label": o["label"], "grouping": o.get("grouping", "")} for o in opts]


def element(f):
    t = f["type"]
    fid, label = f["id"], f.get("label", f["id"])
    if t == "html":
        # CustomHTML — a presentation-only panel (workspace explainers, section intros).
        # Carries no data binding, so it never participates in store (zero data-loss risk).
        # label MUST be present (even empty): customHTML.ftl reads element.properties.label
        # without a ?? guard — a missing key throws in FreeMarker and the element renders
        # as NOTHING (found live: the CTX-02 header style block silently vanished).
        return {"className": "org.joget.apps.form.lib.CustomHTML", "properties": {
            "id": fid, "label": f.get("label", ""), "value": f.get("html", f.get("value", "")),
            "autoColumn": "", "useAjax": ""}}
    if t == "textfield":
        return {"className": "org.joget.apps.form.lib.TextField", "properties": {
            "id": fid, "label": label, "value": str(f.get("value", "")), "placeholder": "", "maxlength": "",
            "size": "", "encryption": "", "storeNumeric": "true" if f.get("storeNumeric") else "",
            "readonly": ro(f), "readonlyLabel": "", "style": "", "requiredSanitize": "",
            "workflowVariable": "", "validator": validator(f)}}
    if t == "textarea":
        return {"className": "org.joget.apps.form.lib.TextArea", "properties": {
            "id": fid, "label": label, "value": "", "placeholder": "",
            "rows": str(f.get("rows", 4)), "cols": "80", "readonly": ro(f), "readonlyLabel": "",
            "requiredSanitize": "", "workflowVariable": "", "validator": validator(f)}}
    if t == "select":
        props = {"id": fid, "label": label, "value": str(f.get("value", "")), "multiple": "", "size": "",
                 "controlField": f.get("controlField", ""), "controlValue": "", "readonly": ro(f), "readonlyLabel": "",
                 "workflowVariable": "", "validator": validator(f)}
        if "users" in f:
            # W-11 / UX-02 §3: configured-registry selection over the DIRECTORY — the
            # enterprise UserOptionsBinder lists users filtered by group/org/dept and
            # the field stores the username. Officers are picked, never typed.
            u = f["users"] or {}
            props["options"] = []
            props["optionsBinder"] = {"className": "org.joget.plugin.enterprise.UserOptionsBinder",
                "properties": {"orgId": u.get("org", ""), "deptId": u.get("dept", ""),
                               "groupId": u.get("group", ""),
                               "addEmptyOption": "true", "emptyLabel": "",
                               "optionLabel": u.get("optionLabel", ""),   # '' = fullname (username)
                               "grouping": ""}}
            return {"className": "org.joget.apps.form.lib.SelectBox", "properties": props}
        if "lookup" in f:
            lk = f["lookup"]
            props["options"] = []
            props["optionsBinder"] = {"className": "org.joget.apps.form.lib.FormOptionsBinder",
                "properties": {"formDefId": lk["formDefId"],
                               "idColumn": lk.get("idColumn", "id").removeprefix("c_"),
                               "labelColumn": lk.get("labelColumn", "name").removeprefix("c_"),
                               "groupingColumn": "",
                               # FormOptionsBinder prepends its own WHERE — a leading WHERE in the
                               # spec causes "WHERE WHERE …" → HQL syntax error → the form renders
                               # "null". Strip any leading WHERE defensively (2026-06-19 fix).
                               "extraCondition": re.sub(r"^\s*WHERE\s+", "",
                                                        lk.get("extraCondition", ""), flags=re.I),
                               "addEmptyOption": "true", "emptyLabel": "", "useAjax": "",
                               "cacheInterval": ""}}
        else:
            props["options"] = static_options(f.get("static_options", []))
            props["optionsBinder"] = dict(V_NONE)
        return {"className": "org.joget.apps.form.lib.SelectBox", "properties": props}
    if t == "radio":
        return {"className": "org.joget.apps.form.lib.Radio", "properties": {
            "id": fid, "label": label, "value": str(f.get("value", "")), "fullWidth": "", "readonly": "",
            "controlField": "", "options": static_options(f.get("static_options", [])),
            "optionsBinder": dict(V_NONE), "validator": validator(f)}}
    if t == "checkbox":
        return {"className": "org.joget.apps.form.lib.CheckBox", "properties": {
            "id": fid, "label": label, "value": "", "workflowVariable": "",
            "options": static_options(f.get("static_options", [{"value": "true", "label": ""}])),
            "optionsBinder": dict(V_NONE), "validator": validator(f)}}
    if t == "date":
        return {"className": "org.joget.apps.form.lib.DatePicker", "properties": {
            "id": fid, "label": label, "value": f.get("value", ""), "dataFormat": "yyyy-MM-dd",
            "datePickerType": "", "currentDateAs": "", "yearRange": f.get("yearRange", "c-5:c+10"),
            "disableWeekends": "", "allowManual": "", "startDateFieldId": "",
            "endDateFieldId": "", "readonly": ro(f), "readonlyLabel": "",
            "workflowVariable": "", "validator": validator(f)}}
    if t == "hidden":
        return {"className": "org.joget.apps.form.lib.HiddenField", "properties": {
            "id": fid, "label": label, "value": "", "useDefaultWhenEmpty": "true"
            if f.get("useDefaultWhenEmpty") else "", "workflowVariable": ""}}
    if t == "fileupload":
        return {"className": "org.joget.apps.form.lib.FileUpload", "properties": {
            "id": fid, "label": label, "size": "", "multiple": "",
            "maxSize": str(f.get("maxSizeKb", 25600)),
            "fileType": f.get("fileType", ""), "permissionType": "",
            "attachment": "", "validator": validator(f)}}
    if t == "id_generator":
        return {"className": "org.joget.apps.form.lib.IdGeneratorField", "properties": {
            "id": fid, "label": label, "format": f.get("format", "ID-??????"),
            "envVariable": f.get("envVariable", fid + "Counter"), "validator": dict(V_NONE)}}
    if t == "subform":
        # 1:1 embedded child form (subject-table specialisation, ADR-001). Joget
        # SubForm stores the child row under the PARENT record's primary key, so a
        # child row shares the parent record's primary key — no foreign key needed.
        return {"className": "org.joget.apps.form.lib.SubForm", "properties": {
            "id": fid, "label": label, "formDefId": f["formDefId"],
            "parentSubFormId": f.get("parentSubFormId", ""),
            "readonly": "true" if f.get("readonly") else "",
            "readonlyLabel": "true" if f.get("readonlyLabel") else ""}}
    if t == "grid":
        # 1:many editable child grid. Child rows are
        # linked by foreignKey (a HiddenField in the child form holding the parent id).
        child = f["formDefId"]
        fk = f.get("foreignKey", "caseId")
        cols = [{"label": c.get("label", c["value"]), "value": c["value"],
                 "width": c.get("width", ""), "format": "", "formatType": ""}
                for c in f.get("columns", [])]
        return {"className": "org.joget.plugin.enterprise.FormGrid", "properties": {
            "id": fid, "label": label, "formDefId": child,
            "pageSize": str(f.get("pageSize", 50)), "enableSorting": "true",
            "readonly": "true" if f.get("readonly") else "", "deleteGridData":
            "true" if f.get("deleteGridData") else "", "validateMaxRow": "",
            "requestParams": [], "options": cols,
            "loadBinder": {"className": "org.joget.plugin.enterprise.MultirowFormBinder",
                           "properties": {"formDefId": child, "foreignKey": fk}},
            "storeBinder": {"className": "org.joget.plugin.enterprise.MultirowFormBinder",
                            "properties": {"formDefId": child, "foreignKey": fk}},
            "validator": dict(V_NONE)}}
    if t == "popupselect":
        # IDR-05/AP-04 (UX-01 §3): reference to an UNBOUNDED operational set — the
        # selection happens in a datalist popup (that list's own server-side search,
        # filters and paging), never a dropdown over the full set. displayField shows
        # the business key/label; the selected row's key is stored. Shape verified
        # against the JogetDxShowcase app on the live instance.
        return {"className": "org.joget.plugin.enterprise.PopupSelectBox", "properties": {
            "id": fid, "label": label, "buttonLabel": "Select",
            "listId": f["listId"], "displayField": f["displayField"],
            "idField": f.get("idField", ""), "multiple": "",
            "value": "", "readonly": ro(f), "readonlyLabel": "",
            "width": "80%", "height": "80%",
            "requestParams": [], "workflowVariable": "",
            "validator": validator(f)}}
    if t == "multipaged":
        # DX9 tabbed record console (ADR-068): enterprise MultiPagedForm, displayMode
        # tab — free click navigation, each page binding a generated per-tab form over
        # the same table (empty parent/subform keys = pages share the console's primary
        # key, i.e. the SAME row). Page set is serialised into the numberOfPage
        # elementselect object — the encoding verified against the JogetDxShowcase app
        # on the live instance: className = page count, properties = page%d_* knobs.
        # page%d_validate stays empty: tabs browse freely, the server enforces every
        # rule on save anyway (VAL-03).
        pages = f["pages"]
        if not 2 <= len(pages) <= 15:
            raise ValueError(f"multipaged '{fid}': {len(pages)} pages — the element supports 2-15")
        pp = {}
        for n, p in enumerate(pages, 1):
            pp[f"page{n}_label"] = p.get("label", f"Page {n}")
            pp[f"page{n}_formDefId"] = p["formDefId"]
            pp[f"page{n}_readonly"] = "true" if p.get("readonly") else ""
            pp[f"page{n}_readonlyLabel"] = ""
            pp[f"page{n}_validate"] = ""
            pp[f"page{n}_parentSubFormId"] = ""
            pp[f"page{n}_subFormParentId"] = ""
        return {"className": "org.joget.plugin.enterprise.MultiPagedForm", "properties": {
            "id": fid, "displayMode": f.get("displayMode", "tab"),
            "ajaxMode": "true" if f.get("ajaxMode", True) else "",
            "partiallyStore": "", "storeMainFormOnPartiallyStore": "",
            "onlyAllowSubmitOnLastPage": "",
            "prevButtonlabel": "Prev", "nextButtonlabel": "Next", "css": "",
            "numberOfPage": {"className": str(len(pages)), "properties": pp}}}
    if t == "period_picker":
        # KP-01 typed.period (the W-12 fix): a tax period is SELECTED from a governed
        # set of valid periods, never typed. Native realization = SelectBox with options
        # generated deterministically from the dd set's periodicities x its year window
        # (both governed facts — no runtime year baked in, so goldens stay stable).
        # Per-tax-type periodicity narrowing (dependsOn) is the period-picker PLUGIN's
        # job (backlog); here the union is offered, labelled by cadence.
        peri = f.get("periodicities", ["monthly", "quarterly", "annual"])
        w = f.get("window", {}) or {}
        y0, y1 = int(w.get("from", 2024)), int(w.get("to", 2027))
        opts = []
        for y in range(y0, y1 + 1):
            if "annual" in peri:
                opts.append({"value": f"{y}", "label": f"{y} (annual)", "grouping": "annual"})
            if "quarterly" in peri:
                for q in (1, 2, 3, 4):
                    opts.append({"value": f"{y}-Q{q}", "label": f"{y}-Q{q} (quarterly)", "grouping": "quarterly"})
            if "monthly" in peri:
                for mth in range(1, 13):
                    opts.append({"value": f"{y}-{mth:02d}", "label": f"{y}-{mth:02d} (monthly)", "grouping": "monthly"})
        return {"className": "org.joget.apps.form.lib.SelectBox", "properties": {
            "id": fid, "label": label, "value": "", "multiple": "", "size": "",
            "controlField": f.get("dependsOn", ""), "controlValue": "",
            "readonly": ro(f), "readonlyLabel": "", "workflowVariable": "",
            "options": static_options(opts), "optionsBinder": dict(V_NONE),
            "validator": validator(f)}}
    if t == "smart_search":
        # KP-01 md.registry (owner-corrected 14.07): PARTIAL-KNOWLEDGE search — the officer
        # searches with whatever facts they know, gets ranked candidates with confidence,
        # picks one explicitly. Realized by the proven joget-smart-search element (Lesotho).
        # The searchable backend (taxpayer search API) is wired via apiEndpoint at deploy;
        # displayColumns/id-pattern come from the field config (dd facts, harvested).
        c = f.get("config", {}) if isinstance(f.get("config"), dict) else {}
        return {"className": "global.govstack.smartsearch.element.SmartSearchElement",
                "properties": {
                    "id": fid, "label": label,
                    "storeValue": f.get("idField", c.get("storeValue", "")),
                    "required": "true" if f.get("required") else "",
                    "displayMode": c.get("displayMode", "criteria"),
                    "displayColumns": c.get("displayColumns", ""),
                    "apiEndpoint": c.get("apiEndpoint", ""),
                    "apiId": c.get("apiId", ""), "apiKey": c.get("apiKey", ""),
                    "nationalIdPattern": c.get("nationalIdPattern", ""),
                    "nationalIdMinLength": str(c.get("nationalIdMinLength", "")),
                    "autoSelectSingleResult": "true" if c.get("autoSelectSingleResult", True) else "",
                    "showRecentFarmers": "true" if c.get("showRecents", True) else "",
                    "maxRecentFarmers": str(c.get("maxRecents", 5))}}
    raise ValueError(f"unknown field type: {t}")


def _bool_str(v, default=True):
    if v is None:
        return "true" if default else "false"
    return "true" if (v is True or str(v).strip().lower() in ("true", "yes", "1")) else "false"


def prefill_properties(pf):
    """Serialise a spec `prefill:` block into FormPrefillLoadBinder plugin properties.
    Grids stay JSON arrays of objects (Joget parses them back to Object[] of Map, which
    the binder reads via getProperty). No raw SQL — the binder looks data up via the
    Joget FormDataDao API from these knobs."""
    def rows(key, fields):
        return [{f: str(r.get(f, "")) for f in fields} for r in (pf.get(key) or [])]
    props = {
        "enabled": _bool_str(pf.get("enabled"), True),
        "onlyOnAdd": _bool_str(pf.get("onlyOnAdd"), True),
        "keySources": rows("keySources", ["source", "name"]),
        "formId": str(pf.get("formId", "")),
        "matchField": str(pf.get("matchField", "")),
        "filters": rows("filters", ["field", "op", "value"]),
        "orderBy": str(pf.get("orderBy", "")),
        "orderNumeric": _bool_str(pf.get("orderNumeric"), False),
        "pickFirst": _bool_str(pf.get("pickFirst"), True),
        "related": rows("related", ["formId", "table", "keyFrom", "alias"]),
        "mappings": rows("mappings", ["from", "to"]),
        "constants": rows("constants", ["to", "value"]),
    }
    if pf.get("table"):
        props["table"] = str(pf["table"])
    return props


def load_binder(fm):
    """Form LOAD binder. `loadBinder: prefill` → the configurable, project-neutral
    FormPrefillLoadBinder (config serialised from the spec's `prefill:` block);
    otherwise the platform default WorkflowFormBinder."""
    if fm.get("loadBinder") == "prefill":
        return {"className": "com.fiscaladmin.joget.formprefill.FormPrefillLoadBinder",
                "properties": prefill_properties(fm.get("prefill", {}))}
    return {"className": "org.joget.apps.form.lib.WorkflowFormBinder", "properties": {}}


def build_form(spec):
    fm = spec["form"]
    sections = []
    for si, sec in enumerate(spec["sections"], 1):
        cols = int(sec.get("columns", 1))
        width = {1: ["100%"], 2: ["50%", "50%"], 3: ["33%", "33%", "33%"]}[cols]
        buckets = [[] for _ in range(cols)]
        sec_managed = bool(sec.get("managed"))  # section-level lock: every field read-only
        for i, fld in enumerate(sec["fields"]):
            if sec_managed and "managed" not in fld and "readonly" not in fld:
                fld = {**fld, "managed": True}
            buckets[i % cols].append(element(fld))
        sections.append({"className": "org.joget.apps.form.model.Section",
            "properties": {"id": f"section{si}", "label": sec.get("label", "")},
            "elements": [{"className": "org.joget.apps.form.model.Column",
                          "properties": {"width": width[c]}, "elements": buckets[c]}
                         for c in range(cols)]})
    return {"className": "org.joget.apps.form.model.Form", "properties": {
        "id": fm["id"], "name": fm["name"], "tableName": fm.get("table", fm["id"]),
        "description": fm.get("description", ""),
        "loadBinder": load_binder(fm),
        "storeBinder": {"className": "org.joget.apps.form.lib.WorkflowFormBinder", "properties": {}},
        "permission": dict(V_NONE), "noPermissionMessage": "",
        "postProcessor": post_processor(spec),
        "postProcessorRunOn": spec.get("postProcessor", {}).get("runOn", "create")},
        "elements": sections}


def post_processor(spec):
    """Optional form post-processor (a tool run after save)."""
    pp = spec.get("postProcessor")
    if not pp:
        return dict(V_NONE)
    return {"className": pp["className"],
            "properties": {k: str(v) for k, v in pp.get("properties", {}).items()}}


def validate(form, path):
    errs = []
    if not form["properties"].get("id"): errs.append("missing form id")
    if not form["properties"].get("tableName"): errs.append("missing tableName")
    for s in form["elements"]:
        if not s["elements"]: errs.append("section without column")
        for c in s["elements"]:
            if "width" not in c["properties"]: errs.append("column without width")
            for e in c["elements"]:
                if not e["className"].startswith(("org.joget.", "global.govstack.")):
                    errs.append(f"non-FQN className {e['className']}")
                p = e["properties"]
                if e["className"].endswith("SelectBox"):
                    if not p["options"] and not p["optionsBinder"]["className"]:
                        errs.append(f"{p['id']}: SelectBox with neither options nor binder")
    return [f"{os.path.basename(path)}: {e}" for e in errs]


def main():
    spec_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    all_errs, count = [], 0
    for fn in sorted(os.listdir(spec_dir)):
        if not fn.endswith(".spec.yml"):
            continue
        spec = yaml.safe_load(open(os.path.join(spec_dir, fn)))
        form = build_form(spec)
        all_errs += validate(form, fn)
        out = os.path.join(out_dir, form["properties"]["id"] + ".json")
        with open(out, "w") as f:
            json.dump(form, f, indent=2, sort_keys=False)
        count += 1
        print(f"  + {form['properties']['id']}.json")
    print(f"{count} forms generated -> {out_dir}")
    if all_errs:
        print("VALIDATION ERRORS:"); [print("  !", e) for e in all_errs]; sys.exit(1)
    print("validation: all checks pass")


if __name__ == "__main__":
    main()
