"""gen_datalists: a list sorted as the platform reads it, a select filter of several choices, and an
amount column (registry 0.12.0; METHOD-2026-11-05-17; MT-DM's hand-off of 5 November 2026, items
21, 10 and 18).

Item 21. Joget 9.0.7 sorts a list descending only where its `order` is "1"
(wflow-core DataList.ORDER_DESCENDING_VALUE; DataList.getQueryParam), so a list the generator wrote
"DESC" was shown ascending. The generator now writes "1" descending and "" ascending.
Item 10. A select filter that carries `multiple` is written with the platform's "true", a choice of
several; without the key it is written exactly as in 0.11.0.
Item 18. A column of format `currency` is written with BeanShellFormatter, the platform 9.0.7
having no number formatter; without it the column is written exactly as in 0.11.0.

Each emission without its new key is held to the 0.11.0 bytes, written out here in full, never
read from the generator's own constants.
"""
import json
import os
import pathlib
import shutil
import subprocess
import sys

import pytest
import yaml

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gen_datalists  # noqa: E402


def _emit(tmp, datalist):
    d = tmp / "specs"
    d.mkdir()
    # the spec in its own key order, as the kit's projector writes it (an option's value, then
    # its label), so that the bytes the generator writes can be held to the 0.11.0 bytes
    (d / "DL-x.spec.yml").write_text(yaml.safe_dump({"datalist": datalist}, sort_keys=False),
                                     encoding="utf-8", newline="\n")
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return out / (datalist["id"] + ".json")


def _list(**over):
    dl = {"id": "dlCases", "name": "Cases",
          "binder": {"type": "jdbc", "sql": "SELECT id, kind, amount FROM v_cases"},
          "columns": [{"id": "kind", "label": "Kind"}]}
    dl.update(over)
    return dl


# ------------------------------------------------------------------ item 21, the sort

def test_a_descending_sort_is_written_as_the_platform_reads_it(tmp_path):
    dl = json.loads(_emit(tmp_path, _list(sort={"column": "amount", "desc": True})).read_text(encoding="utf-8"))
    assert (dl["order"], dl["orderBy"]) == ("1", "amount")


def test_an_ascending_sort_is_written_empty(tmp_path):
    dl = json.loads(_emit(tmp_path, _list(sort={"column": "kind", "desc": False})).read_text(encoding="utf-8"))
    assert (dl["order"], dl["orderBy"]) == ("", "kind")


def test_a_list_with_no_sort_is_written_empty(tmp_path):
    dl = json.loads(_emit(tmp_path, _list()).read_text(encoding="utf-8"))
    assert (dl["order"], dl["orderBy"]) == ("", "")


def test_a_form_list_is_written_empty(tmp_path):
    d = tmp_path / "specs"
    d.mkdir()
    form = {"form": {"id": "frmCase", "name": "Case"},
            "sections": [{"fields": [{"id": "ref", "type": "text", "label": "Ref"}]}]}
    (d / "F-frmCase.spec.yml").write_text(yaml.safe_dump(form), encoding="utf-8", newline="\n")
    out = tmp_path / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    dl = json.loads((out / "list_frmCase.json").read_text(encoding="utf-8"))
    assert (dl["order"], dl["orderBy"]) == ("", "")


def test_no_list_is_written_with_the_old_spelling(tmp_path):
    for i, desc in enumerate((True, False)):
        sub = tmp_path / str(i)
        sub.mkdir()
        text = _emit(sub, _list(sort={"column": "kind", "desc": desc})).read_text(encoding="utf-8")
        assert '"DESC"' not in text and '"ASC"' not in text, text


# ------------------------------------------------------------------ item 10, several choices

ZERO_ELEVEN_SELECT = {
    "name": "kind", "id": "filter_0", "label": "Kind",
    "type": {"className": "org.joget.plugin.enterprise.SelectBoxDataListFilterType",
             "properties": {"label": "", "defaultValue": "", "multiple": "", "size": "",
                            "options": [{"value": "", "label": "- any -"},
                                        {"value": "a", "label": "A"}]}},
    "filterParamName": "f_kind"}


def test_a_select_filter_of_several_choices_writes_multiple_true(tmp_path):
    f = {"id": "kind", "label": "Kind", "type": "select", "multiple": True,
         "options": [{"value": "a", "label": "A"}]}
    dl = json.loads(_emit(tmp_path, _list(filters=[f])).read_text(encoding="utf-8"))
    want = json.loads(json.dumps(ZERO_ELEVEN_SELECT))
    want["type"]["properties"]["multiple"] = "true"
    assert dl["filters"][0] == want


@pytest.mark.parametrize("extra", [{}, {"multiple": False}])
def test_a_select_filter_without_multiple_is_byte_identical_to_0_11_0(tmp_path, extra):
    f = {"id": "kind", "label": "Kind", "type": "select", "options": [{"value": "a", "label": "A"}],
         **extra}
    text = _emit(tmp_path, _list(filters=[f])).read_text(encoding="utf-8")
    dl = json.loads(text)
    assert dl["filters"][0] == ZERO_ELEVEN_SELECT
    # the bytes as json.dump(indent=2) writes the 0.11.0 filter
    assert json.dumps(dl["filters"], indent=2) == json.dumps([ZERO_ELEVEN_SELECT], indent=2)


def test_multiple_is_a_select_filters_key_alone(tmp_path):
    f = {"id": "kind", "label": "Kind", "type": "text", "multiple": True}
    dl = json.loads(_emit(tmp_path, _list(filters=[f])).read_text(encoding="utf-8"))
    assert dl["filters"][0]["type"] == {
        "className": "org.joget.apps.datalist.lib.TextFieldDataListFilterType", "properties": {}}


# ------------------------------------------------------------------ item 18, an amount column

ZERO_ELEVEN_COLUMN = {
    "name": "amount", "id": "column_0", "label": "Amount", "hidden": "false", "renderHtml": "",
    "format": {"className": "", "properties": {}}, "sortable": "true", "datalist_type": "column",
    "exclude_export": "", "width": "", "headerAlignment": "", "alignment": "", "style": "",
    "action": {"className": "", "properties": {}}}


def test_a_column_without_a_formatter_is_byte_identical_to_0_11_0(tmp_path):
    dl = json.loads(_emit(tmp_path, _list(columns=[{"id": "amount", "label": "Amount"}])).read_text(encoding="utf-8"))
    assert dl["columns"][0] == ZERO_ELEVEN_COLUMN
    assert json.dumps(dl["columns"], indent=2) == json.dumps([ZERO_ELEVEN_COLUMN], indent=2)


def test_a_currency_column_is_written_with_the_platforms_beanshell_formatter(tmp_path):
    col = {"id": "amount", "label": "Amount", "formatter": {"type": "currency", "symbol": "\u20ac"}}
    dl = json.loads(_emit(tmp_path, _list(columns=[col])).read_text(encoding="utf-8"))
    got = dl["columns"][0]
    fmt = got.pop("format")
    want = dict(ZERO_ELEVEN_COLUMN)
    want.pop("format")
    assert got == want, "the column but for its format is the 0.11.0 column"
    assert fmt["className"] == "org.joget.plugin.enterprise.BeanShellFormatter"
    script = fmt["properties"]["script"]
    assert "new java.text.DecimalFormat(\"'\u20ac'#,##0.00\"," in script
    assert "java.util.Locale.ENGLISH" in script and "new java.math.BigDecimal(v)" in script


def test_a_currency_columns_decimals_and_no_symbol(tmp_path):
    col = {"id": "amount", "label": "Amount", "formatter": {"type": "currency", "decimals": 0}}
    dl = json.loads(_emit(tmp_path, _list(columns=[col])).read_text(encoding="utf-8"))
    assert "new java.text.DecimalFormat(\"#,##0\"," in dl["columns"][0]["format"]["properties"]["script"]


@pytest.mark.parametrize("bad", [-1, 7, 2.5, "2", True])
def test_a_currency_columns_decimals_out_of_reach_are_refused(tmp_path, bad):
    col = {"id": "amount", "label": "Amount", "formatter": {"type": "currency", "decimals": bad}}
    with pytest.raises(ValueError, match="decimals"):
        _emit(tmp_path, _list(columns=[col]))


def test_a_symbol_with_a_quote_is_quoted_for_the_pattern(tmp_path):
    col = {"id": "amount", "label": "Amount", "formatter": {"type": "currency", "symbol": "o'k"}}
    dl = json.loads(_emit(tmp_path, _list(columns=[col])).read_text(encoding="utf-8"))
    assert "new java.text.DecimalFormat(\"'o''k'#,##0.00\"," in dl["columns"][0]["format"]["properties"]["script"]


JAVA = shutil.which("java")
# The BeanShell the platform runs a BeanShellFormatter's script with: its jar in a Joget 9.0.7
# installation's WEB-INF/lib, named by JOGET_BSH_JAR, else looked for in the installation on MSI.
BSH = pathlib.Path(os.environ.get("JOGET_BSH_JAR") or
                   "C:/Users/aarel/joget-enterprise-linux-9.0.7-10/apache-tomcat-11.0.21/webapps/jw/"
                   "WEB-INF/lib/bsh-2.0b6.jar")


@pytest.mark.skipif(JAVA is None or not BSH.is_file(),
                    reason="no java, or no BeanShell jar of the platform (JOGET_BSH_JAR), to run the script")
def test_the_script_the_generator_writes_shows_the_amount_as_the_records_do(tmp_path):
    """The script the generator writes for a currency column, run by the platform's own
    BeanShell with `value` bound as BeanShellFormatter binds it: 9850 with the symbol of the euro
    shows as MT-DM's records show it, "\u20ac9,850.00"; an empty or absent value shows empty, one
    with spaces around it is read without them, and a value that is no number shows as stored."""
    col = {"id": "amount", "label": "Amount", "formatter": {"type": "currency", "symbol": "\u20ac"}}
    script = json.loads(_emit(tmp_path, _list(columns=[col])).read_text(encoding="utf-8"))["columns"][0]["format"]["properties"]["script"]
    (tmp_path / "script.bsh").write_text(script, encoding="utf-8", newline="\n")
    src = tmp_path / "P.java"
    src.write_text(
        "public class P { public static void main(String[] a) throws Exception {"
        " java.io.PrintStream o = new java.io.PrintStream(System.out, true, \"UTF-8\");"
        " String s = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[0])), \"UTF-8\");"
        " for (int i = 1; i < a.length; i++) {"
        "  bsh.Interpreter in = new bsh.Interpreter(); in.set(\"value\", a[i].equals(\"<null>\") ? null : a[i]);"
        "  o.println(\"[\" + in.eval(s) + \"]\"); } } }", encoding="ascii", newline="\n")
    r = subprocess.run([JAVA, "-cp", str(BSH), str(src), str(tmp_path / "script.bsh"),
                        "9850", "1234567.5", "-3", "", "<null>", " 9850 ", "n/a"], capture_output=True)
    assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    shown = r.stdout.decode("utf-8").splitlines()
    assert shown == ["[\u20ac9,850.00]", "[\u20ac1,234,567.50]", "[-\u20ac3.00]", "[]", "[]",
                     "[\u20ac9,850.00]", "[n/a]"], shown
