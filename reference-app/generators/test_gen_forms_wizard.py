"""gen_forms multipaged: the wizard keys (registry 0.9.10).

Driven by the deployed farmersPortal wizards (measured 2026-08-08): pages carry
parentSubFormId / subFormParentId / validate 'true'; the root carries partiallyStore
and storeMainFormOnPartiallyStore independently ('' vs 'true' on the farmer wizard),
and prevButtonlabel is authored ('Previous', not 'Prev').

The regression half is the contract of the 0.9.9->0.9.10 bump: a multipaged field
that carries NONE of the new keys (every detail_360 tabs element) emits properties
byte-identical to 0.9.9.

Run:  python3 -m pytest reference-app/generators/test_gen_forms_wizard.py -q
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_forms import element  # noqa: E402


def _mpf(**extra):
    f = {"id": "regWizard", "type": "multipaged",
         "pages": [{"label": "General", "formDefId": "frmBasicInfo"},
                   {"label": "Residency", "formDefId": "frmResidency"}]}
    f.update(extra)
    return f


def test_wizard_keys_are_emitted():
    f = _mpf(partiallyStore=True, storeMainFormOnPartiallyStore=True,
             ajaxMode=False, prevButtonlabel="Previous",
             pages=[{"label": "General", "formDefId": "frmBasicInfo",
                     "subFormParentId": "parent_id",
                     "parentSubFormId": "basic_data", "validate": True},
                    {"label": "Residency", "formDefId": "frmResidency",
                     "subFormParentId": "parent_id",
                     "parentSubFormId": "location_data", "validate": True}])
    p = element(f)["properties"]
    assert p["partiallyStore"] == "true"
    assert p["storeMainFormOnPartiallyStore"] == "true"
    assert p["ajaxMode"] == ""
    assert p["prevButtonlabel"] == "Previous"
    pages = p["numberOfPage"]["properties"]
    assert pages["page1_subFormParentId"] == "parent_id"
    assert pages["page1_parentSubFormId"] == "basic_data"
    assert pages["page1_validate"] == "true"
    assert pages["page2_parentSubFormId"] == "location_data"


def test_multipaged_without_the_new_keys_is_byte_identical_to_0_9_9():
    """The bump is a new degree of freedom, not a behaviour change - the exact
    0.9.9 emission for a detail_360-shaped field, asserted literally."""
    out = element(_mpf())
    assert out == {
        "className": "org.joget.plugin.enterprise.MultiPagedForm", "properties": {
            "id": "regWizard", "displayMode": "tab", "ajaxMode": "true",
            "partiallyStore": "", "storeMainFormOnPartiallyStore": "",
            "onlyAllowSubmitOnLastPage": "",
            "prevButtonlabel": "Prev", "nextButtonlabel": "Next", "css": "",
            "numberOfPage": {"className": "2", "properties": {
                "page1_label": "General", "page1_formDefId": "frmBasicInfo",
                "page1_readonly": "", "page1_readonlyLabel": "",
                "page1_validate": "", "page1_parentSubFormId": "",
                "page1_subFormParentId": "",
                "page2_label": "Residency", "page2_formDefId": "frmResidency",
                "page2_readonly": "", "page2_readonlyLabel": "",
                "page2_validate": "", "page2_parentSubFormId": "",
                "page2_subFormParentId": ""}}}}
