"""Standalone test for the project-neutral reference gen_dashboards."""
import copy
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_dashboards as g

SPEC = {"dashboard": {"id": "dashOffice", "name": "Review Pipeline", "charts": [
    {"id": "apps_by_status", "label": "Applications by status", "chartType": "bar",
     "keyName": "status", "value": "n",
     "sql": "SELECT c_status AS status, COUNT(*) AS n FROM app_fd_x GROUP BY c_status"}]}}


def test_sqlchartmenu_shape():
    d = g.build_dashboard(SPEC)
    assert d["id"] == "dashOffice" and d["name"] == "Review Pipeline"
    m = d["menus"][0]
    assert m["className"] == "org.joget.plugin.enterprise.SqlChartMenu"
    p = m["properties"]
    assert p["customId"] == "apps_by_status" and p["chartType"] == "bar"
    assert p["keyName"] == "status" and p["value"] == "n"
    assert p["library"] == "echart" and p["chartUseAllDataRows"] == "true"


def test_server_side_sql_not_scraped():
    p = g.build_dashboard(SPEC)["menus"][0]["properties"]
    assert p["datasource"] == "default"
    assert p["query"] == SPEC["dashboard"]["charts"][0]["sql"]


def test_deterministic():
    assert g.build_dashboard(SPEC) == g.build_dashboard(copy.deepcopy(SPEC))


def test_zero_project_residue():
    blob = json.dumps(g.build_dashboard(SPEC)).lower()
    for token in ("cmbb", "dmbb", "mtca", "rpt-fr", "chart.js", "htmlmenu"):
        assert token not in blob, f"project residue '{token}' leaked into neutral output"
