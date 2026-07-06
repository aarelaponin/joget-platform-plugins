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


def crud_menu(m):
    fid = m["formId"]
    return {"className": "org.joget.plugin.enterprise.CrudMenu", "properties": {
        "id": uid("menu:" + fid), "label": m["label"],
        "addFormId": fid, "editFormId": fid,
        "datalistId": m.get("datalistId", f"list_{fid}"), "customId": f"{fid}_crud",
        "add-afterSaved": "list", "edit-afterSaved": "list",
        "list-showDeleteButton": "yes", "rowCount": "true",
        "buttonPosition": "bothLeft", "checkboxPosition": "left",
        "selectionType": "multiple", "iconIncluded": False}}


def datalist_menu(m):
    dl = m["datalistId"]
    return {"className": "org.joget.apps.userview.lib.DataListMenu", "properties": {
        "id": uid("menu:dl:" + dl), "customId": dl, "label": m["label"], "datalistId": dl,
        "rowCount": "", "buttonPosition": "bothLeft", "checkboxPosition": "left",
        "selectionType": "multiple", "iconIncluded": False}}


def form_menu(m):
    fid = m["formId"]
    return {"className": "org.joget.apps.userview.lib.FormMenu", "properties": {
        "id": uid("menu:form:" + fid), "customId": fid, "label": m["label"],
        "formId": fid, "readonly": "true", "readonlyLabel": "",
        "messageShowAfterComplete": "", "iconIncluded": False}}


MENU_BUILDERS = {"crud": crud_menu, "datalist": datalist_menu, "form": form_menu}


def permission(role):
    if not role:
        return {"className": "", "properties": {}}
    # DX GroupPermission reads a ;-delimited multiselect property `allowedGroupIds` (delta D-044).
    return {"className": "org.joget.apps.userview.lib.GroupPermission",
            "properties": {"allowedGroupIds": role}}


def category(cat):
    menus = []
    for m in cat["menus"]:
        builder = MENU_BUILDERS.get(m.get("type"))
        if builder is None:
            raise ValueError(f"unknown menu type '{m.get('type')}' in category '{cat['id']}'")
        menus.append(builder(m))
    return {"className": "org.joget.apps.userview.model.UserviewCategory",
            "menus": menus, "properties": {
                "hide": "", "permission": permission(cat.get("role")), "comment": "",
                "id": "category-" + uid("category:" + cat["id"]),
                "label": cat["label"], "iconIncluded": ""}}


def build_userview(spec):
    uv = spec["userview"]
    uv_id, uv_name = uv["id"], uv["name"]
    return {"className": "org.joget.apps.userview.model.Userview",
            "categories": [category(c) for c in uv["categories"]],
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
    spec = yaml.safe_load(open(spec_path))
    uv = build_userview(spec)
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, uv["properties"]["id"] + ".json")
    with open(out, "w") as f:
        json.dump(uv, f, indent=2)
    n = sum(len(c["menus"]) for c in uv["categories"])
    print(f"userview {uv['properties']['id']}: {len(uv['categories'])} categories, {n} menus -> {out}")


if __name__ == "__main__":
    main()
