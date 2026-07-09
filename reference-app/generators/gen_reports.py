#!/usr/bin/env python3
"""Deterministic Jasper report generator (P-report) — joget-jasper-report house style.

Reads L2 report specs (a `report:` block) and emits, per report:
  * <id>.jrxml   — a classic-namespace JasperReports design (language="java")
  * <id>.json    — the JasperReportsMenu userview-menu element with the .jrxml inline

A Joget Jasper report IS one `JasperReportsMenu` whose `jrxml` property holds the whole
design; the menu compiles the JRXML at runtime, runs its SQL against the profile datasource,
renders HTML and offers PDF/Excel export. So the entire report ships config-as-code inside the
userview — no GUI designer, no plugin build. The five hard-won rules from the skill are encoded:
language="java" (not groovy); grid props are JSON arrays (parameters=[]); export is a ';'-joined
string; datasource="" = the profile DB; request params are mapped via #requestParam.<name>#.

Usage: gen_reports.py <spec_dir> <out_dir>   ·   same spec -> byte-identical output.
"""
import hashlib
import html
import json
import os
import sys

import yaml

A4 = dict(pageWidth=595, pageHeight=842, left=25, right=25, top=25, bottom=25)
CW = A4["pageWidth"] - A4["left"] - A4["right"]      # column width = 545
LBL_W = 170                                          # label column width in the detail band
ROW_H = 22


def menu_id(custom_id):
    """Deterministic 32-hex element id (Joget menu ids are 32-hex)."""
    return hashlib.md5(custom_id.encode("utf-8")).hexdigest()


def jrxml(rep):
    """Classic-namespace JRXML. One field per detail row (label : value); a title band."""
    name = rep["id"]
    params = "".join(
        f'\n\t<parameter name="{p["name"]}" class="{p.get("class", "java.lang.String")}"/>'
        for p in rep.get("params", []))
    fields = "".join(
        f'\n\t<field name="{f["name"]}" class="{f.get("class", "java.lang.String")}"/>'
        for f in rep["fields"])
    # detail band: each field on its own ruled line (label left, value right)
    rows = []
    for i, f in enumerate(rep["fields"]):
        y = i * ROW_H
        lbl = html.escape(f.get("label", f["name"]))
        rows.append(
            f'\t\t\t<staticText><reportElement x="0" y="{y}" width="{LBL_W}" height="{ROW_H}"/>'
            f'<textElement><font isBold="true"/></textElement>'
            f'<text><![CDATA[{lbl}]]></text></staticText>\n'
            f'\t\t\t<textField><reportElement x="{LBL_W}" y="{y}" width="{CW - LBL_W}" height="{ROW_H}"/>'
            f'<textFieldExpression class="java.lang.String"><![CDATA[$F{{{f["name"]}}}]]>'
            f'</textFieldExpression></textField>')
    detail_h = max(ROW_H, len(rep["fields"]) * ROW_H)
    title = html.escape(rep["name"])
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" '
        f'name="{name}" language="java" pageWidth="{A4["pageWidth"]}" pageHeight="{A4["pageHeight"]}" '
        f'columnWidth="{CW}" leftMargin="{A4["left"]}" rightMargin="{A4["right"]}" '
        f'topMargin="{A4["top"]}" bottomMargin="{A4["bottom"]}">'
        f'{params}\n'
        f'\t<queryString><![CDATA[{rep["sql"]}]]></queryString>'
        f'{fields}\n'
        '\t<title><band height="40">\n'
        f'\t\t\t<staticText><reportElement x="0" y="0" width="{CW}" height="30"/>'
        '<textElement textAlignment="Center"><font size="18" isBold="true"/></textElement>'
        f'<text><![CDATA[{title}]]></text></staticText>\n'
        '\t</band></title>\n'
        f'\t<detail><band height="{detail_h}">\n' + "\n".join(rows) + "\n\t</band></detail>\n"
        '</jasperReport>\n')


def menu(rep, jrxml_text):
    """The JasperReportsMenu element carrying the .jrxml inline (config-as-code)."""
    return {"className": "org.joget.plugin.enterprise.JasperReportsMenu",
            "properties": {
                "id": menu_id(rep["id"]),
                "customId": rep["id"],
                "label": rep["name"],
                "datasource": "",                    # rule 3: "" = the Joget profile DB
                "output": "html",
                "export": rep.get("export", "pdf"),  # rule 4: ';'-joined string, e.g. "pdf;xls"
                # rule 5: request params mapped in; rule 2: a JSON array, never a string
                "parameters": [{"name": p["name"], "value": f'#requestParam.{p.get("request_param", p["name"])}#'}
                               for p in rep.get("params", [])],
                "jrxml": jrxml_text}}


def main():
    spec_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    count = 0
    for fn in sorted(os.listdir(spec_dir)):
        if not fn.endswith(".spec.yml"):
            continue
        spec = yaml.safe_load(open(os.path.join(spec_dir, fn)))
        if "report" not in spec:
            continue
        rep = spec["report"]
        jx = jrxml(rep)
        with open(os.path.join(out_dir, rep["id"] + ".jrxml"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(jx)
        json.dump(menu(rep, jx), open(os.path.join(out_dir, rep["id"] + ".json"), "w"), indent=2)
        count += 1
        print(f"  + {rep['id']}.jrxml + {rep['id']}.json ({len(rep['fields'])} fields, "
              f"{len(rep.get('params', []))} params)")
    print(f"{count} report(s) generated -> {out_dir}")


if __name__ == "__main__":
    main()
