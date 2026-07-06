"""Standalone test for the project-neutral reference gen_userview."""
import copy
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_userview as g

SPEC = {"userview": {"id": "uvExample", "name": "Example", "footer": "Example",
        "categories": [
            {"id": "cat_ops", "label": "Operations", "role": "group_officer", "menus": [
                {"type": "crud", "label": "Cases", "formId": "frmCase", "datalistId": "list_frmCase"},
                {"type": "datalist", "label": "Queue", "datalistId": "list_queue"}]},
            {"id": "cat_ref", "label": "Reference", "menus": [
                {"type": "form", "label": "About", "formId": "frmAbout"}]}]}}


def test_envelope_and_classes():
    uv = g.build_userview(SPEC)
    assert uv["className"] == "org.joget.apps.userview.model.Userview"
    assert uv["properties"]["id"] == "uvExample"
    assert [c["properties"]["label"] for c in uv["categories"]] == ["Operations", "Reference"]
    ops, ref = uv["categories"]
    assert [m["className"] for m in ops["menus"]] == [
        "org.joget.plugin.enterprise.CrudMenu", "org.joget.apps.userview.lib.DataListMenu"]
    assert ref["menus"][0]["className"] == "org.joget.apps.userview.lib.FormMenu"


def test_categories_emitted_as_authored_no_bucket_collapse():
    uv = g.build_userview(SPEC)
    assert len(uv["categories"]) == 2   # one out per input category, not collapsed into buckets


def test_group_permission_from_role():
    uv = g.build_userview(SPEC)
    ops, ref = uv["categories"]
    assert ops["properties"]["permission"] == {
        "className": "org.joget.apps.userview.lib.GroupPermission",
        "properties": {"allowedGroupIds": "group_officer"}}
    assert ref["properties"]["permission"] == {"className": "", "properties": {}}   # open


def test_theme_is_clean_no_project_js():
    theme = g.build_userview(SPEC)["setting"]["properties"]["theme"]
    assert theme["className"] == "org.joget.apps.userview.lib.Dx8TrimedaTheme"
    assert theme["properties"]["disablePwa"] == "true"
    assert theme["properties"]["js"] == "" and theme["properties"]["css"] == ""


def test_deterministic():
    assert g.build_userview(SPEC) == g.build_userview(copy.deepcopy(SPEC))


def test_zero_project_residue():
    blob = json.dumps(g.build_userview(SPEC)).lower()
    for token in ("cmbb", "dmbb", "mtca", "dm_", "farmer", "lesotho"):
        assert token not in blob, f"project residue '{token}' leaked into neutral output"


def test_unknown_menu_type_is_error():
    bad = copy.deepcopy(SPEC)
    bad["userview"]["categories"][0]["menus"].append({"type": "sqlchart", "label": "x"})
    try:
        g.build_userview(bad)
        assert False, "expected ValueError"
    except ValueError as e:
        assert "unknown menu type" in str(e)
