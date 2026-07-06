#!/usr/bin/env python3
"""Project-neutral reference dashboard generator (charts → enterprise SqlChartMenu).

The native Joget way to chart (DX 8/9 Enterprise): server-side SQL runs and Apache ECharts
renders it — `org.joget.plugin.enterprise.SqlChartMenu` — NOT Chart.js in an HtmlPage (which
the sanitiser strips). This reference generator emits one SqlChartMenu per chart from a neutral
`dashboard:` spec; a category of these menus is the fully-native composition (DashboardMenu
portlet encoding is deliberately out of scope — see the joget-dashboard-gen skill).

Scope: chart tiles. KPI RAG cards (a CustomHTML/API-fed surface) are out of scope here.

Spec (YAML):
    dashboard:
      id: dashOffice
      name: "Review Pipeline"
      charts:
        - id: apps_by_status         # SqlChartMenu customId
          label: "Applications by status"
          chartType: bar             # bar | line | pie | area
          keyName: status            # category / x column alias
          value: n                   # numeric / y column alias
          sql: "SELECT c_status AS status, COUNT(*) AS n FROM app_fd_permit_application GROUP BY c_status"

Usage:  gen_dashboards.py <spec.yml> <out_dir>
"""
import json
import os
import sys
import uuid

import yaml

NS = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
SEED = "joget-dashboard:"   # neutral namespace


def uid(name):
    return str(uuid.uuid5(NS, SEED + name))


def sqlchart_menu(c):
    # keyName = category/x alias, value = numeric/y alias. datasource "default" runs the raw SQL
    # on the profile DB; library "echart" is Apache ECharts. chartWidth/Height are raw CSS.
    return {"className": "org.joget.plugin.enterprise.SqlChartMenu", "properties": {
        "id": uid("chart:" + c["id"]), "customId": c["id"],
        "label": c["label"], "title": c.get("title", c["label"]),
        "chartType": c["chartType"], "library": "echart",
        "keyName": c["keyName"], "value": c["value"], "chartUseAllDataRows": "true",
        "showValue": c.get("showValue", "true"), "showLegend": c.get("showLegend", ""),
        "horizontal": c.get("horizontal", ""),
        "chartWidth": c.get("chartWidth", "100%"), "chartHeight": c.get("chartHeight", "360px"),
        "showTable": "", "showExportLinks": "", "iconIncluded": False,
        "datasource": "default", "query": c["sql"]}}


def build_dashboard(spec):
    d = spec["dashboard"]
    return {"id": d["id"], "name": d.get("name", d["id"]),
            "menus": [sqlchart_menu(c) for c in d.get("charts", [])]}


def main():
    spec_path, out_dir = sys.argv[1], sys.argv[2]
    spec = yaml.safe_load(open(spec_path))
    dash = build_dashboard(spec)
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, dash["id"] + ".json")
    with open(out, "w") as f:
        json.dump(dash, f, indent=2)
    print(f"dashboard {dash['id']}: {len(dash['menus'])} chart(s) -> {out}")


if __name__ == "__main__":
    main()
