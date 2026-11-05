"""The two constructs the kit wrote into the specs and the generators dropped (joget-spec-kit METHOD-2026-10-24-01,
the write-off pilot's first run: `rowsFrom` was in the request's form spec and 0 times in the package, and the
form menu's `after_save` was in the userview spec and nowhere in the userview). Each is shown on a spec that uses
it and, beside it, on one that does not, emitted exactly as before.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_forms as gf
import gen_userview as gu

GRID = {"id": "write_off_lines", "type": "grid", "label": "The lines", "formDefId": "frmWriteOffLine",
        "foreignKey": "write_off", "columns": [{"value": "case_scope_line", "label": "Line"},
                                               {"value": "amount_written_off", "label": "Amount"}]}
ROWS = {"table": "app_fd_mtcadm_case_scope_line", "parentColumn": "c_debt_case", "keyParam": "debt_case",
        "refField": "case_scope_line", "refColumn": "id",
        "map": [{"from": "c_tax_type", "to": "tax_type"}], "where": "c_how_it_left IS NULL"}


def test_a_grid_whose_lines_are_carried_loads_them_with_the_grid_rows_binder():
    el = gf.element(dict(GRID, rowsFrom=ROWS))
    lb = el["properties"]["loadBinder"]
    assert lb["className"] == "com.fiscaladmin.joget.formprefill.GridRowsPrefillBinder"
    assert lb["properties"] == {
        "formDefId": "frmWriteOffLine", "foreignKey": "write_off", "rowsTable": "mtcadm_case_scope_line",
        "parentField": "debt_case", "keyParam": "debt_case", "refField": "case_scope_line",
        "refColumn": "id", "where": "e.customProperties.how_it_left IS NULL",
        "map": [{"from": "tax_type", "to": "tax_type"}]}
    # the store binder stays the platform's own
    assert el["properties"]["storeBinder"]["className"] == "org.joget.plugin.enterprise.MultirowFormBinder"


def test_a_grid_without_carried_lines_is_emitted_exactly_as_before():
    el = gf.element(dict(GRID))
    assert json.dumps(el["properties"]["loadBinder"], sort_keys=False) == json.dumps(
        {"className": "org.joget.plugin.enterprise.MultirowFormBinder",
         "properties": {"formDefId": "frmWriteOffLine", "foreignKey": "write_off"}})
    assert list(el["properties"]) == ["id", "label", "formDefId", "pageSize", "enableSorting", "readonly",
                                      "deleteGridData", "validateMaxRow", "requestParams", "options",
                                      "loadBinder", "storeBinder", "validator"]


def test_a_carried_grid_naming_no_key_is_refused():
    bad = dict(ROWS)
    bad.pop("keyParam")
    try:
        gf.element(dict(GRID, rowsFrom=bad))
    except ValueError as e:
        assert "names no keyParam" in str(e)
    else:
        raise AssertionError("refused nothing")


MENU = {"type": "form", "label": "Request a write-off", "formId": "frmWriteOffRequest",
        "submit_label": "Submit for approval"}


def test_a_form_menu_with_a_page_after_the_save_redirects_to_it_with_the_record_saved():
    m = gu.form_menu(dict(MENU, after_save={"menu": "lsBatchNotPosted_crud", "param": "id", "carries": "saved"}))
    p = m["properties"]
    assert p["submitButtonLabel"] == "Submit for approval"
    assert (p["redirectUrlAfterComplete"], p["fieldPassover"], p["fieldPassoverMethod"], p["paramName"]) == \
        ("lsBatchNotPosted_crud?_mode=edit", "id", "param", "id")


def test_a_moves_form_redirects_to_the_record_it_moved_by_the_field_that_holds_it():
    m = gu.form_menu({"type": "form", "label": "Approve", "formId": "actWriteOffApprove", "submit_label": "Approve",
                      "after_save": {"menu": "lsWriteOffsToDecide_crud", "mode": "edit", "param": "id",
                                     "carries": "field", "field": "record_id"}})
    p = m["properties"]
    assert (p["redirectUrlAfterComplete"], p["fieldPassover"], p["paramName"]) == \
        ("lsWriteOffsToDecide_crud?_mode=edit", "record_id", "id")
    m = gu.form_menu({"type": "form", "label": "Read it", "formId": "frmA",
                      "after_save": {"menu": "frmCase", "param": "dcase", "carries": "opened", "from": "dcase"}})
    assert (m["properties"]["redirectUrlAfterComplete"], m["properties"]["fieldPassover"]) == ("frmCase", "dcase")


def test_a_form_menu_without_a_page_after_the_save_is_emitted_exactly_as_before():
    m = gu.form_menu(dict(MENU))
    assert list(m["properties"]) == ["id", "customId", "label", "formId", "readonly", "readonlyLabel",
                                     "messageShowAfterComplete", "iconIncluded", "submitButtonLabel"]
