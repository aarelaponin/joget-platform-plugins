"""gen_datalists 0.14.0: a state badge worded by its lifecycle's names (METHOD-2026-11-05-87, R50; MT-DM's request of 10
November 2026).

A `status_badge` formatter may carry `names`, each state's identifier mapped to its words (the kit reads them from the
lifecycle as its State filter does). The badge's BeanShellFormatter script then draws a stored value a state names by
that name, any other value exactly as stored (not capitalised), and an empty value as empty. Each name is escaped twice:
for the HTML it is drawn in (`&`, `<`, `>`, `"`) and for the BeanShell string it stands in (`\\` and `"`). A badge
without `names` is written byte for byte as 0.13.0 wrote it, held here to the script written out below, never read from
the generator's own constants.

  N1 a badge without `names`: the script byte for byte as before (the code capitalised, "_" a space)
  N2 with `names`: each name written beside its colour, in the spec's order; the value drawn by its name, any other as
     stored, an empty value empty (the script's own text)
  N3 each name escaped for the HTML and then for the BeanShell string: &, <, >, " and \\, and a character outside ASCII
  N4 `names` that is no map of text is refused
  N5 the column is drawn as HTML, by the platform's BeanShellFormatter, as before
  N6 the script run by BeanShell (bsh.Interpreter), where JOGET_BSH_JAR names its jar: every value's string
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

TONES = {"new": "grey", "in_progress": "amber", "closed": "green"}
NAMES = {"new": "New", "in_progress": "In Progress", "closed": "Closed"}
SPAN = ('return "<span style=\\"display:inline-block;padding:2px 10px;border-radius:11px;color:#fff;font-size:12px;'
        'line-height:18px;white-space:nowrap;background:"+bg+"\\">"+t+"</span>";')
PUTS = 'm.put("new","#546e7a");m.put("in_progress","#ef6c00");m.put("closed","#2e7d32");'
OLD_SCRIPT = ('java.util.Map m = new java.util.HashMap();' + PUTS +
              'String v = (value==null)?"":value.toString();'
              'if (v.equals("")) return "";'
              'String bg = m.containsKey(v)?(String)m.get(v):"#546e7a";'
              'String t = v.substring(0,1).toUpperCase()+v.substring(1).replace("_"," ");' + SPAN)


def _emit(tmp, formatter):
    d = tmp / "specs"
    d.mkdir()
    dl = {"id": "dlCases", "name": "Cases", "binder": {"type": "form", "formDefId": "frmCase"},
          "columns": [{"id": "state", "label": "State", "formatter": formatter}]}
    (d / "DL-dlCases.spec.yml").write_text(yaml.safe_dump({"datalist": dl}, sort_keys=False, allow_unicode=True),
                                           encoding="utf-8", newline="\n")
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return json.loads((out / "dlCases.json").read_text(encoding="utf-8"))["columns"][0]


def test_n1_a_badge_without_names_is_written_byte_for_byte_as_before(tmp_path):
    col = _emit(tmp_path, {"type": "status_badge", "tones": TONES})
    assert col["format"] == {"className": "org.joget.plugin.enterprise.BeanShellFormatter",
                             "properties": {"script": OLD_SCRIPT}}
    assert gen_datalists.badge_format({"type": "status_badge", "tones": TONES})["properties"]["script"] == OLD_SCRIPT


def test_n2_each_name_is_written_beside_its_colour_and_drawn_in_place_of_the_capitalised_code(tmp_path):
    script = _emit(tmp_path, {"type": "status_badge", "tones": TONES, "names": NAMES})["format"]["properties"]["script"]
    assert script == ('java.util.Map m = new java.util.HashMap();' + PUTS +
                      'java.util.Map n = new java.util.HashMap();'
                      'n.put("new","New");n.put("in_progress","In Progress");n.put("closed","Closed");'
                      'String v = (value==null)?"":value.toString();'
                      'if (v.equals("")) return "";'
                      'String bg = m.containsKey(v)?(String)m.get(v):"#546e7a";'
                      'String t = n.containsKey(v)?(String)n.get(v):v;' + SPAN)
    assert "toUpperCase" not in script            # a value no state names is drawn as stored, not capitalised


def test_n3_each_name_is_escaped_for_the_html_and_then_for_the_beanshell_string():
    names = {"amp": "A & B", "lt": "<b>", "gt": "x > y", "quote": 'say "no"', "back": "a\\b", "accent": "Révisé",
             "all": 'R&D <"x"> \\'}
    script = gen_datalists.badge_format({"type": "status_badge", "tones": {}, "names": names})["properties"]["script"]
    assert ('n.put("amp","A &amp; B");n.put("lt","&lt;b&gt;");n.put("gt","x &gt; y");'
            'n.put("quote","say &quot;no&quot;");n.put("back","a\\\\b");n.put("accent","Révisé");'
            'n.put("all","R&amp;D &lt;&quot;x&quot;&gt; \\\\");') in script
    # the code is written as a BeanShell string too
    s2 = gen_datalists.badge_format({"type": "status_badge", "tones": {}, "names": {'a"b': "X"}})["properties"]["script"]
    assert 'n.put("a\\"b","X");' in s2


def test_n4_names_that_are_no_map_of_text_are_refused():
    for bad in (["New"], {"new": 3}, "New"):
        with pytest.raises(ValueError, match=r"a status badge's `names` maps each state's identifier to its words"):
            gen_datalists.badge_format({"type": "status_badge", "tones": TONES, "names": bad})


def test_n5_the_column_is_drawn_as_html_by_the_platforms_beanshell_formatter(tmp_path):
    col = _emit(tmp_path, {"type": "status_badge", "tones": TONES, "names": NAMES})
    assert col["renderHtml"] == "true"
    assert col["format"]["className"] == "org.joget.plugin.enterprise.BeanShellFormatter"


BSH = os.environ.get("JOGET_BSH_JAR")


@pytest.mark.skipif(not BSH or not pathlib.Path(BSH).is_file() or not shutil.which("java"),
                    reason="JOGET_BSH_JAR names no BeanShell jar, or no java is on the path")
def test_n6_the_script_run_by_beanshell_draws_each_value(tmp_path):
    names = dict(NAMES, quoted='A & "B" <c> \\d é')
    script = gen_datalists.badge_format({"type": "status_badge", "tones": TONES, "names": names})["properties"]["script"]
    values = ['"new"', '"in_progress"', '"quoted"', '"no_state_names_it"', '""', "null"]
    body = "String f(Object value) {\n" + script + "\n}\n" + "".join(
        f'print("<<" + f({v}) + ">>");\n' for v in values)
    f = tmp_path / "badge.bsh"
    f.write_text(body, encoding="utf-8")
    r = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-cp", BSH, "bsh.Interpreter",
                        str(f)], capture_output=True, text=True, encoding="utf-8", timeout=120)
    got = [ln[2:-2] for ln in r.stdout.splitlines() if ln.startswith("<<") and ln.endswith(">>")]
    pill = ('<span style="display:inline-block;padding:2px 10px;border-radius:11px;color:#fff;font-size:12px;'
            'line-height:18px;white-space:nowrap;background:{}">{}</span>')
    assert got == [pill.format("#546e7a", "New"), pill.format("#ef6c00", "In Progress"),
                   pill.format("#546e7a", "A &amp; &quot;B&quot; &lt;c&gt; \\d é"),
                   pill.format("#546e7a", "no_state_names_it"), "", ""], r.stdout + r.stderr
