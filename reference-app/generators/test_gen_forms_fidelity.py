"""gen_forms fidelity channels (registry 0.9.12).

Driven by the deployed farmersPortal export (probed 2026-08-08), whose form
family must round-trip with ZERO findings (owner objective):

  * `verbatim` field type - 12 deployed elements (QualityBanner, ConcatField,
    Signature, SmartSearch, AutoCenterBootstrap, GisPolygonCapture,
    EmbeddedDatalist, FormGrid x3) carry their full config in the export;
    the generator re-emits {className, properties} untouched.
  * `props` overlay - deployed residual properties the typed channels do not
    own (placeholder x38, maxlength x15, hidden value x3, yearRange
    'c-200:c+1', non-regenerable validators) are merged into the element's
    properties LAST, so the deployed value always wins.
  * lookup groupingColumn / useAjax / emptyLabel - deployed
    FormOptionsBinder carries groupingColumn 'district_code' / 'category',
    useAjax 'true' and authored emptyLabel '-- Select District --'.

The regression half of the 0.9.11->0.9.12 bump: a spec carrying NONE of the
new keys emits byte-identical properties (asserted LITERALLY below).

Run:  python3 -m pytest reference-app/generators/test_gen_forms_fidelity.py -q
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_forms import element  # noqa: E402


def test_verbatim_element_is_emitted_untouched():
    el = {"className": "global.govstack.gisui.element.GisPolygonCaptureElement",
          "properties": {"id": "geometry", "captureMode": "BOTH",
                         "apiKey": "%%%%secret%%%%",
                         "tileProvider": "SATELLITE_ESRI"}}
    out = element({"id": "geometry", "type": "verbatim", "element": el})
    assert out["className"] == el["className"]
    assert out["properties"] == el["properties"]


def test_props_overlay_wins_last():
    out = element({"id": "national_id", "type": "textfield", "label": "ID",
                   "props": {"placeholder": "Enter national ID number",
                             "maxlength": "20", "value": "X-1"}})
    p = out["properties"]
    assert p["placeholder"] == "Enter national ID number"
    assert p["maxlength"] == "20"
    assert p["value"] == "X-1"


def test_props_overlay_replaces_the_validator():
    v = {"className": "org.joget.apps.form.lib.DefaultValidator",
         "properties": {"type": "custom", "custom-regex": "^[0-9]+$",
                        "message": "numbers only"}}
    out = element({"id": "qty", "type": "textfield", "label": "Qty",
                   "storeNumeric": True, "props": {"validator": v}})
    assert out["properties"]["validator"] == v, \
        "the deployed validator is authoritative over the derived one"
    assert out["properties"]["storeNumeric"] == "true", \
        "the typed channels still fire; the overlay only replaces what it names"


def test_props_overlay_on_multipaged_carries_authored_buttons():
    out = element({"id": "w", "type": "multipaged",
                   "pages": [{"label": "A", "formDefId": "frmA"},
                             {"label": "B", "formDefId": "frmB"}],
                   "props": {"prevButtonlabel": "Previous"}})
    assert out["properties"]["prevButtonlabel"] == "Previous"
    assert out["properties"]["nextButtonlabel"] == "Next"


def test_lookup_reads_grouping_ajax_and_empty_label():
    out = element({"id": "collection_point", "type": "select", "label": "CP",
                   "lookup": {"formDefId": "md37collectionPoint",
                              "idColumn": "code", "labelColumn": "name",
                              "groupingColumn": "district_code",
                              "useAjax": "true",
                              "emptyLabel": "-- Select District --"}})
    b = out["properties"]["optionsBinder"]["properties"]
    assert b["groupingColumn"] == "district_code"
    assert b["useAjax"] == "true"
    assert b["emptyLabel"] == "-- Select District --"


def test_textfield_without_new_keys_is_byte_identical_to_0_9_11():
    out = element({"id": "first_name", "type": "textfield", "label": "First Name"})
    assert out == {"className": "org.joget.apps.form.lib.TextField", "properties": {
        "id": "first_name", "label": "First Name", "value": "", "placeholder": "",
        "maxlength": "", "size": "", "encryption": "", "storeNumeric": "",
        "readonly": "", "readonlyLabel": "", "style": "", "requiredSanitize": "",
        "workflowVariable": "",
        "validator": {"className": "", "properties": {}}}}


def test_lookup_without_new_keys_is_byte_identical_to_0_9_11():
    out = element({"id": "district", "type": "select", "label": "District",
                   "lookup": {"formDefId": "mdDistrict",
                              "idColumn": "code", "labelColumn": "name"}})
    assert out == {"className": "org.joget.apps.form.lib.SelectBox", "properties": {
        "id": "district", "label": "District", "value": "", "multiple": "",
        "size": "", "controlField": "", "controlValue": "", "readonly": "",
        "readonlyLabel": "", "workflowVariable": "",
        "validator": {"className": "", "properties": {}},
        "options": [],
        "optionsBinder": {
            "className": "org.joget.apps.form.lib.FormOptionsBinder",
            "properties": {"formDefId": "mdDistrict", "idColumn": "code",
                           "labelColumn": "name", "groupingColumn": "",
                           "extraCondition": "", "addEmptyOption": "true",
                           "emptyLabel": "", "useAjax": "", "cacheInterval": ""}}}}


def test_explicit_column_tags_beat_round_robin():
    """farmerIncomePrograms: 5 fields left, 6 right - i%cols can never put more
    on the right, so the explicit tag decides."""
    from gen_forms import build_form
    spec = {"form": {"id": "f", "name": "F", "table": "t"},
            "sections": [{"id": "s1", "columns": 2, "fields": [
                {"id": "a1", "type": "textfield", "label": "", "column": 0},
                {"id": "b1", "type": "textfield", "label": "", "column": 1},
                {"id": "b2", "type": "textfield", "label": "", "column": 1}]}]}
    cols = build_form(spec)["elements"][0]["elements"]
    ids = [[e["properties"]["id"] for e in c["elements"]] for c in cols]
    assert ids == [["a1"], ["b1", "b2"]]


def test_untagged_fields_distribute_round_robin_as_before():
    from gen_forms import build_form
    spec = {"form": {"id": "f", "name": "F", "table": "t"},
            "sections": [{"id": "s1", "columns": 2, "fields": [
                {"id": "a1", "type": "textfield", "label": ""},
                {"id": "b1", "type": "textfield", "label": ""},
                {"id": "a2", "type": "textfield", "label": ""}]}]}
    cols = build_form(spec)["elements"][0]["elements"]
    ids = [[e["properties"]["id"] for e in c["elements"]] for c in cols]
    assert ids == [["a1", "a2"], ["b1"]], "byte-identical to 0.9.11 absent the tag"
