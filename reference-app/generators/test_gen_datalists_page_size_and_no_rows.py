"""gen_datalists 0.17.0: a list's page size and its own words for no rows (METHOD-2026-11-05-125, R69; MT-DM's
requests 7 and 14 of 16 and 18 November 2026).

Request 7. The kit's spec carries the model's `page_size` as `pageSize`; the generator writes it as the datalist's
`pageSize`, which the platform reads as the list's default page size (wflow-core 9.0.7, JsonUtil: Integer.parseInt,
DataList.setDefaultPageSize). Up to 0.16.0 every list was written `"pageSize": 0`, and the platform drew 10 rows a page.
Request 14. The spec's `noRows` is built into the datalist: the two builder properties DataList.getStyles writes into
the list's own <style> (`<id selector> .filter-cell{<the property>}`), which draw the list's words in place of
displaytag's "Nothing found to display." in the empty row's cell.

A spec with neither key is held to the 0.16.0 bytes, written out here in full, never read from the generator.
"""
import json
import pathlib
import re
import sys

import yaml

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gen_datalists  # noqa: E402


def _emit(tmp, datalist):
    d = tmp / "specs"
    d.mkdir()
    (d / "DL-x.spec.yml").write_text(yaml.safe_dump({"datalist": datalist}, sort_keys=False),
                                     encoding="utf-8", newline="\n")
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return json.loads((out / (datalist["id"] + ".json")).read_text(encoding="utf-8"))


def _list(**over):
    d = {"id": "lsMyWorklist", "name": "My cases", "binder": {"type": "form", "formDefId": "recDebtCase"},
         "columns": [{"id": "case_number", "label": "Case"}]}
    d.update(over)
    return d


#: the 0.16.0 envelope of _list(), written out in full
V016 = {"id": "lsMyWorklist", "name": "My cases",
        "binder": {"className": "org.joget.plugin.enterprise.AdvancedFormRowDataListBinder",
                   "properties": {"formDefId": "recDebtCase", "extraCondition": ""}},
        "filters": [], "actions": [], "rowActions": [], "order": "", "orderBy": "", "useSession": "false",
        "showPageSizeSelector": "true", "pageSize": 0, "pageSizeSelectorOptions": "10,20,30,40,50,100",
        "buttonPosition": "bothLeft", "checkboxPosition": "left"}


def test_a_spec_with_neither_key_is_written_as_0_16_0_wrote_it(tmp_path):
    got = _emit(tmp_path, _list())
    got.pop("columns")
    assert got == V016


def test_7_the_page_size_is_written_as_the_datalists_page_size(tmp_path):
    got = _emit(tmp_path, _list(pageSize=50))
    assert got["pageSize"] == 50
    assert {k: v for k, v in got.items() if k not in ("columns", "pageSize")} == {
        k: v for k, v in V016.items() if k != "pageSize"}


WORDS = "No case meets these criteria. The criteria stay in view so that you can widen them."


def _as_the_platform_draws_it(dl):
    """The list's <style> as DataList.getStyles writes it for the prefix FILTER_ (wflow-core 9.0.7: generateStyle's
    recipe `\\u0001 \\u0001{\\u0001}` over STYLE, the selector `.dataList#dataList_<id> .filter-cell`), where the datalist
    gives BUILDER_GENERATED_FILTER_CSS and so keeps its own BUILDER_GENERATED_FILTER_STYLE
    (JsonUtil.generateBuilderProperties)."""
    assert "BUILDER_GENERATED_FILTER_CSS" in dl
    return "" + " " + f".dataList#dataList_{dl['id']} .filter-cell" + "{" + dl["BUILDER_GENERATED_FILTER_STYLE"] + "}"


def _rules(css):
    """The rules of a style sheet without at-rules: [(selector, body)], each brace closed in order, strings kept whole."""
    out, i, n = [], 0, len(css)
    while i < n:
        j = css.find("{", i)
        if j < 0:
            assert css[i:].strip() == "", css[i:]
            break
        sel, k, body, s = css[i:j].strip(), j + 1, "", None
        while True:
            c = css[k]
            if s:
                if c == "\\":
                    body += css[k:k + 2]
                    k += 2
                    continue
                if c == s:
                    s = None
            elif c in "\"'":
                s = c
            elif c == "}":
                break
            body += c
            k += 1
        out.append((sel, body))
        i = k + 1
    return out


def test_14_the_lists_words_are_drawn_in_place_of_the_platforms_in_the_empty_row(tmp_path):
    dl = _emit(tmp_path, _list(noRows=WORDS))
    rules = dict(_rules(_as_the_platform_draws_it(dl)))
    cell = ".dataList#dataList_lsMyWorklist table tbody tr.empty td"
    assert "visibility:hidden" in rules[cell] and "font-size:0" in rules[cell], rules
    assert f'content:"{WORDS}"' in rules[cell + "::before"] and "visibility:visible" in rules[cell + "::before"], rules
    assert {k: v for k, v in dl.items() if not k.startswith(("columns", "BUILDER_GENERATED_FILTER_"))} == V016


def test_14_no_word_closes_the_string_or_the_style_it_stands_in(tmp_path):
    words = 'Say "none" \\ here </style><script>alert(1)</script>\nnext line'
    dl = _emit(tmp_path, _list(noRows=words))
    css = _as_the_platform_draws_it(dl)
    assert "</style" not in css.lower() and "<" not in css and ">" not in css
    rules = dict(_rules(css))
    before = rules[".dataList#dataList_lsMyWorklist table tbody tr.empty td::before"]
    m = re.search(r'content:"((?:[^"\\]|\\.)*)"', before)
    assert m, before
    # the CSS string read back as the browser reads it gives the words, the line's end as a line's end
    back = re.sub(r"\\([0-9A-Fa-f]{1,6}) ?", lambda x: chr(int(x.group(1), 16)), m.group(1))
    back = re.sub(r"\\(.)", r"\1", back)
    assert back == words.replace("\n", "\n"), back
