"""gen_datalists 0.15.0: a column read through a reference, worded by its attribute's list (METHOD-2026-11-05-89, R51;
MT-DM's K1 of 11 November 2026).

The kit projects a column of another record than the row's (`via`) as an `optionsValue` formatter over the referred
record's form: its key the option's value, the attribute itself its words, so the attribute's stored code is drawn.
Where that attribute is bound to a list, the kit's formatter carries `words`: a maintained list's form (`formDefId`,
`idColumn`, `labelColumn`), or a fixed list's rows (`options`). The generator then writes the library's
WordedOptionsFormatter (joget-list-formatters 1.1.0) with its second step: `wordsBinder` over the list's form, or none,
and `words`, the list's rows, or none. Every formatter without the key is written exactly as 0.14.0 wrote it, held here
to the bytes written out below, never read from the generator's own constants.

  V1 a via column over a maintained list: the worded formatter, the first binder over the referred record's form, no
     grid, `split` true (the platform's formatter splits), `unlisted` as_stored, the second binder over the list's form
     as the platform's formatter takes a binder, no words grid
  V2 a via column over a fixed list: the second binder names no class, the grid holds the list's rows in order, as text
  V3 the properties are written in the order of the formatter's own property page
  V4 `words` that is not a mapping, or names neither a form nor rows, is refused
  V5 `words` beside `unlisted` and `split` keeps both
  G1 a via column without the key: byte for byte as 0.14.0 wrote it
  G2 the other formatters a list column takes, without the key, are written as 0.14.0 wrote them
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


#: a via column's formatter as the kit projects it: the referred record's form, its key, the attribute itself
VIA = {"type": "optionsValue", "formDefId": "recCaseScopeLine", "idColumn": "id", "labelColumn": "how_it_left"}
WORDED = "com.fiscaladmin.joget.lists.WordedOptionsFormatter"


def _list(formatter):
    return {"id": "lsWriteOffViewLines", "name": "The write-off's lines",
            "binder": {"type": "form", "formDefId": "recWriteOffLine"},
            "columns": [{"id": "case_scope_line", "label": "Left the case", "formatter": formatter}]}


def _format(tmp, formatter) -> dict:
    return _emit(tmp, _list(formatter))["columns"][0]["format"]


def _binder(form, id_column, label_column):
    return {"className": "org.joget.apps.form.lib.FormOptionsBinder",
            "properties": {"formDefId": form, "idColumn": id_column, "labelColumn": label_column,
                           "addEmptyOption": "true", "groupingColumn": "", "useAjax": "", "extraCondition": "",
                           "cacheInterval": "", "emptyLabel": ""}}


#: the via column's formatter as 0.14.0 wrote it, key order included, written out by hand
BEFORE = (
    '{"className": "org.joget.plugin.enterprise.OptionsValueFormatter", "properties": {"optionsBinder": '
    '{"className": "org.joget.apps.form.lib.FormOptionsBinder", "properties": {"formDefId": "recCaseScopeLine", '
    '"idColumn": "id", "labelColumn": "how_it_left", "addEmptyOption": "true", "groupingColumn": "", '
    '"useAjax": "", "extraCondition": "", "cacheInterval": "", "emptyLabel": ""}}, "options": []}}')


def test_a_via_column_over_a_maintained_list_takes_the_second_binder(tmp_path):
    """V1."""
    got = _format(tmp_path, dict(VIA, words={"formDefId": "md_scope_line_exit", "idColumn": "code",
                                             "labelColumn": "name"}))
    assert got == {"className": WORDED,
                   "properties": {"optionsBinder": _binder("recCaseScopeLine", "id", "how_it_left"),
                                  "options": [],
                                  "split": "true", "unlisted": "as_stored",
                                  "wordsBinder": _binder("md_scope_line_exit", "code", "name"),
                                  "words": []}}, got


def test_a_via_column_over_a_fixed_list_takes_the_lists_rows(tmp_path):
    """V2: the rows in the list's order, a number written as its text."""
    got = _format(tmp_path, dict(VIA, words={"options": [{"value": "written_off", "label": "Written off"},
                                                         {"value": "paid", "label": "Paid"},
                                                         {"value": 7, "label": 7}]}))
    assert got == {"className": WORDED,
                   "properties": {"optionsBinder": _binder("recCaseScopeLine", "id", "how_it_left"),
                                  "options": [],
                                  "split": "true", "unlisted": "as_stored",
                                  "wordsBinder": {"className": "", "properties": {}},
                                  "words": [{"value": "written_off", "label": "Written off"},
                                            {"value": "paid", "label": "Paid"},
                                            {"value": "7", "label": "7"}]}}, got


def test_the_properties_are_in_the_order_of_the_property_page(tmp_path):
    """V3: optionsBinder, options, wordsBinder, words come in the page's order of their kind; the page places the two new
    ones before split, the writer after unlisted - the platform reads a formatter's properties by name, not order."""
    got = _format(tmp_path, dict(VIA, words={"options": [{"value": "a", "label": "A"}]}))
    assert list(got["properties"]) == ["optionsBinder", "options", "split", "unlisted", "wordsBinder", "words"], got


def test_words_that_name_neither_a_form_nor_rows_are_refused(tmp_path):
    """V4."""
    with pytest.raises(ValueError, match="words"):
        _format(tmp_path, dict(VIA, words={}))
    sub = tmp_path / "s"
    sub.mkdir()
    with pytest.raises(ValueError, match="words"):
        _format(sub, dict(VIA, words=["written_off"]))


def test_words_beside_unlisted_and_split_keep_both(tmp_path):
    """V5: a formatter of 0.13.0's keys with the second step: `split` false is written "false"."""
    got = _format(tmp_path, dict(VIA, unlisted="as_stored", split=False,
                                 words={"options": [{"value": "a", "label": "A"}]}))
    assert got["properties"]["split"] == "false" and got["properties"]["unlisted"] == "as_stored", got
    assert got["properties"]["words"] == [{"value": "a", "label": "A"}], got


def test_a_via_column_without_the_key_is_written_as_before(tmp_path):
    """G1: byte for byte as 0.14.0 wrote it."""
    assert json.dumps(_format(tmp_path, dict(VIA))) == BEFORE


def test_the_other_formatters_without_the_key_are_written_as_before(tmp_path):
    """G2: a fixed list's formatter and a keyed formatter of 0.13.0, without `words`, as 0.14.0 wrote them."""
    got = _format(tmp_path, {"type": "optionsValue", "options": [{"value": "a", "label": "A"}]})
    assert json.dumps(got) == (
        '{"className": "org.joget.plugin.enterprise.OptionsValueFormatter", "properties": {"options": '
        '[{"value": "a", "label": "A"}], "optionsBinder": {"className": "", "properties": {}}}}'), got
    sub = tmp_path / "k"
    sub.mkdir()
    got = _format(sub, dict(VIA, unlisted="as_stored"))
    assert list(got["properties"]) == ["optionsBinder", "options", "split", "unlisted"], got
