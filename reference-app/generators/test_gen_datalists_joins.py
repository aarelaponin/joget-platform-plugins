"""gen_datalists: AdvancedFormRowDataListBinder joins (registry 0.9.11).

The deployed farmersPortal registers carry binder joins - child TABLES
joined on `<table>.<fk>` = parent `id`. The L2 spec spells them
`binder.joins: [{table, fk}]`; the emission is the measured deployed shape.
A binder without joins emits byte-identical properties to 0.9.10.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_datalists  # noqa: E402


def _spec(joins=None):
    binder = {"type": "form", "formDefId": "frmParent"}
    if joins:
        binder["joins"] = joins
    return {"datalist": {"id": "list_parent", "name": "Register",
                         "binder": binder,
                         "columns": [{"id": "child_tbl.first_name",
                                      "label": "First"}]}}


def _emit(tmp, spec):
    import yaml
    d = tmp / "specs"
    d.mkdir()
    (d / "DL-x.spec.yml").write_text(yaml.safe_dump(spec))
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return json.loads((out / "list_parent.json").read_text())


def test_joins_are_emitted_in_the_deployed_shape(tmp_path):
    dl = _emit(tmp_path, _spec(joins=[{"table": "child_tbl", "fk": "parent_id"}]))
    props = dl["binder"]["properties"]
    assert props["joins"] == [{"joinFieldId": "id", "tableName": "child_tbl",
                               "fieldId": "child_tbl.parent_id"}]


def test_binder_without_joins_is_byte_identical_to_0_9_10(tmp_path):
    dl = _emit(tmp_path, _spec())
    assert dl["binder"] == {
        "className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
        "properties": {"formDefId": "frmParent", "extraCondition": ""}}
