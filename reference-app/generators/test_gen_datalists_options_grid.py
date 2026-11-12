"""gen_datalists 0.13.0: the library's WordedOptionsFormatter, for a column that asks for it (METHOD-2026-11-05-81, R47
version 2; MT-DM's request of 9 November 2026).

The platform's OptionsValueFormatter fills its map from its `options` grid and then, whenever its binder answers,
replaces the map with the binder's rows (jw-enterprise-plugins 9.0.7, javap -c of getOptionMap), so a grid written
beside a form binder is never drawn. A column of the spec whose formatter is `optionsValue` and carries
`unlisted: as_stored` is therefore written as com.fiscaladmin.joget.lists.WordedOptionsFormatter (plugin
joget-list-formatters 1.0.0), which keeps the binder's rows and adds the grid's values where no row holds them. Every
formatter without the key is written exactly as 0.12.0 wrote it, held here to the bytes written out below, never read
from the generator's own constants.

  W1 a keyed formatter over a form: the worded formatter, binder as the platform's formatter takes it, the grid in the
     spec's order, `split` true where the spec says nothing, `unlisted` as_stored
  W2 `split: false` is written "false"
  W3 the properties are written in the order of the formatter's own property page
  W4 a keyed formatter over no form names no binder, as a fixed list's does
  W5 a value `unlisted` does not take, and a `split` that is not true or false, are refused
  W6 a number is written as its text
  G1 a formatter without the key: byte for byte as 0.12.0 wrote it - and `options` beside a form binder without the
     key is NOT written (version 1 wrote that grid beside the platform's formatter, where it is never drawn)
  G2 a fixed list's formatter (options, no form) is written as 0.11.0 wrote it
"""
import json
import pathlib
import sys

import pytest
import yaml

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gen_datalists  # noqa: E402


def _emit(tmp, datalist):
    d = tmp / "specs"
    d.mkdir()
    (d / "DL-x.spec.yml").write_text(yaml.safe_dump({"datalist": datalist}, sort_keys=False, allow_unicode=True),
                                     encoding="utf-8", newline="\n")
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return json.loads((out / (datalist["id"] + ".json")).read_text(encoding="utf-8"))


FORM = {"type": "optionsValue", "formDefId": "recUser", "idColumn": "directory_identifier", "labelColumn": "name"}
WORDED = "com.fiscaladmin.joget.lists.WordedOptionsFormatter"


def _list(formatter):
    return {"id": "dlHistory", "name": "History",
            "binder": {"type": "jdbc", "sql": "SELECT id, c_made_by AS made_by FROM app_fd_x"},
            "columns": [{"id": "made_by", "label": "Who acted", "formatter": formatter}]}


def _format(tmp, formatter) -> dict:
    return _emit(tmp, _list(formatter))["columns"][0]["format"]


#: the formatter as 0.12.0 wrote it for FORM, key order included (the order of the platform's own property list)
BEFORE = (
    '{"className": "org.joget.plugin.enterprise.OptionsValueFormatter", "properties": {"optionsBinder": '
    '{"className": "org.joget.apps.form.lib.FormOptionsBinder", "properties": {"formDefId": "recUser", '
    '"idColumn": "directory_identifier", "labelColumn": "name", "addEmptyOption": "true", "groupingColumn": "", '
    '"useAjax": "", "extraCondition": "", "cacheInterval": "", "emptyLabel": ""}}, "options": []}}')

#: the binder as the platform's formatter takes it, for FORM
BINDER = {"className": "org.joget.apps.form.lib.FormOptionsBinder",
          "properties": {"formDefId": "recUser", "idColumn": "directory_identifier", "labelColumn": "name",
                         "addEmptyOption": "true", "groupingColumn": "", "useAjax": "", "extraCondition": "",
                         "cacheInterval": "", "emptyLabel": ""}}


def test_a_keyed_formatter_is_the_worded_formatter_with_binder_and_grid(tmp_path):
    """W1: `system` worded "the solution" is in the grid, after it the values in the order given; the binder is the
    one the platform's formatter takes; split is true where the spec says nothing; unlisted is as_stored."""
    got = _format(tmp_path, dict(FORM, unlisted="as_stored",
                                 options=[{"value": "system", "label": "the solution"},
                                          {"value": "import", "label": "the import"}]))
    assert got == {"className": WORDED,
                   "properties": {"optionsBinder": BINDER,
                                  "options": [{"value": "system", "label": "the solution"},
                                              {"value": "import", "label": "the import"}],
                                  "split": "true", "unlisted": "as_stored"}}, got


def test_split_false_is_written_false(tmp_path):
    """W2."""
    got = _format(tmp_path, dict(FORM, unlisted="as_stored", split=False))
    assert got["properties"]["split"] == "false" and got["properties"]["options"] == [], got
    sub = tmp_path / "t"
    sub.mkdir()
    got = _format(sub, dict(FORM, unlisted="as_stored", split=True))
    assert got["properties"]["split"] == "true", got


def test_the_properties_are_in_the_order_of_the_property_page(tmp_path):
    """W3: optionsBinder, options, split, unlisted - the order of wordedOptionsFormatter.json."""
    got = _format(tmp_path, dict(FORM, unlisted="as_stored"))
    assert list(got["properties"]) == ["optionsBinder", "options", "split", "unlisted"], got


def test_a_keyed_formatter_over_no_form_names_no_binder(tmp_path):
    """W4: as a fixed list's formatter names none; the grid holds the values."""
    got = _format(tmp_path, {"type": "optionsValue", "unlisted": "as_stored", "split": False,
                             "options": [{"value": "a", "label": "A"}]})
    assert got == {"className": WORDED,
                   "properties": {"optionsBinder": {"className": "", "properties": {}},
                                  "options": [{"value": "a", "label": "A"}],
                                  "split": "false", "unlisted": "as_stored"}}, got


def test_a_value_unlisted_does_not_take_and_a_split_that_is_not_a_boolean_are_refused(tmp_path):
    """W5."""
    with pytest.raises(ValueError, match="as_stored"):
        _format(tmp_path, dict(FORM, unlisted="blank"))
    sub = tmp_path / "s"
    sub.mkdir()
    with pytest.raises(ValueError, match="true or false"):
        _format(sub, dict(FORM, unlisted="as_stored", split="no"))


def test_a_number_is_written_as_its_text(tmp_path):
    """W6: a value or a label the spec gives as a number is written as text, as a fixed list's are."""
    got = _format(tmp_path, dict(FORM, unlisted="as_stored", options=[{"value": 0, "label": 0}]))
    assert got["properties"]["options"] == [{"value": "0", "label": "0"}], got


def test_a_formatter_without_the_key_is_written_as_before(tmp_path):
    """G1: no key, whatever else it carries, is the 0.12.0 formatter, byte for byte. `options` beside a form binder
    is not written: version 1 wrote that grid beside the platform's formatter, where the platform never draws it."""
    for n, fmt in enumerate((dict(FORM), dict(FORM, options=[]),
                             dict(FORM, options=[{"value": "system", "label": "the solution"}]))):
        sub = tmp_path / str(n)
        sub.mkdir()
        assert json.dumps(_format(sub, fmt)) == BEFORE, fmt


def test_a_fixed_lists_formatter_is_written_as_before(tmp_path):
    """G2: `options` with no form (a fixed list, 0.11.0) is the formatter that names no binder, as before."""
    got = _format(tmp_path, {"type": "optionsValue", "options": [{"value": "a", "label": "A"}]})
    assert got == {"className": "org.joget.plugin.enterprise.OptionsValueFormatter",
                   "properties": {"options": [{"value": "a", "label": "A"}],
                                  "optionsBinder": {"className": "", "properties": {}}}}, got
