#!/usr/bin/env python3
"""Project-neutral reference userview generator.

The delivery userview generators (per project) fork this shape and add project-specific
policy — category buckets, role maps, per-form save overrides, theme JavaScript, a
project-namespaced uuid seed. This reference generator keeps ONLY the project-neutral
structural core, so it can serve as the round-trip oracle for spec-to-code projectors
(joget-spec-kit) without embedding any project content:

  * categories emitted AS AUTHORED (no bucket collapse / reordering);
  * per-category GroupPermission from an optional `role` (absent = open);
  * CrudMenu / DataListMenu / FormMenu — the platform-neutral menu shapes;
  * a clean Dx8TrimedaTheme (PWA/push disabled per delta D-045) with NO project JS/CSS;
  * deterministic ids: uuid5 over a neutral namespace (same spec → same JSON).

The JSON shapes are extracted from a production-proven delivery generator, so the output
is valid Joget userview definition JSON; the policy that made that generator project-
specific is exactly what is left out.

Spec (YAML):
    userview:
      id: uvExample
      name: "Example"
      footer: "Example"            # optional
      categories:
        - id: cat_ops
          label: "Operations"
          role: "group_officer"    # optional → GroupPermission allowedGroupIds; absent = open
          menus:
            - {type: crud,     label: "Cases",  formId: frmCase, datalistId: list_frmCase}
            - {type: datalist, label: "Queue",  datalistId: list_queue}
            - {type: form,     label: "About",  formId: frmAbout}

Usage:  gen_userview.py <spec.yml> <out_dir>
"""
import json
import os
import sys
import uuid

import yaml

NS = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
SEED = "joget-userview:"   # neutral namespace (NOT a project prefix)


def uid(name):
    return str(uuid.uuid5(NS, SEED + name))


def _selection(consumes):
    """The selection column (checkbox) belongs ONLY when a selection-consuming action
    exists on the menu — today that is the delete action; a bulk action would extend it.
    Without one, the checkboxes are a dead surface the user cannot act on (A001; STA-01/05).
    Returns (checkboxPosition, selectionType). T3 (on jdx7): confirm empty strings suppress
    the column on CrudMenu/DataListMenu 9.0.7 — the design's proposed 'off' value."""
    return ("left", "multiple") if consumes else ("", "")


def crud_menu(m):
    fid = m["formId"]
    dl = m.get("datalistId", f"list_{fid}")
    # customId keyed by the DATALIST: two crud menus may expose the same form over
    # different lists (worklist vs all-cases) — form-keyed ids would collide.
    # add=False -> no addFormId -> CrudMenu renders no New button (engine/audited-path
    # creation only); delete=False -> delete button off (e.g. case history retention).
    # Selection column rides on a consuming action (delete on, the default) — delete:false
    # with no bulk action gets no dead checkbox column (A001).
    cbpos, seltype = _selection(bool(m.get("delete", True)))
    return {"className": "org.joget.plugin.enterprise.CrudMenu", "properties": {
        "id": uid("menu:" + dl + ":" + fid), "label": m["label"],
        "addFormId": fid if m.get("add", True) else "", "editFormId": fid,
        "datalistId": dl, "customId": f"{dl}_crud",
        "add-afterSaved": "list", "edit-afterSaved": "list",
        "list-showDeleteButton": "yes" if m.get("delete", True) else "", "rowCount": "true",
        "buttonPosition": "bothLeft", "checkboxPosition": cbpos,
        "selectionType": seltype, "iconIncluded": False}}


def datalist_menu(m):
    dl = m["datalistId"]
    # a read-list menu carries no delete by default; selection UI only if a bulk action
    # (or an explicit delete) is declared, else the checkbox column is dead (A001).
    cbpos, seltype = _selection(bool(m.get("delete", False)))
    return {"className": "org.joget.apps.userview.lib.DataListMenu", "properties": {
        "id": uid("menu:dl:" + dl), "customId": dl, "label": m["label"], "datalistId": dl,
        "rowCount": "", "buttonPosition": "bothLeft", "checkboxPosition": cbpos,
        "selectionType": seltype, "iconIncluded": False}}


def form_menu(m):
    """A FormMenu the user can actually SUBMIT, unless the spec asks for a display-only one.

    0.9.7: `readonly` was hardcoded "true" here, so every form menu the kit has ever emitted
    rendered uneditable — "New registration" could not be filled in. The acceptance suites never
    saw it because they drive the data API, which is a different door; the defect is only visible
    to a person clicking. Found 2026-08-02 by the owner, on the fifteenth deployment of an app
    whose gate was green.
    Read-only stays expressible (`readonly: true` on the menu) because a display-only form menu is
    a real thing — a printable summary, a confirmation page. It is now a choice rather than the
    only possible outcome.
    """
    fid = m["formId"]
    ro = bool(m.get("readonly", False))
    return {"className": "org.joget.apps.userview.lib.FormMenu", "properties": {
        "id": uid("menu:form:" + fid), "customId": fid, "label": m["label"],
        "formId": fid, "readonly": "true" if ro else "", "readonlyLabel": "",
        "messageShowAfterComplete": "", "iconIncluded": False}}


def inbox_menu(m):
    """Workflow task inbox (assigned activities). Empty processId/assignmentToDisplay = all
    assignments across the running processes; a processId scopes it to one process."""
    slug = "".join(c if c.isalnum() else "_" for c in m["label"]).strip("_").lower() or "inbox"
    return {"className": "org.joget.apps.userview.lib.InboxMenu", "properties": {
        "id": uid("menu:inbox:" + m["label"]), "customId": "inbox_" + slug, "label": m["label"],
        "assignmentToDisplay": "", "processId": m.get("processId", ""),
        "rowCount": "", "buttonPosition": "bothLeft", "showPopup": "", "iconIncluded": False}}


MENU_BUILDERS = {"crud": crud_menu, "datalist": datalist_menu, "form": form_menu,
                 "inbox": inbox_menu}


def permission(role):
    if not role:
        return {"className": "", "properties": {}}
    # DX GroupPermission reads a ;-delimited multiselect property `allowedGroupIds` (delta D-044).
    return {"className": "org.joget.apps.userview.lib.GroupPermission",
            "properties": {"allowedGroupIds": role}}


def report_menu(m, reports_dir):
    """A report menu inlines the generated JasperReportsMenu (jrxml already embedded by
    gen_reports). Exposing a report is just placing that element into the category; the
    navigation label wins over the report's own name."""
    rid = m["reportId"]
    if not reports_dir:
        raise ValueError(f"report menu '{rid}' needs the reports dir (gen_reports output)")
    path = os.path.join(reports_dir, rid + ".json")
    if not os.path.exists(path):
        raise ValueError(f"report menu '{rid}': {path} not found — run gen_reports first")
    el = json.load(open(path))
    el["properties"]["label"] = m["label"]
    return el


def dashboard_menus(m, dashboards_dir):
    """A dashboard menu inlines the generated dashboard's chart menus (SqlChartMenu elements
    already emitted by gen_dashboards) into the category — one dashboard expands to N chart
    menus (parallel to report_menu, 1→N). If the dashboard has exactly one chart, the nav
    label wins over the chart's own label; with several, each chart keeps its label."""
    did = m["dashboardId"]
    if not dashboards_dir:
        raise ValueError(f"dashboard menu '{did}' needs the dashboards dir (gen_dashboards output)")
    path = os.path.join(dashboards_dir, did + ".json")
    if not os.path.exists(path):
        raise ValueError(f"dashboard menu '{did}': {path} not found — run gen_dashboards first")
    charts = json.load(open(path)).get("menus", [])
    if len(charts) == 1 and m.get("label"):
        charts[0]["properties"]["label"] = m["label"]
    return charts


def category(cat, reports_dir=None, dashboards_dir=None):
    menus = []
    for m in cat["menus"]:
        if m.get("type") == "report":
            menus.append(report_menu(m, reports_dir)); continue
        if m.get("type") == "dashboard":
            menus.extend(dashboard_menus(m, dashboards_dir)); continue
        builder = MENU_BUILDERS.get(m.get("type"))
        if builder is None:
            raise ValueError(f"unknown menu type '{m.get('type')}' in category '{cat['id']}'")
        menus.append(builder(m))
    return {"className": "org.joget.apps.userview.model.UserviewCategory",
            "menus": menus, "properties": {
                "hide": "", "permission": permission(cat.get("role")), "comment": "",
                "id": "category-" + uid("category:" + cat["id"]),
                "label": cat["label"], "iconIncluded": ""}}


def build_userview(spec, reports_dir=None, dashboards_dir=None):
    uv = spec["userview"]
    uv_id, uv_name = uv["id"], uv["name"]
    return {"className": "org.joget.apps.userview.model.Userview",
            "categories": [category(c, reports_dir, dashboards_dir) for c in uv["categories"]],
            "properties": {"logoutText": "Logout", "welcomeMessage": "",
                           "name": uv_name, "description": "",
                           "footerMessage": uv.get("footer", ""), "id": uv_id},
            "setting": {"properties": {
                "tempDisablePermissionChecking": "", "userviewDescription": "",
                "userviewId": uv_id, "hideThisUserviewInAppCenter": "",
                "userview_thumbnail": "", "userview_category": "",
                "theme": {"className": "org.joget.apps.userview.lib.Dx8TrimedaTheme",
                          "properties": {"disablePwa": "true", "disablePush": "true",
                                         "js": "", "css": ""}},
                "permission": {"className": "", "properties": {}},
                "userviewName": uv_name}}}


def main():
    spec_path, out_dir = sys.argv[1], sys.argv[2]
    reports_dir = sys.argv[3] if len(sys.argv) > 3 else None    # optional: gen_reports output
    dashboards_dir = sys.argv[4] if len(sys.argv) > 4 else None  # optional: gen_dashboards output
    spec = yaml.safe_load(open(spec_path))
    uv = build_userview(spec, reports_dir, dashboards_dir)
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, uv["properties"]["id"] + ".json")
    with open(out, "w") as f:
        json.dump(uv, f, indent=2)
    n = sum(len(c["menus"]) for c in uv["categories"])
    print(f"userview {uv['properties']['id']}: {len(uv['categories'])} categories, {n} menus -> {out}")


if __name__ == "__main__":
    main()
