"""gen_datalists: the acts on the rows a person chooses (METHOD-2026-09-28-07, the kit's gap G1).

A spec's `actions` are written among the datalist's own actions, where the platform shows each as
a button over the list, with a box on each row to choose it: the same HyperlinkDataListAction as a
row's, which on the button writes each chosen row's value of `hrefColumn`, joined by ';', into
`hrefParam` (wflow-core 9.0.7, HyperlinkDataListAction.executeAction). A spec that names none emits
exactly what it emitted before: an empty `actions`.
"""
import json
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_datalists  # noqa: E402

UV = "#request.contextPath#/web/userview/exApp/uvEx/_/"


def _emit(tmp, actions=None):
    spec = {"datalist": {"id": "dlItems", "name": "Items",
                         "binder": {"type": "form", "formDefId": "frmItem"},
                         "columns": [{"id": "item_no", "label": "No."}],
                         "rowActions": [{"label": "Open", "href": UV + "dlItems_crud?_mode=edit",
                                         "hrefColumn": "id", "hrefParam": "id"}]}}
    if actions is not None:
        spec["datalist"]["actions"] = actions
    src = tmp / "specs"
    src.mkdir()
    (src / "DL-dlItems.spec.yml").write_text(yaml.safe_dump(spec), encoding="utf-8")
    out = tmp / "out"
    argv = sys.argv
    sys.argv = ["gen_datalists.py", str(src), str(out)]
    try:
        gen_datalists.main()
    finally:
        sys.argv = argv
    return json.loads((out / "dlItems.json").read_text(encoding="utf-8"))


def test_an_act_on_the_chosen_rows_is_among_the_lists_own_actions(tmp_path):
    dl = _emit(tmp_path, actions=[{"label": "Note the chosen items", "href": UV + "frmNoteMany",
                                   "hrefColumn": "item_no", "hrefParam": "items"}])
    assert dl["actions"] == [{
        "className": "org.joget.apps.datalist.lib.HyperlinkDataListAction", "id": "action_0",
        "label": "Note the chosen items",
        "properties": {"label": "Note the chosen items", "href": UV + "frmNoteMany",
                       "hrefColumn": "item_no", "hrefParam": "items", "target": "",
                       "confirmation": ""}}], dl["actions"]
    assert [ra["id"] for ra in dl["rowActions"]] == ["rowAction_0"]      # the row's act stands
    assert dl["checkboxPosition"] == "left"                                # a box on each row


def test_a_spec_that_names_no_act_on_chosen_rows_emits_none(tmp_path):
    assert _emit(tmp_path)["actions"] == []
