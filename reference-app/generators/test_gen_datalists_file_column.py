"""gen_datalists 0.16.0: a column that opens a stored file (METHOD-2026-11-05-93, R53; MT-DM's request of 11 November 2026,
UC-DM-12-S04.L05).

A column whose spec formatter is `{type: file, record, form}` or `{type: file, record, form_from, forms}` holds the name of a
file a form's file field stored. The generator writes it as the platform's BeanShellFormatter, drawn as HTML, whose script
draws, where the row holds a name, the record's id (the column `record` names) and a form the column may open, a link to the
platform's download address; and everywhere else the stored value, escaped for HTML, with no link. A spec without such a
column is written byte for byte as 0.15.0 wrote it.

  F1 the column with `form_from` and `forms`, written out here in full, script and all
  F2 the column with `form`: the form written into the script, no column read for it
  F3 a spec that names no `record`, neither or both of `form` and `form_from`, `forms` beside `form`, `form_from` without
     `forms`, or `forms` that are no list of names, is refused
  F4 each value the spec names, and the column's name, is written into the script as a BeanShell string (`\\` and `"`
     escaped)
  F5 a column without the format, and a date column, are written byte for byte as before (written out here)
  F6 the cell as the platform draws it - DataListDecorator.formatColumn (wflow-core 9.0.7) over a column drawn as HTML whose
     format is the platform's own BeanShellFormatter (jw-enterprise-plugins 9.0.7) with the script, on the installation's
     jars - on a name holding `"`, `'`, `<`, `&`, a space, `+` and a character outside ASCII; two names joined by `;`; an
     empty name; no name; an empty record id; no record id; a form not among `forms`; no current request (an export); no
     current app: the platform's download address where a file is stored, the stored value escaped and no link everywhere
     else; the address and the text computed here, by this file's own encoder and escaper, never read from the generator.
     formatColumn hands a formatter of a column drawn as HTML the value stripped by StringUtil.stripHtmlRelaxed, which
     changes the first name (its `<b>` and `&`); the link names the stored file, read from the row
  F7 the platform's own ImageFormatter (wflow-core 9.0.7, imageSrc `form`), run on the same name, builds the same address,
     up to the `?timestamp=` it adds to defeat a browser's cache
  F8 the value formatColumn hands a formatter of a column drawn as HTML is not the stored name (F6's reason)

F6, F7 and F8 need java and a Joget 9.0.7 installation: JOGET_TOMCAT_DIR names its Tomcat folder (webapps/jw/WEB-INF/lib and
lib/), else it is looked for on MSI; without them they are skipped.
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

FROM = {"type": "file", "record": "id", "form_from": "file_form", "forms": ["frmRequest", "frmDocAttach"]}
FIXED = {"type": "file", "record": "id", "form": "frmRequest"}

#: F1: the script gen_datalists 0.16.0 writes for FROM, written out here
SCRIPT_FROM = ('String v = (value==null)?"":value.toString();try {Object so = org.joget.apps.datalist.service.DataLi'
              'stService.evaluateColumnValueFromRow(row,"file_name");if (so != null) v = org.joget.commons.util.Sec'
              'urityUtil.decrypt(so.toString());} catch (Exception x) {}String e = v.replace("&","&amp;").replace("'
              '<","&lt;").replace(">","&gt;").replace("\\"","&quot;").replace("\'","&#39;");try {java.util.Set fs = n'
              'ew java.util.HashSet();fs.add("frmRequest");fs.add("frmDocAttach");Object ro = org.joget.apps.datali'
              'st.service.DataListService.evaluateColumnValueFromRow(row,"id");String id = (ro==null)?"":ro.toStrin'
              'g().trim();Object fo = org.joget.apps.datalist.service.DataListService.evaluateColumnValueFromRow(ro'
              'w,"file_form");String f = (fo==null)?"":fo.toString().trim();if (v.trim().equals("") || id.equals(""'
              ') || !fs.contains(f)) return e;rq = org.joget.workflow.util.WorkflowUtil.getHttpServletRequest();ad '
              '= org.joget.apps.app.service.AppUtil.getCurrentAppDefinition();if (rq == null || ad == null) return '
              'e;String b = rq.getContextPath() + "/web/client/app/" + java.net.URLEncoder.encode(ad.getAppId(),"UT'
              'F8").replaceAll("\\\\+","%20") + "/" + java.net.URLEncoder.encode(ad.getVersion().toString(),"UTF8").r'
              'eplaceAll("\\\\+","%20") + "/form/download/" + java.net.URLEncoder.encode(f,"UTF8").replaceAll("\\\\+","'
              '%20") + "/" + java.net.URLEncoder.encode(id,"UTF8").replaceAll("\\\\+","%20") + "/";StringBuilder o = '
              'new StringBuilder();String[] ps = v.split(";");for (int i = 0; i < ps.length; i++) {String p = ps[i]'
              ';if (p.trim().equals("")) continue;String h = b + java.net.URLEncoder.encode(p,"UTF8").replaceAll("\\'
              '\\+","%20") + ".";if (o.length() > 0) o.append(", ");o.append("<a href=\\"" + h.replace("&","&amp;").r'
              'eplace("<","&lt;").replace(">","&gt;").replace("\\"","&quot;").replace("\'","&#39;") + "\\" target=\\"_b'
              'lank\\">" + p.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\\"","&quot;").rep'
              'lace("\'","&#39;") + "</a>");}if (o.length() == 0) return e;return o.toString();} catch (Exception x)'
              ' { return e; }')


def _emit(tmp, columns):
    d = tmp / "specs"
    d.mkdir()
    dl = {"id": "dlPapers", "name": "Papers", "binder": {"type": "jdbc", "sql": "SELECT id, file_name, file_form FROM v"},
          "columns": columns}
    (d / "DL-dlPapers.spec.yml").write_text(yaml.safe_dump({"datalist": dl}, sort_keys=False, allow_unicode=True),
                                            encoding="utf-8", newline="\n")
    out = tmp / "out"
    out.mkdir()
    sys.argv = ["gen_datalists.py", str(d), str(out)]
    gen_datalists.main()
    return json.loads((out / "dlPapers.json").read_text(encoding="utf-8"))["columns"]


def _column(name, label, fmt, render_html):
    return {"name": name, "id": "column_0", "label": label, "hidden": "false", "renderHtml": render_html,
            "format": fmt, "sortable": "true", "datalist_type": "column", "exclude_export": "", "width": "",
            "headerAlignment": "", "alignment": "", "style": "", "action": {"className": "", "properties": {}}}


def test_f1_the_column_with_form_from_written_out_in_full(tmp_path):
    (got,) = _emit(tmp_path, [{"id": "file_name", "label": "The file", "formatter": FROM}])
    assert got == _column("file_name", "The file", {"className": "org.joget.plugin.enterprise.BeanShellFormatter",
                                                    "properties": {"script": SCRIPT_FROM}}, "true"), got


def test_f2_the_column_with_form_names_its_form_in_the_script(tmp_path):
    (got,) = _emit(tmp_path, [{"id": "file_name", "label": "The file", "formatter": FIXED}])
    script = got["format"]["properties"]["script"]
    assert got["renderHtml"] == "true" and got["format"]["className"] == "org.joget.plugin.enterprise.BeanShellFormatter"
    assert 'java.util.Set fs = new java.util.HashSet();fs.add("frmRequest");' in script, script
    assert 'Object fo = "frmRequest";' in script and "file_form" not in script, script
    assert script == SCRIPT_FROM.replace('fs.add("frmRequest");fs.add("frmDocAttach");', 'fs.add("frmRequest");').replace(
        'Object fo = org.joget.apps.datalist.service.DataListService.evaluateColumnValueFromRow(row,"file_form");',
        'Object fo = "frmRequest";'), script


@pytest.mark.parametrize("fmt,words", [
    ({"type": "file", "form": "frmRequest"}, "a file column's `record` names the column of the row"),
    ({"type": "file", "record": "id"}, "a file column names its form by `form` or by `form_from`"),
    ({"type": "file", "record": "id", "form": "a", "form_from": "b", "forms": ["a"]}, "one of the two"),
    ({"type": "file", "record": "id", "form": "a", "forms": ["a"]}, "`forms` is the key of a file column whose form comes"),
    ({"type": "file", "record": "id", "form_from": "b"}, "lists with `forms` the forms that column may name"),
    ({"type": "file", "record": "id", "form_from": "b", "forms": "a"}, "lists with `forms` the forms that column may name"),
    ({"type": "file", "record": "id", "form_from": "b", "forms": []}, "lists with `forms` the forms that column may name"),
], ids=["no record", "no form", "both", "forms beside form", "form_from without forms", "forms no list", "forms empty"])
def test_f3_a_spec_short_of_its_keys_is_refused(tmp_path, fmt, words):
    with pytest.raises(ValueError, match=words.replace("(", r"\(").replace(")", r"\)")):
        _emit(tmp_path, [{"id": "file_name", "label": "The file", "formatter": fmt}])


def test_f4_each_value_is_written_as_a_beanshell_string():
    script = gen_datalists.file_format({"type": "file", "record": 'r"1\\', "form_from": 'f"o', "forms": ['a"b\\c', "d"]},
                                       'n"m\\')["properties"]["script"]
    assert 'fs.add("a\\"b\\\\c");fs.add("d");' in script, script
    assert 'evaluateColumnValueFromRow(row,"r\\"1\\\\")' in script and 'evaluateColumnValueFromRow(row,"f\\"o")' in script
    assert 'evaluateColumnValueFromRow(row,"n\\"m\\\\")' in script, script


def test_f5_a_column_without_the_format_and_a_date_column_are_written_as_before(tmp_path):
    cols = _emit(tmp_path, [{"id": "file_name", "label": "The file"},
                            {"id": "date_attached", "label": "Date attached", "formatter": {"type": "date"}}])
    assert cols[0] == _column("file_name", "The file", {"className": "", "properties": {}}, "")
    want = _column("date_attached", "Date attached", {"className": "org.joget.plugin.enterprise.DateFormatter",
                                                      "properties": {"dataFormat": "yyyy-MM-dd",
                                                                     "displayFormat": "dd/MM/yyyy"}}, "")
    want["id"] = "column_1"
    assert cols[1] == want, cols[1]


# ------------------------------------------------------------------ the script run by the platform's own formatter

TOMCAT = pathlib.Path(os.environ.get("JOGET_TOMCAT_DIR") or "C:/Users/aarel/joget-enterprise-linux-9.0.7-10/apache-tomcat-11.0.21")
JARS = TOMCAT / "webapps" / "jw" / "WEB-INF" / "lib"
JAVA = shutil.which("java")
NEEDS = pytest.mark.skipif(JAVA is None or not (JARS / "bsh-2.0b6.jar").is_file() or not (TOMCAT / "lib").is_dir(),
                           reason="no java, or no Joget 9.0.7 installation (JOGET_TOMCAT_DIR) to run the formatters on")
RUN_JAVA = ('// Run.java column SCRIPT NAME CASES OUT | image FORMDEFID RECORDCOLUMN CASES OUT - runs a list colu'
           "mn's formatter of the platform\n// (Joget 9.0.7, on the installation's jars) on rows of a query list."
           '  `column`: the cell as the platform draws it,\n// org.joget.apps.datalist.service.DataListDecorator.'
           'formatColumn, over a column NAME drawn as HTML whose one format is\n// org.joget.plugin.enterprise.Be'
           'anShellFormatter with the script SCRIPT - formatColumn hands the formatter the value stripped by\n// '
           'StringUtil.stripHtmlRelaxed, and the formatter binds datalist, column, row and value and runs the sc'
           "ript by\n// AppPluginUtil.executeScript; the case's value stands in the row under NAME.  `image`: org"
           ".joget.apps.datalist.lib.ImageFormatter,\n// its format called directly on the case's value (imageSrc"
           ' "form", its formDefId\n// and recordId given, imagefullsize "true", so that it writes the download a'
           "ddress as a link's href; its AppService is a\n// stand-in answering a table name, in a Spring StaticA"
           'pplicationContext given to AppUtil and WorkflowUtil.  Each line of CASES:\n// label TAB context TAB a'
           'pp TAB value TAB row, each field but the label hex of its UTF-8 bytes, "-" for none - the context pa'
           'th\n// a current request answers (none: no request, RequestContextHolder reset), "<appId>/<version>" '
           'of the current app (none: no\n// current app), the cell\'s value, and the row as "key=hex,key=hex" (a '
           "HashMap, as a query list's row is).  Writes to OUT one\n// line each: label TAB the formatter's resul"
           't as hex, <null>, or the hex of "<exception ...>".\nimport java.nio.charset.StandardCharsets;\nimport '
           'java.nio.file.Files;\nimport java.nio.file.Paths;\nimport java.util.HashMap;\nimport java.util.List;\nim'
           'port java.util.Map;\n\npublic class Run {\n    static String hex(String s) {\n        StringBuilder b = '
           'new StringBuilder();\n        for (byte x : s.getBytes(StandardCharsets.UTF_8)) b.append(String.forma'
           't("%02x", x & 0xff));\n        return b.toString();\n    }\n\n    static String unhex(String h) {\n      '
           '  byte[] b = new byte[h.length() / 2];\n        for (int i = 0; i < b.length; i++) b[i] = (byte) Inte'
           'ger.parseInt(h.substring(2 * i, 2 * i + 2), 16);\n        return new String(b, StandardCharsets.UTF_8'
           ');\n    }\n\n    static Object stub(Class<?> type, String name, Object answer) {\n        return java.la'
           'ng.reflect.Proxy.newProxyInstance(Run.class.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> '
           '{\n            if (m.getName().equals(name)) return answer;\n            Class<?> t = m.getReturnType('
           ');\n            if (t == boolean.class) return false;\n            if (t == int.class) return 0;\n     '
           '       if (t == long.class) return 0L;\n            if (m.getName().equals("toString")) return "a sta'
           'nd-in " + type.getSimpleName();\n            return null;\n        });\n    }\n\n    public static void m'
           'ain(String[] a) throws Exception {\n        boolean image = a[0].equals("image");\n        String scri'
           'pt = image ? null : new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.UTF_8);\n       '
           ' int k = 3;\n        if (image) {\n            org.springframework.context.support.StaticApplicationCo'
           'ntext ctx = new org.springframework.context.support.StaticApplicationContext();\n            ctx.getB'
           'eanFactory().registerSingleton("appService",\n                    stub(org.joget.apps.app.service.App'
           'Service.class, "getFormTableName", "stand_in_table"));\n            ctx.refresh();\n            new or'
           'g.joget.apps.app.service.AppUtil().setApplicationContext(ctx);\n            new org.joget.workflow.ut'
           'il.WorkflowUtil().setApplicationContext(ctx);\n        }\n        List<String> lines = Files.readAllLi'
           'nes(Paths.get(a[k]), StandardCharsets.UTF_8);\n        java.io.PrintStream o = new java.io.PrintStrea'
           'm(new java.io.FileOutputStream(a[k + 1]), true, "UTF-8");\n        for (String ln : lines) {\n        '
           '    if (ln.isEmpty()) continue;\n            String[] f = ln.split("\\t", -1);\n            if (!f[1].e'
           'quals("-")) {\n                org.springframework.web.context.request.RequestContextHolder.setReques'
           'tAttributes(\n                        new org.springframework.web.context.request.ServletRequestAttri'
           'butes((jakarta.servlet.http.HttpServletRequest)\n                                stub(jakarta.servlet'
           '.http.HttpServletRequest.class, "getContextPath", unhex(f[1]))));\n            } else {\n             '
           '   org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();\n          '
           '  }\n            if (!f[2].equals("-")) {\n                String[] av = unhex(f[2]).split("/", 2);\n  '
           '              org.joget.apps.app.model.AppDefinition ad = new org.joget.apps.app.model.AppDefinition'
           '();\n                ad.setAppId(av[0]);\n                ad.setVersion(Long.valueOf(av[1]));\n        '
           '        org.joget.apps.app.service.AppUtil.setCurrentAppDefinition(ad);\n            } else {\n       '
           '         org.joget.apps.app.service.AppUtil.setCurrentAppDefinition(null);\n            }\n           '
           ' Object value = f[3].equals("-") ? null : unhex(f[3]);\n            Map<String, Object> row = new Has'
           'hMap<>();\n            if (!f[4].equals("-") && !f[4].isEmpty()) {\n                for (String kv : f'
           '[4].split(",")) {\n                    String[] p = kv.split("=", 2);\n                    row.put(p[0'
           '], unhex(p[1]));\n                }\n            }\n            String r;\n            try {\n           '
           '     if (image) {\n                    org.joget.apps.datalist.lib.ImageFormatter im = new org.joget.'
           'apps.datalist.lib.ImageFormatter();\n                    im.setProperty("imageSrc", "form");\n        '
           '            im.setProperty("formDefId", a[1]);\n                    im.setProperty("recordId", a[2]);'
           '\n                    im.setProperty("imagefullsize", "true");\n                    im.setProperty("he'
           'ight", "");\n                    im.setProperty("width", "");\n                    im.setProperty("def'
           'aultImage", "");\n                    r = im.format(null, null, row, value);\n                } else {'
           '\n                    // the cell as the platform draws it: DataListDecorator.formatColumn over a col'
           'umn drawn as HTML, named a[2],\n                    // whose one format is the BeanShellFormatter (se'
           "tProperty puts the one property into the plugin's map;\n                    // setProperties would fi"
           'rst resolve hash variables through the Spring context of a running instance);\n                    //'
           " the row holds the stored value under the column's name, as a query list's row does\n                "
           '    org.joget.plugin.enterprise.BeanShellFormatter bf = new org.joget.plugin.enterprise.BeanShellFor'
           'matter();\n                    bf.setProperty("script", script);\n                    org.joget.apps.d'
           'atalist.model.DataListColumn col = new org.joget.apps.datalist.model.DataListColumn(a[2], a[2], true'
           ');\n                    col.setRenderHtml(true);\n                    col.addFormat(bf);\n             '
           '       org.joget.apps.datalist.model.DataList dl = new org.joget.apps.datalist.model.DataList();\n   '
           '                 dl.setColumns(new org.joget.apps.datalist.model.DataListColumn[]{col});\n           '
           '         row.put(a[2], value);\n                    r = new org.joget.apps.datalist.service.DataListD'
           'ecorator(dl).formatColumn(col, row, value);\n                }\n            } catch (Exception e) {\n  '
           '              r = "<exception " + e + ">";\n            }\n            o.println(f[0] + "\\t" + (r == n'
           'ull ? "<null>" : hex(r)));\n        }\n        o.close();\n    }\n}\n')

NAME = "Ann's \"<b>&co\" r\u00e9sum\u00e9 +1.pdf"
CTX, APP, VER = "/jw", "mtcaDm", "82"
CASES = [  # label, request, app, value, row
    ("named", True, True, NAME, {"id": "r-1", "file_form": "frmDocAttach"}),
    ("two names", True, True, "a.pdf;b c.pdf", {"id": "r-2", "file_form": "frmRequest"}),
    ("an empty name", True, True, "", {"id": "r-1", "file_form": "frmRequest"}),
    ("no name", True, True, None, {"id": "r-1", "file_form": "frmRequest"}),
    ("an empty record id", True, True, NAME, {"id": "", "file_form": "frmRequest"}),
    ("no record id", True, True, NAME, {"file_form": "frmRequest"}),
    ("a form not among forms", True, True, NAME, {"id": "r-1", "file_form": "frmOther"}),
    ("no request", False, True, NAME, {"id": "r-1", "file_form": "frmRequest"}),
    ("no app", True, False, NAME, {"id": "r-1", "file_form": "frmRequest"}),
]


def _enc(s):
    """The platform's encoding of a part of the address, written out here: java.net.URLEncoder.encode(s, "UTF8") - letters,
    digits and `.-*_` kept, a space `+`, every other byte of the UTF-8 `%XX` - then every `+` replaced by `%20`."""
    out = []
    for b in s.encode("utf-8"):
        c = chr(b)
        out.append(c if (c.isascii() and c.isalnum()) or c in ".-*_" else "+" if c == " " else "%%%02X" % b)
    return "".join(out).replace("+", "%20")


def _html(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&#39;")


def _address(form, rid, name):
    return f"{CTX}/web/client/app/{_enc(APP)}/{_enc(VER)}/form/download/{_enc(form)}/{_enc(rid)}/{_enc(name)}."


def _run(tmp_path, mode, script_or_args):
    hx = lambda s: s.encode("utf-8").hex()
    lines = []
    for label, req, app, val, row in CASES:
        rowtxt = ",".join(f"{k}={hx(v)}" for k, v in row.items()) or "-"
        lines.append("\t".join([label, hx(CTX) if req else "-", hx(f"{APP}/{VER}") if app else "-",
                                hx(val) if val is not None else "-", rowtxt]))
    (tmp_path / "cases.txt").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    (tmp_path / "Run.java").write_text(RUN_JAVA, encoding="utf-8", newline="\n")
    cp = os.pathsep.join([str(JARS / "*"), str(TOMCAT / "lib" / "*")])
    if mode == "column":
        (tmp_path / "script.bsh").write_text(script_or_args, encoding="utf-8", newline="\n")
        args = ["column", str(tmp_path / "script.bsh"), "file_name"]
    else:
        args = ["image", *script_or_args]
    r = subprocess.run([JAVA, "-cp", cp, str(tmp_path / "Run.java"), *args, str(tmp_path / "cases.txt"),
                        str(tmp_path / "out.txt")], capture_output=True, timeout=600)
    assert r.returncode == 0, (r.stdout + r.stderr).decode("utf-8", "replace")[-4000:]
    got = {}
    for ln in (tmp_path / "out.txt").read_text(encoding="utf-8").splitlines():
        label, h = ln.split("\t")
        got[label] = None if h == "<null>" else bytes.fromhex(h).decode("utf-8")
    return got


@NEEDS
def test_f6_the_script_run_by_the_platforms_beanshell_formatter(tmp_path):
    got = _run(tmp_path, "column", SCRIPT_FROM)
    link = lambda form, rid, name: f'<a href="{_html(_address(form, rid, name))}" target="_blank">{_html(name)}</a>'
    assert got["named"] == link("frmDocAttach", "r-1", NAME), got["named"]
    assert got["two names"] == link("frmRequest", "r-2", "a.pdf") + ", " + link("frmRequest", "r-2", "b c.pdf"), got
    for label in ("an empty name", "no name"):
        assert got[label] == "", (label, got[label])
    for label in ("an empty record id", "no record id", "a form not among forms", "no request", "no app"):
        assert got[label] == _html(NAME), (label, got[label])
        assert "<a" not in got[label] and "<b>" not in got[label] and "&co" not in got[label].replace("&amp;co", "")
    # the name's own characters, each where it must stand
    assert "Ann%27s%20%22%3Cb%3E%26co%22%20r%C3%A9sum%C3%A9%20%2B1.pdf." in got["named"]
    assert ">Ann&#39;s &quot;&lt;b&gt;&amp;co&quot; r\u00e9sum\u00e9 +1.pdf</a>" in got["named"]


@NEEDS
def test_f7_the_platforms_image_formatter_builds_the_same_address(tmp_path):
    import re
    (tmp_path / "ours").mkdir()
    (tmp_path / "theirs").mkdir()
    ours = _run(tmp_path / "ours", "column", SCRIPT_FROM)
    # the platform's formatter takes its form from its own property: frmDocAttach, the form the row "named" names
    theirs = _run(tmp_path / "theirs", "image", ["frmDocAttach", "id"])
    ours_href = re.findall(r'<a href="([^"]*)"', ours["named"])
    their_href = re.findall(r'<a href="([^"]*)\?timestamp=\d+"', theirs["named"])
    assert len(ours_href) == 1 and ours_href == their_href, (ours_href, their_href)
    assert ours_href[0] == _address("frmDocAttach", "r-1", NAME)


@NEEDS
def test_f8_the_value_the_platform_hands_the_formatter_is_not_the_stored_name(tmp_path):
    """F6's reason: through formatColumn, a formatter of a column drawn as HTML receives the value stripped by
    StringUtil.stripHtmlRelaxed, not the stored name; a script that drew `value` would address another file. The script
    the generator writes reads the name from the row (F6 holds the link to the stored name)."""
    got = _run(tmp_path, "column", 'return (value==null)?"<null>":value.toString();')
    assert got["named"] != NAME and "&amp;co" in got["named"], got["named"]
    assert got["two names"] == "a.pdf;b c.pdf", got["two names"]
