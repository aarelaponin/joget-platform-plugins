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


def test_selection_ui_only_when_consuming_action_exists():
    # A001 / STA-01/05: the checkbox column is emitted ONLY when a selection-consuming
    # action exists. crud with delete on (default) keeps it; delete:false drops it; a
    # read-only datalist (no delete) has nothing to consume selection -> no dead column.
    spec = {"userview": {"id": "uvSel", "name": "Sel", "categories": [
        {"id": "c", "label": "C", "menus": [
            {"type": "crud", "label": "Del on", "formId": "frmA", "datalistId": "la"},
            {"type": "crud", "label": "Del off", "formId": "frmB", "datalistId": "lb", "delete": False},
            {"type": "datalist", "label": "Read", "datalistId": "lc"}]}]}}
    del_on, del_off, dlist = g.build_userview(spec)["categories"][0]["menus"]
    assert (del_on["properties"]["selectionType"], del_on["properties"]["checkboxPosition"]) == ("multiple", "left")
    assert (del_off["properties"]["selectionType"], del_off["properties"]["checkboxPosition"]) == ("", "")
    assert (dlist["properties"]["selectionType"], dlist["properties"]["checkboxPosition"]) == ("", "")


def test_a_hidden_category_is_written_hidden():
    # 0.11.0 (METHOD-2026-09-25-11, item 1): a category the spec marks `hide` is written with the
    # platform's own `hide` set to "yes", so its menus stand out of the menu and open only by
    # address - a form that belongs to a record is reached from the record, never from the menu.
    # Up to 0.10.0 every category's `hide` was written empty whatever the spec said, and the
    # kit's trigger forms (and any model category marked `hidden: true`) were shown (T112).
    spec = copy.deepcopy(SPEC)
    spec["userview"]["categories"][1]["hide"] = True
    ops, ref = g.build_userview(spec)["categories"]
    assert ref["properties"]["hide"] == "yes"
    assert ops["properties"]["hide"] == ""          # a category the spec does not hide is shown


def test_a_form_menu_says_its_act_and_a_reading_form_offers_no_save():
    # 0.11.0 (METHOD-2026-09-26-02, point 3). The button of a move's form says the move's words
    # (FormMenu submitButtonLabel), and a form placed only for reading offers no Save: a form menu
    # read-only as the platform reads it ("Yes"; FormMenu, Joget DX 9.0.7, compares
    # "Yes".equalsIgnoreCase), a crud menu whose record is read only in edit mode
    # ("yes".equalsIgnoreCase(edit-readonly), CrudMenu 9.0.7), with no Edit button.
    spec = {"userview": {"id": "uvRead", "name": "Read", "categories": [
        {"id": "c", "label": "C", "menus": [
            {"type": "form", "label": "Close the case", "formId": "actCaseClose",
             "submit_label": "Close the case"},
            {"type": "form", "label": "The closed case", "formId": "frmClosed", "readonly": True},
            {"type": "crud", "label": "My cases", "formId": "frmWork", "datalistId": "lsMine",
             "add": False, "delete": False, "edit_readonly": True},
            {"type": "form", "label": "Plain", "formId": "frmPlain"},
            {"type": "crud", "label": "Plain cases", "formId": "frmCase",
             "datalistId": "list_frmCase"}]}]}}
    act, closed, work, plain, plain_crud = g.build_userview(spec)["categories"][0]["menus"]
    assert act["properties"]["submitButtonLabel"] == "Close the case"
    assert act["properties"]["readonly"] == ""
    assert closed["properties"]["readonly"] == "Yes"
    w = work["properties"]
    assert (w["edit-readonly"], w["edit-readonlyLabel"], w["edit-showEditButton"]) == \
        ("yes", "true", "")
    # absent the keys, the emission is exactly the one before them
    assert "submitButtonLabel" not in plain["properties"]
    assert not any(k.startswith("edit-read") or k == "edit-showEditButton"
                   for k in plain_crud["properties"])
    base = copy.deepcopy(SPEC)
    assert g.build_userview(base) == g.build_userview(SPEC)


def test_a_list_with_an_act_on_chosen_rows_draws_its_boxes():
    # METHOD-2026-10-09-05, item 7 (the engagement's LS-98). A list that declares an act on the
    # rows a person chooses (the model's `chosen_actions`) needs the box on each row to choose
    # them. The menu overrides the list's own checkboxPosition, and wrote it empty wherever the
    # menu offered no Delete, so the act's button stood over a list with no box to choose a row
    # by. The spec's `chosen_actions: true` draws the column on a crud menu without Delete and on
    # a list menu; absent the key, the emission is as before.
    spec = {"userview": {"id": "uvChosen", "name": "Chosen", "categories": [
        {"id": "c", "label": "C", "menus": [
            {"type": "crud", "label": "My cases", "formId": "frmWork", "datalistId": "lsMine",
             "add": False, "delete": False, "chosen_actions": True},
            {"type": "datalist", "label": "Queue", "datalistId": "lsQueue",
             "chosen_actions": True},
            {"type": "crud", "label": "No act", "formId": "frmB", "datalistId": "lb",
             "delete": False}]}]}}
    mine, queue, plain = g.build_userview(spec)["categories"][0]["menus"]
    for m in (mine, queue):
        assert (m["properties"]["checkboxPosition"], m["properties"]["selectionType"]) == \
            ("left", "multiple")
    assert mine["properties"]["list-showDeleteButton"] == ""      # still no Delete
    assert (plain["properties"]["checkboxPosition"], plain["properties"]["selectionType"]) == \
        ("", "")
