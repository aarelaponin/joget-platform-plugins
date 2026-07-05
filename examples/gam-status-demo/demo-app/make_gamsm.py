#!/usr/bin/env python3
"""Emit the 5 Joget form defs for the GAM status-manager reuse-proof app (gamsm).

Deliberately tiny: just enough tables for the platform StatusManager + CaseEventWriter
to run over a non-debt 'gamWidget' entity. The gamMove trigger form carries the
GamMoveGuard post-processor (runOn create).
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), "generated")


def tf(fid, label, readonly=False, mandatory=False):
    p = {"id": fid, "label": label, "value": "", "placeholder": "", "maxlength": "",
         "size": "", "encryption": "", "storeNumeric": "", "readonly": "true" if readonly else "",
         "readonlyLabel": "", "style": "", "requiredSanitize": "", "workflowVariable": "",
         "validator": {"className": "org.joget.apps.form.lib.DefaultValidator",
                       "properties": {"mandatory": "true" if mandatory else "", "type": "", "message": ""}}}
    return {"className": "org.joget.apps.form.lib.TextField", "properties": p}


def form(fid, name, fields, post=None, run_on="create"):
    props = {"id": fid, "name": name, "tableName": fid,
             "loadBinder": {"className": "org.joget.apps.form.lib.WorkflowFormBinder", "properties": {}},
             "storeBinder": {"className": "org.joget.apps.form.lib.WorkflowFormBinder", "properties": {}},
             "permission": {"className": "", "properties": {}}, "noPermissionMessage": ""}
    if post:
        props["postProcessor"] = {"className": post[0], "properties": post[1]}
        props["postProcessorRunOn"] = run_on
    section = {"className": "org.joget.apps.form.model.Section",
               "properties": {"id": "s1", "label": name},
               "elements": [{"className": "org.joget.apps.form.model.Column",
                             "properties": {"width": "100%"},
                             "elements": fields}]}
    return {"className": "org.joget.apps.form.model.Form", "properties": props, "elements": [section]}


FORMS = {
    "gamWidget": form("gamWidget", "GAM Widget (demo entity)",
                      [tf("name", "Name"), tf("status", "Status")]),
    "gamEvent": form("gamEvent", "GAM Event Chain",
                     [tf(x, x) for x in ["caseId", "seq", "eventType", "actor",
                                          "eventTime", "prevHash", "hash", "payload"]]),
    "mmEntityState": form("mmEntityState", "MM Entity State",
                          [tf(x, x) for x in ["entity", "scope", "code", "isInitial", "isTerminal"]]),
    "mmEntityTransition": form("mmEntityTransition", "MM Entity Transition",
                               [tf(x, x) for x in ["entity", "scope", "fromStatus", "toStatus"]]),
    "gamMove": form("gamMove", "GAM Status Move (trigger)",
                    [tf("widgetId", "Widget id", mandatory=True),
                     tf("targetStatus", "Target status", mandatory=True),
                     tf("reason", "Reason"),
                     tf("result", "Result (set by guard)", readonly=True),
                     tf("resultAt", "Result at", readonly=True)],
                    post=("com.fiscaladmin.gam.statusdemo.GamMoveGuard", {})),
}


def main():
    for sub in ("forms", "datalists", "userviews"):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    for fid, d in FORMS.items():
        with open(os.path.join(OUT, "forms", fid + ".json"), "w") as f:
            json.dump(d, f, indent=2)
    print("wrote", len(FORMS), "forms to", os.path.join(OUT, "forms"))


if __name__ == "__main__":
    main()
