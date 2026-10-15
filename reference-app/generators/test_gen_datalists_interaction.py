"""gen_datalists: the list patterns of the interaction design step (registry 0.11.0).

METHOD-2026-09-25-11, items 2 and 3. A select filter over a list of values reads the list at
run time through the platform's own options binder, a filter over a divided list is chosen in
two steps (category, then value), and a date column is shown in the day-first pattern it asks
for while the stored value is read in the format it is stored in. A spec that uses none of this
emits exactly the 0.10.0 filter (asserted literally below).
"""
import json
import pathlib
import sys

import pytest
import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_datalists  # noqa: E402

# The options binder a list of values is read through: the enterprise JDBC options binder, whose
# first column is the value, the second the label and the third the grouping, in the query's order.
KIND = {"className": "org.joget.plugin.enterprise.JdbcOptionsBinder",
        "properties": {"jdbcDatasource": "default", "useAjax": "", "addEmpty": "", "emptyLabel": "",
                       "sql": "SELECT c_code, c_name FROM app_fd_ex_lv_kind "
                              "WHERE c_standing = 'in_force' "
                              "ORDER BY CAST(NULLIF(c_sort_order, '') AS NUMERIC)"}}
YEAR = {"className": "org.joget.plugin.enterprise.JdbcOptionsBinder",
        "properties": {"jdbcDatasource": "default", "useAjax": "", "addEmpty": "", "emptyLabel": "",
                       "sql": "SELECT c_code, c_name FROM app_fd_ex_lv_year "
                              "WHERE c_standing = 'in_force' "
                              "ORDER BY CAST(NULLIF(c_sort_order, '') AS NUMERIC)"}}
PERIOD = {"className": "org.joget.plugin.enterprise.JdbcOptionsBinder",
          "properties": {"jdbcDatasource": "default", "useAjax": "", "addEmpty": "", "emptyLabel": "",
                         "sql": "SELECT c_code, c_name, c_group_code FROM app_fd_ex_lv_period "
                                "WHERE c_standing = 'in_force' "
                                "ORDER BY CAST(NULLIF(c_sort_order, '') AS NUMERIC)"}}


def _emit(tmp, filters=None, columns=None):
    spec = {"datalist": {"id": "dlCases", "name": "Cases",
                         "binder": {"type": "jdbc", "sql": "SELECT id, kind, period, period_year, "
                                                           "opened_on, closed_at FROM v_cases"},
                         "columns": columns or [{"id": "kind", "label": "Kind"}],
                         "filters": filters or []}}
    d = tmp / "specs"
    d.mkdir()
    (d / "DL-dlCases.spec.yml").write_text(yaml.safe_dump(spec))
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return json.loads((out / "dlCases.json").read_text())


def test_a_select_filter_over_a_list_reads_the_list_at_run_time(tmp_path):
    # item 2: the filter carries the platform's options binder, so it offers the values in force
    # in the list's order as they stand when the list is shown, not as they were at the build.
    dl = _emit(tmp_path, filters=[{"id": "kind", "label": "Kind", "type": "select",
                                   "options": [{"value": "a", "label": "A"}],
                                   "optionsBinder": KIND}])
    t = dl["filters"][0]["type"]
    assert t["className"] == "org.joget.plugin.enterprise.SelectBoxDataListFilterType"
    assert t["properties"]["optionsBinder"] == KIND
    assert "controlField" not in t["properties"]      # one step: nothing controls it


def test_a_filter_over_a_divided_list_is_chosen_in_two_steps(tmp_path):
    # item 2: the category first, then the value; the value's filter names the category's filter
    # as its control, and the platform offers only the values whose grouping holds the category
    # chosen (the third column of the value's binder).
    dl = _emit(tmp_path, filters=[
        {"id": "period_year", "label": "Tax year", "type": "select", "optionsBinder": YEAR},
        {"id": "period", "label": "Tax period", "type": "select", "optionsBinder": PERIOD,
         "controlField": "period_year"}])
    year, period = dl["filters"]
    assert [year["name"], period["name"]] == ["period_year", "period"]
    assert year["type"]["properties"]["optionsBinder"] == YEAR
    assert "controlField" not in year["type"]["properties"]
    assert period["type"]["properties"]["optionsBinder"] == PERIOD
    assert period["type"]["properties"]["controlField"] == "period_year"


@pytest.mark.parametrize("filters", [
    # the control names no filter of the list
    [{"id": "period", "type": "select", "optionsBinder": PERIOD, "controlField": "period_year"}],
    # the control stands after the filter it controls
    [{"id": "period", "type": "select", "optionsBinder": PERIOD, "controlField": "period_year"},
     {"id": "period_year", "type": "select", "optionsBinder": YEAR}],
    # the control is not a select filter
    [{"id": "period_year", "type": "text"},
     {"id": "period", "type": "select", "optionsBinder": PERIOD, "controlField": "period_year"}],
    # the control's name is not a plain name: the platform's script finds the control by an
    # unquoted name and a dot breaks it, so the value's drop-down would offer nothing
    [{"id": "yr.code", "type": "select", "optionsBinder": YEAR},
     {"id": "period", "type": "select", "optionsBinder": PERIOD, "controlField": "yr.code"}],
])
def test_a_second_step_whose_first_step_cannot_be_found_is_refused(tmp_path, filters):
    with pytest.raises(ValueError, match="controlField"):
        _emit(tmp_path, filters=filters)


def test_a_select_filter_without_a_binder_is_byte_identical_to_0_10_0(tmp_path):
    dl = _emit(tmp_path, filters=[{"id": "kind", "label": "Kind", "type": "select",
                                   "options": [{"value": "a", "label": "A"}]}])
    assert dl["filters"][0] == {
        "name": "kind", "id": "filter_0", "label": "Kind",
        "type": {"className": "org.joget.plugin.enterprise.SelectBoxDataListFilterType",
                 "properties": {"label": "", "defaultValue": "", "multiple": "", "size": "",
                                "options": [{"value": "", "label": "- any -"},
                                            {"value": "a", "label": "A"}]}},
        "filterParamName": "f_kind"}


def test_a_date_column_is_shown_day_first_and_read_as_it_is_stored(tmp_path):
    # item 3: the day-first pattern is the DateFormatter's displayFormat; its dataFormat is the
    # format the value is stored in - a date control stores yyyy-MM-dd (gen_forms DatePicker
    # dataFormat), a moment the transition guard sets is stored yyyy-MM-dd HH:mm:ss - unless
    # the spec names the stored format itself.
    dl = _emit(tmp_path, columns=[
        {"id": "opened_on", "label": "Opened", "formatter": {"type": "date", "format": "dd/MM/yyyy"}},
        {"id": "closed_at", "label": "Closed",
         "formatter": {"type": "date", "format": "dd/MM/yyyy HH:mm:ss"}},
        {"id": "period", "label": "Period",
         "formatter": {"type": "date", "format": "dd.MM.yyyy", "dataFormat": "yyyyMMdd"}}])
    fmts = {c["name"]: c["format"] for c in dl["columns"]}
    for name in fmts:
        assert fmts[name]["className"] == "org.joget.plugin.enterprise.DateFormatter"
    assert fmts["opened_on"]["properties"] == {"dataFormat": "yyyy-MM-dd", "displayFormat": "dd/MM/yyyy"}
    assert fmts["closed_at"]["properties"] == {"dataFormat": "yyyy-MM-dd HH:mm:ss",
                                               "displayFormat": "dd/MM/yyyy HH:mm:ss"}
    assert fmts["period"]["properties"] == {"dataFormat": "yyyyMMdd", "displayFormat": "dd.MM.yyyy"}


def test_a_column_over_a_fixed_list_shows_its_label_from_the_values_given(tmp_path):
    # METHOD-2026-09-26-02, point 19 (a): a fixed list has no table, so its column's labels are
    # written into the platform's OptionsValueFormatter as its `options` grid (value, label), and
    # no binder is named; the formatter reads the grid first (jw-enterprise-plugins 9.0.7,
    # OptionsValueFormatter.getOptionMap). A formatter naming a form is emitted as before.
    dl = _emit(tmp_path, columns=[
        {"id": "kind", "label": "Kind", "formatter": {
            "type": "optionsValue",
            "options": [{"value": "a", "label": "Alpha"}, {"value": "b", "label": "Beta"}]}},
        {"id": "period", "label": "Period", "formatter": {
            "type": "optionsValue", "formDefId": "lovPeriod", "idColumn": "code",
            "labelColumn": "name"}}])
    kind, period = (c["format"] for c in dl["columns"])
    assert kind == {"className": "org.joget.plugin.enterprise.OptionsValueFormatter",
                    "properties": {"options": [{"value": "a", "label": "Alpha"},
                                               {"value": "b", "label": "Beta"}],
                                   "optionsBinder": {"className": "", "properties": {}}}}
    assert period["properties"]["optionsBinder"]["className"] == \
        "org.joget.apps.form.lib.FormOptionsBinder"
    assert period["properties"]["optionsBinder"]["properties"]["formDefId"] == "lovPeriod"
    assert period["properties"]["options"] == []
