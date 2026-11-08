#!/usr/bin/env python3
"""Deterministic companion-datalist generator (P5) — joget-datalist-gen house style.

Reads form specs (Output C YAML) and emits one list_<formId>.json per form, and
custom datalist specs (binder: jdbc|form). UX layer:
EVERY column is sortable; filters carry a type (text|select); custom lists can declare
`rowActions`/`drill` (HyperlinkDataListAction) for summary->detail and detail->record
drill-down. Filter values + drill params reach a JDBC binder via #requestParam.<param>#
in the SQL (the verified JDBC filter mechanism), so a filter dropdown and a drill link
feed the same SQL param. Same spec -> same JSON.

0.11.0: a select filter may carry the platform's options binder (read at run time) and a
controlField naming an earlier select filter (a divided list chosen in two steps); a date
column is shown in its day-first pattern (displayFormat) and read in its stored format
(dataFormat). See filter_entry, check_controls and date_format. A column's `optionsValue`
formatter may carry the values and labels of a fixed list (`options`) in place of a form to read
them from (METHOD-2026-09-26-02, point 19 (a)); see fk_format.

0.12.0 (METHOD-2026-11-05-17; MT-DM's hand-off of 5 November 2026, items 21, 10 and 18): a list's
sort direction is written as the platform reads it - "1" descending, "" ascending (wflow-core
9.0.7, DataList.ORDER_DESCENDING_VALUE "1"; DataList.getQueryParam sorts descending only where
the default order equals "1"), where 0.11.0 wrote "DESC" and "ASC", so that every descending list
was shown ascending; a select filter may carry `multiple`, which makes it a choice of several (the
platform's own value "true" of SelectBoxDataListFilterType's `multiple`); and a column may carry
the formatter `currency`, an amount shown with its grouping and its decimals, written through the
platform's BeanShellFormatter, since the platform has no number formatter (see currency_format).
A spec using neither new key emits exactly the 0.11.0 filter and column (asserted literally in
test_gen_datalists_order.py); the sort direction changes every list's `order` by design.

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


TONE_HEX = {"grey": "#546e7a", "blue": "#1565c0", "green": "#2e7d32",
            "amber": "#ef6c00", "red": "#c62828"}


def badge_format(fmt):
    """status_badge → an enterprise BeanShellFormatter that renders the state value as a
    coloured HTML pill. `value` is bound by the formatter; the value→colour map comes from
    the model's lifecycle tones, so the badge is deterministic and lifecycle-aware."""
    puts = "".join('m.put("%s","%s");' % (code, TONE_HEX.get(tone, TONE_HEX["grey"]))
                   for code, tone in fmt.get("tones", {}).items())
    script = (
        "java.util.Map m = new java.util.HashMap();" + puts +
        'String v = (value==null)?"":value.toString();'
        'if (v.equals("")) return "";'
        'String bg = m.containsKey(v)?(String)m.get(v):"#546e7a";'
        'String t = v.substring(0,1).toUpperCase()+v.substring(1).replace("_"," ");'
        'return "<span style=\\"display:inline-block;padding:2px 10px;border-radius:11px;'
        'color:#fff;font-size:12px;line-height:18px;white-space:nowrap;background:"'
        '+bg+"\\">"+t+"</span>";')
    return {"className": "org.joget.plugin.enterprise.BeanShellFormatter",
            "properties": {"script": script}}


def fk_format(lookup):
    if lookup.get("options") is not None and not lookup.get("formDefId"):
        # 0.11.0 (METHOD-2026-09-26-02, point 19 (a)): a column over a FIXED list shows the
        # value's label from the list's values written into the formatter - the platform's own
        # OptionsValueFormatter reads its `options` grid of value and label (jw-enterprise-plugins
        # 9.0.7, getOptionMap) and loads a binder only when one is named. A fixed list has no
        # table to read, so it names none. A formatter with a formDefId is emitted as before.
        return {"className": "org.joget.plugin.enterprise.OptionsValueFormatter",
                "properties": {"options": [{"value": str(o["value"]), "label": str(o["label"])}
                                           for o in lookup["options"]],
                               "optionsBinder": {"className": "", "properties": {}}}}
    return {"className": "org.joget.plugin.enterprise.OptionsValueFormatter",
            "properties": {"optionsBinder": {
                "className": "org.joget.apps.form.lib.FormOptionsBinder",
                "properties": {"formDefId": lookup["formDefId"],
                               "idColumn": lookup.get("idColumn", "code").removeprefix("c_"),
                               "labelColumn": lookup.get("labelColumn", "name").removeprefix("c_"),
                               "addEmptyOption": "true", "groupingColumn": "", "useAjax": "",
                               "extraCondition": "", "cacheInterval": "", "emptyLabel": ""}},
                "options": []}}


STORED_DATE = "yyyy-MM-dd"                 # a date control stores this (gen_forms DatePicker dataFormat)
STORED_MOMENT = "yyyy-MM-dd HH:mm:ss"      # a moment the transition guard sets is stored so


def date_format(fmt):
    """0.11.0 (METHOD-2026-09-25-11, item 3): the spec's `format` - the day-first pattern the
    person reads - is the DateFormatter's displayFormat; its dataFormat is the format the value
    is STORED in, which the formatter parses before it shows it. Up to 0.10.0 the pattern went
    into dataFormat and displayFormat was left empty, so a stored 2026-09-25 did not parse as
    dd/MM/yyyy, and the column showed the stored value, not the date day first. The stored
    format is the spec's `dataFormat` where it names one, else the format of the library's own
    writers: yyyy-MM-dd HH:mm:ss for a pattern that shows the time, yyyy-MM-dd for a date."""
    shown = fmt.get("format", "dd/MM/yyyy")
    stored = fmt.get("dataFormat") or (STORED_MOMENT if re.search(r"[Hh]", shown) else STORED_DATE)
    return {"className": "org.joget.plugin.enterprise.DateFormatter",
            "properties": {"dataFormat": stored, "displayFormat": shown}}


def _java_str(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def currency_format(fmt):
    """0.12.0 (item 18 of MT-DM's hand-off of 5 November 2026): an amount, shown with its grouping
    and its decimals and, where the spec names one, its currency's symbol before it - "€9,850.00"
    for 9850 with `symbol: "€"`. The platform 9.0.7 carries no number formatter for a list column:
    its formatters are OptionsValueFormatter, NestedDatalistFormatter, ProcessStatusDatalistFormatter,
    ConditionFormatter, DateFormatter and BeanShellFormatter (jw-enterprise-plugins 9.0.7), and
    DefaultFormatter and ImageFormatter (wflow-core 9.0.7). So the amount is written through the
    platform's own BeanShellFormatter, as the status badge is, by java.text.DecimalFormat with
    English symbols, whatever the server's locale. Spec keys, each optional: `symbol` (none by
    default) and `decimals` (2 by default, 0 to 6). The value is read with the spaces around it
    taken off; one that does not read as a number - an empty value or none among them - is shown
    as stored, an empty or absent value as empty."""
    decimals = fmt.get("decimals", 2)
    if isinstance(decimals, bool) or not isinstance(decimals, int) or not 0 <= decimals <= 6:
        raise ValueError(f"a currency column's decimals must be a whole number from 0 to 6, not {decimals!r}")
    symbol = str(fmt.get("symbol") or "")
    prefix = "'" + symbol.replace("'", "''") + "'" if symbol else ""
    pattern = prefix + "#,##0" + ("." + "0" * decimals if decimals else "")
    script = (
        'String v = (value==null)?"":value.toString().trim();'
        "try {"
        "java.text.DecimalFormat f = new java.text.DecimalFormat(" + _java_str(pattern) + ","
        " java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ENGLISH));"
        "return f.format(new java.math.BigDecimal(v));"
        "} catch (Exception e) { return v; }")
    return {"className": "org.joget.plugin.enterprise.BeanShellFormatter",
            "properties": {"script": script}}


def col(idx, name, label, fmt=None, action=None, render_html=False):
    """Full canonical column shape — ALWAYS sortable; label cleaned of provenance leaks."""
    return {"name": name, "id": f"column_{idx}", "label": clean_label(label), "hidden": "false",
            "renderHtml": "true" if render_html else "", "format": fmt or {"className": "", "properties": {}},
            "sortable": "true", "datalist_type": "column", "exclude_export": "", "width": "",
            "headerAlignment": "", "alignment": "", "style": "",
            "action": action or {"className": "", "properties": {}}}


PLAIN_NAME = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def filter_entry(idx, f):
    """f: {id, label?, type? (text|select), options?, param?, optionsBinder?, controlField?}.
    JDBC SQL reads #requestParam.<param>#.

    0.11.0, a select filter over a list of values (METHOD-2026-09-25-11, item 2):
      * `optionsBinder` - the platform's own options binder ({className, properties}), placed on
        the filter as it stands. The filter then offers the rows the binder reads when the list is
        shown - a list of values' values in force, in its order, as the binder's query asks -
        instead of the inline `options` fixed at the build. (SelectBoxDataListFilterType,
        9.0.7: the binder's rows replace the inline options; the enterprise JDBC options binder
        reads value, label and grouping from its query's first three columns, in its order.)
      * `controlField` - a filter over a DIVIDED list is chosen in two steps: the category's
        filter first, then the value's filter, which names the category's filter here. The
        platform then offers only the values whose grouping (the value binder's third column)
        holds the category chosen, as the form's two drop-downs do. The category's filter is an
        ordinary select filter: like every filter it narrows the rows by its own column, so the
        list's rows carry the category under that name. See check_controls.
    Absent both keys the emission is byte-identical to 0.10.0.

    0.12.0, a select filter of several choices (item 10 of MT-DM's hand-off of 5 November 2026):
      * `multiple` - true makes the filter a choice of several: SelectBoxDataListFilterType's
        `multiple` is written "true", the platform's own value for its multi-select box
        (jw-enterprise-plugins 9.0.7, selectBoxDataListFilterType.json; its template then draws
        the select box `multiple`). How a query list's own SQL reads the several values is the
        model's (the kit's half, R6). Absent the key, or false, `multiple` is written "" as
        before, and the emission is byte-identical to 0.11.0."""
    fid = f["id"]
    param = f.get("param", "f_" + fid)
    ftype = f.get("type", "text")
    if ftype == "select":
        opts = [{"value": "", "label": "- any -"}]
        opts += [o if isinstance(o, dict) else {"value": o, "label": o} for o in f.get("options", [])]
        t = {"className": "org.joget.plugin.enterprise.SelectBoxDataListFilterType",
             "properties": {"label": "", "defaultValue": "", "multiple": "true" if f.get("multiple") else "",
                            "size": "", "options": opts}}
        if f.get("optionsBinder"):
            t["properties"]["optionsBinder"] = f["optionsBinder"]
        if f.get("controlField"):
            t["properties"]["controlField"] = f["controlField"]
    elif ftype == "date_range":
        # a from/to date picker; formatJava is the STORED format it compares against.
        t = {"className": "org.joget.plugin.enterprise.DateRangeDataListFilterType",
             "properties": {"showFieldLabel": "true", "fromLabel": f.get("fromLabel", "From"),
                            "toLabel": f.get("toLabel", "To"), "format": "dd/mm/yy",
                            "formatJava": "yyyy-MM-dd", "yearRange": "c-10:c+10", "enableTime": ""}}
    else:
        t = {"className": "org.joget.apps.datalist.lib.TextFieldDataListFilterType", "properties": {}}
    return {"name": fid, "id": f"filter_{idx}", "label": f.get("label", fid),
            "type": t, "filterParamName": param}


def check_controls(dl_id, filters):
    """0.11.0: a filter's `controlField` names an EARLIER select filter of the same list, by a
    plain name. Otherwise the second step has no first: the platform's script finds the control
    by its name, unquoted (jquery.dynamicoptions.js, `[name=...]`), so a name it cannot find - or
    one with a dot, which breaks that lookup - leaves the value's drop-down offering nothing, and
    nothing says why. Refused here instead."""
    seen = {}
    for f in filters:
        ctl = f.get("controlField")
        if ctl:
            if f.get("type", "text") != "select":
                raise ValueError(f"datalist '{dl_id}': filter '{f['id']}' has a controlField "
                                 f"but is not a select filter")
            if seen.get(ctl) != "select":
                what = ("stands after it or is not in the list" if ctl not in seen
                        else "is not a select filter")
                raise ValueError(f"datalist '{dl_id}': filter '{f['id']}' names controlField "
                                 f"'{ctl}', which {what} - the category is chosen first, in a "
                                 f"select filter of the same list")
            if not PLAIN_NAME.match(ctl):
                raise ValueError(f"datalist '{dl_id}': filter '{f['id']}' names controlField "
                                 f"'{ctl}', which is not a plain name (letters, digits, _) - the "
                                 f"platform finds the control by its name unquoted")
        seen[f["id"]] = f.get("type", "text")


def row_action(idx, ra):
    """ra: {label, to (target menu customId), hrefColumn, hrefParam}. HyperlinkDataListAction."""
    href = ra["href"] if ra.get("href") else f"{UV}/{ra['to']}"
    props = {"label": ra.get("label", "Open"), "href": href,
             "hrefColumn": ra.get("hrefColumn", "id"),
             "hrefParam": ra.get("hrefParam", ""), "target": ra.get("target", ""),
             "confirmation": ""}
    if ra.get("shownWhen"):
        # METHOD-2026-10-09-05, item 8: shown only on a row whose column `shownWhen` holds a value
        # — the platform's own rule on a row action (DataListDecorator.isRowActionVisible, Joget
        # DX 8 and 9). `<>` '' and not `IS NOT EMPTY`, which the platform holds on every row (it
        # reads a missing value as ''). Absent the key, the row action is as before.
        props["rules"] = [{"join": "AND", "field": str(ra["shownWhen"]), "operator": "<>",
                           "value": ""}]
    return {"className": "org.joget.apps.datalist.lib.HyperlinkDataListAction",
            "id": f"rowAction_{idx}", "label": ra.get("label", "Open"),
            "properties": props}


def list_action(idx, a):
    """a: {label, href | to, hrefColumn, hrefParam}. An act on the rows a person chooses: the same
    HyperlinkDataListAction among the datalist's own `actions`, which the platform shows as a
    button over the list, with a box on each row to choose it; on the button it writes each chosen
    row's value of hrefColumn, joined by ';', into hrefParam (wflow-core 9.0.7,
    HyperlinkDataListAction.executeAction)."""
    out = row_action(idx, a)
    out["id"] = f"action_{idx}"
    return out


#: 0.12.0 (item 21): the sort direction as the platform reads it. wflow-core 9.0.7,
#: DataList.getQueryParam, sorts descending only where the list's default order equals
#: DataList.ORDER_DESCENDING_VALUE, "1"; any other value is ascending. The datalist builder writes
#: "1" for descending and "2" or "" for ascending (jw/js/dbuilder.core.js, the `order` select box).
#: Ascending is written "" - the builder's "no order chosen", read ascending - so that a list
#: sorted ascending carries no order of its own. Up to 0.11.0 the generator wrote "DESC" and
#: "ASC", and the platform showed every descending list ascending.
ORDER_DESC = "1"
ORDER_ASC = ""


def sort_order(desc) -> str:
    """The platform's `order` for a spec's `sort.desc`."""
    return ORDER_DESC if desc else ORDER_ASC


def envelope(dl_id, name, binder, cols, filters, row_actions, order_by="", order=ORDER_ASC,
             actions=None):
    return {"id": dl_id, "name": name, "binder": binder, "columns": cols,
            "filters": filters, "actions": list(actions or []), "rowActions": row_actions,
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
                elif fmt and fmt.get("type") == "status_badge":
                    cols.append(col(i, c["id"], c["label"], badge_format(fmt), action, render_html=True))
                elif fmt and fmt.get("type") == "date":
                    cols.append(col(i, c["id"], c["label"], date_format(fmt), action))
                elif fmt and fmt.get("type") == "currency":
                    cols.append(col(i, c["id"], c["label"], currency_format(fmt), action))
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
                # 0.9.11: binder joins - the deployed farmersPortal register shape,
                # measured 2026-08-08: joinFieldId is always the parent row `id`.
                # Absent joins the emission is byte-identical to 0.9.10.
                if d["binder"].get("joins"):
                    binder["properties"]["joins"] = [
                        {"joinFieldId": "id", "tableName": j["table"],
                         "fieldId": f"{j['table']}.{j['fk']}"}
                        for j in d["binder"]["joins"]]
            check_controls(d["id"], d.get("filters", []))
            filters = [filter_entry(i, f) for i, f in enumerate(d.get("filters", []))]
            ras = [row_action(i, r) for i, r in enumerate(d.get("rowActions", []))]
            las = [list_action(i, a) for i, a in enumerate(d.get("actions", []))]
            dl = envelope(d["id"], d["name"], binder, cols, filters, ras,
                          d.get("sort", {}).get("column", ""),
                          sort_order(d.get("sort", {}).get("desc")), actions=las)
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
