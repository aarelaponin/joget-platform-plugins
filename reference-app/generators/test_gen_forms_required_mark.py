"""gen_forms: a field's optional `required_mark` (joget-spec-kit METHOD-2026-11-05-95, R54; MT-DM's request P-d of 12
November 2026, the Reject reason).

A field that a validation of a move requires is drawn, on that move's page, with the platform's required mark, and its
empty value is refused by the form root's RequireGuard in the validation's words. The mark must refuse nothing a second
time: the platform's DefaultValidator draws the mark only where it is `mandatory`, which would refuse the empty value
again, and the platform joins two errors of one field into one message (FormData.addFormError). So `required_mark`
writes the platform's BeanShellValidator with the decoration "*" (BeanShellValidator.getElementDecoration returns its
`decoration`) and the script `return true;`, which refuses nothing.

  M1 a field without the key is written byte for byte as before, held to the elements written out below (never read
     from the generator's own constants)
  M2 with the key, the field's validator slot holds that BeanShellValidator, and every other property is as without it
  M3 `required` wins: a required field keeps its DefaultValidator (mandatory), the key changing nothing
  M4 a number keeps the DefaultValidator a number is written with (`type: double`), the key changing nothing: the
     platform draws no mark on it without `mandatory` (the stated limit of the mark)
  M5 a validator the spec names in `props` wins over the mark, as it wins over every derived validator
  M6 a whole form: the key changes only the validators of the fields that carry it, and the form without the key is
     written byte for byte as before
"""
import copy
import json
import pathlib
import sys

import pytest

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from gen_forms import build_form, element  # noqa: E402

NONE = {"className": "", "properties": {}}
MARK = {"className": "org.joget.apps.form.lib.BeanShellValidator",
        "properties": {"script": "return true;", "decoration": "*"}}
TEXTFIELD = {"className": "org.joget.apps.form.lib.TextField", "properties": {
    "id": "rejection_note", "label": "Rejection note", "value": "", "placeholder": "", "maxlength": "", "size": "",
    "encryption": "", "storeNumeric": "", "readonly": "", "readonlyLabel": "", "style": "", "requiredSanitize": "",
    "workflowVariable": "", "validator": {"className": "", "properties": {}}}}
TEXTAREA = {"className": "org.joget.apps.form.lib.TextArea", "properties": {
    "id": "rejection_reason", "label": "Rejection reason", "value": "", "placeholder": "", "rows": "4", "cols": "80",
    "readonly": "", "readonlyLabel": "", "requiredSanitize": "", "workflowVariable": "",
    "validator": {"className": "", "properties": {}}}}
REQUIRED = {"className": "org.joget.apps.form.lib.DefaultValidator",
            "properties": {"mandatory": "true", "type": "", "message": ""}}
NUMBER = {"className": "org.joget.apps.form.lib.DefaultValidator",
          "properties": {"mandatory": "", "type": "double", "message": ""}}

FIELDS = [
    {"id": "rejection_note", "type": "textfield", "label": "Rejection note"},
    {"id": "rejection_reason", "type": "textarea", "label": "Rejection reason"},
    {"id": "outcome", "type": "select", "label": "Outcome",
     "static_options": [{"value": "pending", "label": "Pending"}, {"value": "unsuccessful", "label": "Unsuccessful"}]},
    {"id": "date_performed", "type": "date", "label": "Date performed"},
]


def _el(f):
    return json.loads(json.dumps(element(copy.deepcopy(f))))


def test_m1_a_field_without_the_key_is_written_as_before():
    assert _el(FIELDS[0]) == TEXTFIELD
    assert _el(FIELDS[1]) == TEXTAREA
    for f in FIELDS[2:]:
        assert _el(f)["properties"]["validator"] == NONE, f["type"]


@pytest.mark.parametrize("f", FIELDS, ids=[f["type"] for f in FIELDS])
def test_m2_the_key_writes_the_mark_and_nothing_else(f):
    marked = _el(dict(f, required_mark=True))
    plain = _el(f)
    assert marked["properties"]["validator"] == MARK
    marked["properties"]["validator"] = plain["properties"]["validator"]
    assert marked == plain


def test_m3_required_wins():
    assert _el(dict(FIELDS[1], required=True, required_mark=True))["properties"]["validator"] == REQUIRED
    assert _el(dict(FIELDS[1], required=True))["properties"]["validator"] == REQUIRED


def test_m4_a_number_keeps_its_validator():
    f = {"id": "amount", "type": "textfield", "label": "Amount", "storeNumeric": True}
    assert _el(dict(f, required_mark=True))["properties"]["validator"] == NUMBER
    assert _el(f)["properties"]["validator"] == NUMBER


def test_m5_a_named_validator_wins_over_the_mark():
    v = {"className": "org.joget.apps.form.lib.DefaultValidator",
         "properties": {"type": "custom", "custom-regex": "^(?!pending$).+$", "mandatory": "true",
                        "message": "Give the outcome of the contact."}}
    out = _el(dict(FIELDS[2], required_mark=True, props={"validator": v}))
    assert out["properties"]["validator"] == v


def test_m6_a_whole_form():
    spec = {"form": {"id": "actWriteOffReject", "name": "Reject - Write-off", "table": "wo_act"},
            "sections": [{"label": "Write-off", "columns": 1, "fields": copy.deepcopy(FIELDS)}],
            "require_guard": {"rules": "lc_action|reject|rejection_reason|Give the reason for rejecting it."}}
    plain = json.loads(json.dumps(build_form(copy.deepcopy(spec))))
    marked_spec = copy.deepcopy(spec)
    marked_spec["sections"][0]["fields"][1]["required_mark"] = True
    marked = json.loads(json.dumps(build_form(marked_spec)))
    els = marked["elements"][0]["elements"][0]["elements"]
    assert els[1]["properties"]["validator"] == MARK
    els[1]["properties"]["validator"] = NONE
    assert marked == plain
    assert plain["elements"][0]["elements"][0]["elements"][1] == TEXTAREA
    assert plain["properties"]["validator"] == {"className": "com.fiscaladmin.joget.requireguard.RequireGuard",
                                                "properties": {"rules": spec["require_guard"]["rules"]}}
