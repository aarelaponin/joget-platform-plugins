#!/usr/bin/env python3
"""Build a Joget JWA (app export zip) from generated artefacts (P5-deterministic).

Format reverse-engineered from a live DX9 export: appDefinition.xml with
<formDefinitionList>/<datalistDefinitionList>/<userviewDefinitionList> entries,
each carrying HTML-escaped JSON in <json>.

Usage: build_jwa.py <generated_dir> <app_id> <app_name> <out.jwa>

If <generated_dir>/workflow/ contains package.xpdl +
packageDefinition.fragment.xml (from gen_workflow.py), the fragment is
spliced into <packageDefinitionList> and package.xpdl is added at zip root.
"""
import html
import json
import os
import sys
import zipfile


def esc(s):
    return html.escape(s, quote=True)


def main():
    gen, app_id, app_name, out = sys.argv[1:5]
    forms, datalists, userviews = [], [], []

    for fn in sorted(os.listdir(f"{gen}/forms")):
        d = json.load(open(f"{gen}/forms/{fn}"))
        p = d["properties"]
        forms.append(f"""      <formDefinition>
         <id>{p['id']}</id>
         <appId>{app_id}</appId>
         <appVersion>1</appVersion>
         <name>{esc(p['name'])}</name>
         <tableName>{p['tableName']}</tableName>
         <json>{esc(json.dumps(d))}</json>
      </formDefinition>""")

    for fn in sorted(os.listdir(f"{gen}/datalists")):
        d = json.load(open(f"{gen}/datalists/{fn}"))
        datalists.append(f"""      <datalistDefinition>
         <id>{d['id']}</id>
         <appId>{app_id}</appId>
         <appVersion>1</appVersion>
         <name>{esc(d['name'])}</name>
         <description></description>
         <json>{esc(json.dumps(d))}</json>
      </datalistDefinition>""")

    for fn in sorted(os.listdir(f"{gen}/userviews")):
        d = json.load(open(f"{gen}/userviews/{fn}"))
        p = d["properties"]
        userviews.append(f"""      <userviewDefinition>
         <id>{p['id']}</id>
         <appId>{app_id}</appId>
         <appVersion>1</appVersion>
         <name>{esc(p['name'])}</name>
         <description></description>
         <json>{esc(json.dumps(d))}</json>
      </userviewDefinition>""")

    # API Builder definition: one AppFormAPI element per form (data path for seeds/tests)
    form_ids = sorted(json.load(open(f"{gen}/forms/{fn}"))["properties"]["id"]
                      for fn in os.listdir(f"{gen}/forms") if fn.endswith(".json"))
    api_def = {"properties": {"id": f"API-{app_id}-data", "name": f"{app_id} data API",
               "description": "", "termsOfService": "", "contactName": "",
               "contactEmail": "", "licenseName": "", "licenseUrl": "",
               "externalDocUrl": "", "externalDocDesc": "", "enableInternal": ""},
               "elements": [{"className": "org.joget.api.lib.AppFormAPI",
                             "properties": {"id": f"E{i:04d}", "formDefId": fid, "label": "",
                                            "ignorePermission": "true",
                                            "ENABLED_PATHS": "post:/;post:/saveOrUpdate;get:/"}}
                            for i, fid in enumerate(form_ids, 1)]}
    builder = f"""      <builderDefinition>
         <id>API-{app_id}-data</id>
         <appId>{app_id}</appId>
         <appVersion>1</appVersion>
         <name>{app_id} data API</name>
         <type>api</type>
         <json>{esc(json.dumps(api_def))}</json>
      </builderDefinition>"""

    wf_dir = os.path.join(gen, "workflow")
    xpdl = None
    pkg_list = "   <packageDefinitionList/>"
    if os.path.isdir(wf_dir) and os.path.exists(f"{wf_dir}/package.xpdl"):
        xpdl = open(f"{wf_dir}/package.xpdl").read()
        fragment = open(f"{wf_dir}/packageDefinition.fragment.xml").read()
        pkg_list = f"   <packageDefinitionList>\n{fragment}\n   </packageDefinitionList>"

    xml = f"""<appDefinition>
   <id>{app_id}</id>
   <version>1</version>
   <name>{esc(app_name)}</name>
   <published>false</published>
{pkg_list}
   <formDefinitionList>
{chr(10).join(forms)}
   </formDefinitionList>
   <datalistDefinitionList>
{chr(10).join(datalists)}
   </datalistDefinitionList>
   <userviewDefinitionList>
{chr(10).join(userviews)}
   </userviewDefinitionList>
   <builderDefinitionList>
{builder}
   </builderDefinitionList>
   <environmentVariableList/>
   <pluginDefaultPropertiesList/>
   <resourceList/>
</appDefinition>
"""
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("appDefinition.xml", xml)
        if xpdl:
            z.writestr("package.xpdl", xpdl)
    print(f"JWA built: {out} ({len(forms)} forms, {len(datalists)} datalists, "
          f"{len(userviews)} userviews, workflow={'yes' if xpdl else 'no'}; "
          f"xml {len(xml)} bytes)")


if __name__ == "__main__":
    main()
