#!/usr/bin/env python3
"""Deterministic companion-datalist generator (P5) — joget-datalist-gen house style.

Reads form specs (Output C YAML) and emits one list_<formId>.json per form, and
custom datalist specs (binder: jdbc|form). UX layer:
EVERY column is sortable; filters carry a type (text|select); custom lists can declare
`rowActions`/`drill` (HyperlinkDataListAction) for summary->detail and detail->record
drill-down. Filter values + drill params reach a JDBC binder via #requestParam.<param>#
in the SQL (the verified JDBC filter mechanism), so a filter dropdown and a drill link
feed the same SQL param. Same spec -> same JSON.

Usage: gen_datalists.py <spec_dir> <out_dir>
"""
import json
import os
import re
import sys

import yaml

SKIP_TYPES = {"textarea", "hidden"}
UV = os.environ.get("JOGET_DRILL_BASE", "#request.contextPath#/web/userview/_app_/_console_/_")  # detail-list / record drill base (project-overridable)

# form-field labels carry authoring annotations that help a data-entry user but are pure
# noise as a grid column header — provenance notes ("(set by engine)"), FK/source-table refs
# FK/source-table refs, explanatory glosses ("(copied from source)")
# and enum hints ("(SUCCESS/PARTIAL/FAILURE)"). Strip [..] always, and strip EVERY ()-segment EXCEPT a
# small allowlist of genuinely informative units/codes a header should keep: currencies, %, and the
# a small code range. (An earlier pass stripped only a fixed keyword set, which left the glosses above.)
_KEEP_PAREN = re.compile(r"^(EUR|USD|MDL|GBP|RON|%|C\d(\s*[-–]\s*C\d)?|C1-C6|days|months)$", re.I)


def clean_label(label):
    s = re.sub(r"\s*\[[^\]]*\]", "", label or "")
    s = re.sub(r"\s*\(([^)]*)\)",
               lambda m: m.group(0) if _KEEP_PAREN.match(m.group(1).strip()) else "", s)
    return re.sub(r"\s{2,}", " ", s).strip().rstrip(";,").strip()


def fk_format(lookup):
    return {"className": "org.joget.plugin.enterprise.OptionsValueFormatter",
            "properties": {"optionsBinder": {
                "className": "org.joget.apps.form.lib.FormOptionsBinder",
                "properties": {"formDefId": lookup["formDefId"],
                               "idColumn": lookup.get("idColumn", "code").removeprefix("c_"),
                               "labelColumn": lookup.get("labelColumn", "name").removeprefix("c_"),
                               "addEmptyOption": "true", "groupingColumn": "", "useAjax": "",
                               "extraCondition": "", "cacheInterval": "", "emptyLabel": ""}},
                "options": []}}


def col(idx, name, label, fmt=None, action=None, render_html=False):
    """Full canonical column shape — ALWAYS sortable; label cleaned of provenance leaks."""
    return {"name": name, "id": f"column_{idx}", "label": clean_label(label), "hidden": "false",
            "renderHtml": "true" if render_html else "", "format": fmt or {"className": "", "properties": {}},
            "sortable": "true", "datalist_type": "column", "exclude_export": "", "width": "",
            "headerAlignment": "", "alignment": "", "style": "",
            "action": action or {"className": "", "properties": {}}}


def filter_entry(idx, f):
    """f: {id, label?, type? (text|select), options?, param?}. JDBC SQL reads #requestParam.<param>#."""
    fid = f["id"]
    param = f.get("param", "f_" + fid)
    ftype = f.get("type", "text")
    if ftype == "select":
        opts = [{"value": "", "label": "- any -"}]
        opts += [o if isinstance(o, dict) else {"value": o, "label": o} for o in f.get("options", [])]
        t = {"className": "org.joget.plugin.enterprise.SelectBoxDataListFilterType",
             "properties": {"label": "", "defaultValue": "", "multiple": "", "size": "", "options": opts}}
    else:
        t = {"className": "org.joget.apps.datalist.lib.TextFieldDataListFilterType", "properties": {}}
    return {"name": fid, "id": f"filter_{idx}", "label": f.get("label", fid),
            "type": t, "filterParamName": param}


def row_action(idx, ra):
    """ra: {label, to (target menu customId), hrefColumn, hrefParam}. HyperlinkDataListAction."""
    href = ra["href"] if ra.get("href") else f"{UV}/{ra['to']}"
    return {"className": "org.joget.apps.datalist.lib.HyperlinkDataListAction",
            "id": f"rowAction_{idx}", "label": ra.get("label", "Open"),
            "properties": {"label": ra.get("label", "Open"), "href": href,
                           "hrefColumn": ra.get("hrefColumn", "id"),
                           "hrefParam": ra.get("hrefParam", ""), "target": ra.get("target", ""),
                           "confirmation": ""}}


def envelope(dl_id, name, binder, cols, filters, row_actions, order_by="", order="ASC"):
    return {"id": dl_id, "name": name, "binder": binder, "columns": cols,
            "filters": filters, "actions": [], "rowActions": row_actions,
            "order": order, "orderBy": order_by, "useSession": "false",
            "showPageSizeSelector": "true", "pageSize": 0,
            "pageSizeSelectorOptions": "10,20,30,40,50,100",
            "buttonPosition": "bothLeft", "checkboxPosition": "left"}


def main():
    spec_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    count = 0
    for fn in sorted(os.listdir(spec_dir)):
        if not fn.endswith(".spec.yml"):
            continue
        spec = yaml.safe_load(open(os.path.join(spec_dir, fn)))

        if "datalist" in spec:  # custom datalist spec
            d = spec["datalist"]
            cols = []
            for i, c in enumerate(d["columns"]):
                fmt = c.get("formatter")
                action = None
                if c.get("drill"):  # cell-level drill: this column links to a detail list
                    dr = c["drill"]
                    action = row_action(0, dr)["properties"] and {
                        "className": "org.joget.apps.datalist.lib.HyperlinkDataListAction",
                        "properties": {"href": dr.get("href") or f"{UV}/{dr['to']}",
                                       "hrefColumn": dr.get("hrefColumn", c["id"]),
                                       "hrefParam": dr.get("hrefParam", ""), "target": dr.get("target", ""),
                                       "confirmation": ""}}
                if fmt and fmt.get("type") == "optionsValue":
                    cols.append(col(i, c["id"], c["label"], fk_format(fmt), action))
                elif fmt and fmt.get("type") == "date":
                    cols.append(col(i, c["id"], c["label"],
                        {"className": "org.joget.plugin.enterprise.DateFormatter",
                         "properties": {"dataFormat": fmt.get("format", "dd/MM/yyyy")}}, action))
                else:
                    cols.append(col(i, c["id"], c["label"], None, action, bool(c.get("renderHtml"))))
            if d["binder"].get("type") == "jdbc":  # sanctioned raw-SQL slot (P3)
                binder = {"className": "org.joget.plugin.enterprise.JdbcDataListBinder",
                          "properties": {"jdbcDatasource": "default", "optimisePaging": "",
                                         "primaryKey": d["binder"].get("primaryKey", "id"),
                                         "sql": d["binder"]["sql"]}}
            else:
                binder = {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
                          "properties": {"formDefId": d["binder"]["formDefId"],
                                         "extraCondition": d.get("extraCondition", "")}}
            filters = [filter_entry(i, f) for i, f in enumerate(d.get("filters", []))]
            ras = [row_action(i, r) for i, r in enumerate(d.get("rowActions", []))]
            dl = envelope(d["id"], d["name"], binder, cols, filters, ras,
                          d.get("sort", {}).get("column", ""),
                          "DESC" if d.get("sort", {}).get("desc") else "ASC")
            json.dump(dl, open(os.path.join(out_dir, d["id"] + ".json"), "w"), indent=2)
            count += 1
            print(f"  + {d['id']}.json (custom, {len(cols)} cols, {len(filters)} filters, {len(ras)} drill)")
            continue

        if "form" not in spec:
            continue
        form_id = spec["form"]["id"]
        # form-row binder applies datalist filters natively; a form spec may declare
        # `autoFilters: {fieldId: label}` to auto-add "contains" filters on chosen columns.
        auto_filter = spec["form"].get("autoFilters", {})
        # optional `listColumns` (ordered field ids) curates a wide form's companion list to a
        # sensible default; absent -> show all fields (legacy). Field metadata is looked up for labels/lookups.
        fmeta = {f["id"]: f for sec in spec["sections"] for f in sec["fields"]}
        list_cols = spec["form"].get("listColumns")
        order = list_cols if list_cols else [f["id"] for sec in spec["sections"] for f in sec["fields"]]
        columns = []
        filters = []
        idx = 0
        for fid in order:
            f = fmeta.get(fid)
            if not f or (not list_cols and f["type"] in SKIP_TYPES):
                continue
            label = f.get("label", fid)
            if "lookup" in f:
                columns.append(col(idx, fid, label, fk_format(f["lookup"])))
            else:
                columns.append(col(idx, fid, label))  # sortable plain column
            if fid in auto_filter:
                filters.append(filter_entry(len(filters),
                    {"id": fid, "label": auto_filter[fid], "type": "text"}))
            idx += 1
        binder = {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
                  "properties": {"formDefId": form_id, "extraCondition": ""}}
        ras = [row_action(i, r) for i, r in enumerate(spec["form"].get("listRowActions", []))]
        dl = envelope(f"list_{form_id}", f"List: {spec['form']['name']}", binder, columns, filters, ras)
        json.dump(dl, open(os.path.join(out_dir, f"list_{form_id}.json"), "w"), indent=2)
        count += 1
        print(f"  + list_{form_id}.json ({len(columns)} cols)")
    print(f"{count} datalists generated -> {out_dir}")


if __name__ == "__main__":
    main()
