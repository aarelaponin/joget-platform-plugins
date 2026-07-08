#!/usr/bin/env python3
"""Deterministic workflow generator (P5) — joget-workflow-gen shapes.

Builds package.xpdl (XPDL 1.0, namespace http://www.wfmc.org/2002/XPDL1.0 —
Joget's parser is strict) + packageDefinition.fragment.xml (the three maps:
packageActivityFormMap / packageActivityPluginMap / packageParticipantMap)
from a WF-*.spec.yml. Shapes verified against a live Joget export
(jtdeadline app, 8.1.6 app_src). Custom plugins use the MultiTools wrapper
(pattern A). Same spec -> same output.

Usage: gen_workflow.py <wf_spec.yml> <out_dir> <app_id>
"""
import html
import json
import os
import sys

import yaml


def esc(s):
    return html.escape(str(s), quote=True)


def main():
    spec_path, out_dir, app_id = sys.argv[1:4]
    spec = yaml.safe_load(open(spec_path))
    pkg = spec["package"]
    participants = spec["participants"]
    variables = spec.get("variables", [])
    processes = spec["processes"]

    # ---------------- package.xpdl ----------------
    parts_xml = "\n".join(
        f'        <Participant Id="{p["id"]}" Name="{esc(p["name"])}">\n'
        f'            <ParticipantType Type="ROLE"/>\n'
        f"        </Participant>" for p in participants)

    procs_xml = []
    for proc in processes:
        fields = "\n".join(
            f'            <DataField Id="{v}" IsArray="FALSE">\n'
            f"                <DataType>\n"
            f'                    <BasicType Type="STRING"/>\n'
            f"                </DataType>\n"
            f"            </DataField>" for v in variables)
        acts_xml = []
        last_manual_performer = participants[0]["id"]
        for a in proc["activities"]:
            if a["type"] == "manual":
                impl = "<No/>"
                performer = a["performer"]
                last_manual_performer = performer
            elif a["type"] == "tool":
                impl = '<Tool Id="default_application"/>'
                performer = a.get("performer", last_manual_performer)
            else:
                raise SystemExit(f"unsupported activity type: {a['type']}")
            acts_xml.append(
                f'                <Activity Id="{a["id"]}" Name="{esc(a.get("name", a["id"]))}">\n'
                f"                    <Implementation>\n"
                f"                        {impl}\n"
                f"                    </Implementation>\n"
                f"                    <Performer>{performer}</Performer>\n"
                f"                </Activity>")
        trans_xml = "\n".join(
            f'                <Transition From="{t["from"]}" Id="t{i}" To="{t["to"]}"/>'
            for i, t in enumerate(proc["transitions"], 1))
        procs_xml.append(
            f'        <WorkflowProcess Id="{proc["id"]}" Name="{esc(proc["name"])}">\n'
            f'            <ProcessHeader DurationUnit="h"/>\n'
            f"            <DataFields>\n{fields}\n            </DataFields>\n"
            f"            <Activities>\n" + "\n".join(acts_xml) + "\n            </Activities>\n"
            f"            <Transitions>\n{trans_xml}\n            </Transitions>\n"
            f"        </WorkflowProcess>")

    xpdl = f"""<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<Package xmlns="http://www.wfmc.org/2002/XPDL1.0" xmlns:xpdl="http://www.wfmc.org/2002/XPDL1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" Id="{pkg['id']}" Name="{esc(pkg['name'])}" xsi:schemaLocation="http://www.wfmc.org/2002/XPDL1.0 http://wfmc.org/standards/docs/TC-1025_schema_10_xpdl.xsd">
    <PackageHeader>
        <XPDLVersion>1.0</XPDLVersion>
        <Vendor/>
        <Created/>
    </PackageHeader>
    <Script Type="text/javascript"/>
    <Participants>
{parts_xml}
    </Participants>
    <Applications>
        <Application Id="default_application"/>
    </Applications>
    <WorkflowProcesses>
{chr(10).join(procs_xml)}
    </WorkflowProcesses>
</Package>
"""

    # ---------------- packageDefinition fragment ----------------
    form_entries, plugin_entries, part_entries = [], [], []
    for proc in processes:
        pid = proc["id"]
        start_activity = next(a["id"] for a in proc["activities"] if a["type"] == "manual")
        for a in proc["activities"]:
            if a["type"] == "manual":
                form_entries.append(f"""            <entry>
               <string>{pid}::{a['id']}</string>
               <packageActivityForm>
                  <processDefId>{pid}</processDefId>
                  <activityDefId>{a['id']}</activityDefId>
                  <formId>{a['form']}</formId>
                  <type>SINGLE</type>
                  <autoContinue>false</autoContinue>
                  <disableSaveAsDraft>false</disableSaveAsDraft>
               </packageActivityForm>
            </entry>""")
            elif a["type"] == "tool":
                if "tools" in a:  # multi-tool chain: sequential MultiTools
                    tool_list = [{"className": t["className"],
                                  "properties": {k: str(v) for k, v in t.get("config", {}).items()}}
                                 for t in a["tools"]]
                elif a.get("plugin") == "custom":
                    tool_list = [{"className": a["className"],
                                  "properties": {k: str(v) for k, v in a.get("config", {}).items()}}]
                else:
                    raise SystemExit(f"only custom plugins supported so far: {a['id']}")
                props = {"runInMultiThread": "", "comment": a.get("name", a["id"]),
                         "tools": tool_list}
                plugin_name = "org.joget.apps.app.lib.MultiTools"
                plugin_entries.append(f"""            <entry>
               <string>{pid}::{a['id']}</string>
               <packageActivityPlugin>
                  <processDefId>{pid}</processDefId>
                  <activityDefId>{a['id']}</activityDefId>
                  <pluginName>{plugin_name}</pluginName>
                  <pluginProperties>{esc(json.dumps(props))}</pluginProperties>
               </packageActivityPlugin>
            </entry>""")
        for p in participants:
            r = p["resolve"]
            rtype = r["type"]
            value = start_activity if rtype == "requester" else r.get("value", "")
            part_entries.append(f"""            <entry>
               <string>{pid}::{p['id']}</string>
               <packageParticipant>
                  <processDefId>{pid}</processDefId>
                  <participantId>{p['id']}</participantId>
                  <type>{rtype}</type>
                  <value>{esc(value)}</value>
               </packageParticipant>
            </entry>""")

    fragment = f"""      <packageDefinition>
         <appId>{app_id}</appId>
         <id>{pkg['id']}</id>
         <name>{esc(pkg['name'])}</name>
         <packageActivityFormMap>
{chr(10).join(form_entries)}
         </packageActivityFormMap>
         <packageActivityPluginMap>
{chr(10).join(plugin_entries)}
         </packageActivityPluginMap>
         <packageParticipantMap>
{chr(10).join(part_entries)}
         </packageParticipantMap>
      </packageDefinition>"""

    os.makedirs(out_dir, exist_ok=True)
    open(os.path.join(out_dir, "package.xpdl"), "w").write(xpdl)
    open(os.path.join(out_dir, "packageDefinition.fragment.xml"), "w").write(fragment)

    # ---- reference self-check (skill section 13, subset) ----
    errs = []
    for proc in processes:
        ids = {a["id"] for a in proc["activities"]}
        pids = {p["id"] for p in participants}
        for t in proc["transitions"]:
            if t["from"] not in ids or t["to"] not in ids:
                errs.append(f"transition {t['from']}->{t['to']}: unknown activity")
        for a in proc["activities"]:
            if a["type"] == "manual" and a.get("performer") not in pids:
                errs.append(f"{a['id']}: performer not a participant")
        if "processStartWhiteList" not in pids:
            errs.append("processStartWhiteList participant missing")
    n_acts = sum(len(p["activities"]) for p in processes)
    print(f"workflow {pkg['id']}: {len(processes)} process(es), {n_acts} activities, "
          f"{len(form_entries)} form-map / {len(plugin_entries)} plugin-map / "
          f"{len(part_entries)} participant-map entries -> {out_dir}")
    print("reference check:", errs if errs else "PASS")
    sys.exit(1 if errs else 0)


if __name__ == "__main__":
    main()
