"""gen_forms: a smart search that names its record carries no API setting (METHOD-2026-09-28-07,
item 6; the search round's report, section 7, item 3).

joget-smart-search 8.2.0 searches the record its properties name (`recordTable`, written by the kit
through `props`) through its own sealed service, and reads no API definition, identifier or key. The
generator wrote the farmer search's `apiEndpoint`, `apiId` and an empty `apiKey` on every smart
search, and the kit's lint A003 read the empty key as a literal secret. They are written only where
no record is named; there, the farmer search is emitted as it was.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_forms import element  # noqa: E402

API = {"apiEndpoint", "apiId", "apiKey"}


def _search(props=None):
    f = {"id": "holder_code", "type": "smart_search", "label": "Holder",
         "config": {"entityLabel": "holder", "populate": "name:shown_name"}}
    if props:
        f["props"] = props
    return element(f)["properties"]


def test_a_search_that_names_its_record_carries_no_api_setting():
    p = _search({"recordTable": "app_fd_t_holder", "exactColumn": "c_code", "storeValue": "c_code",
                 "populate": "c_name:shown_name", "locks": "shown_name"})
    assert not API & set(p), sorted(p)
    assert p["recordTable"] == "app_fd_t_holder" and p["locks"] == "shown_name"


def test_a_search_that_names_no_record_keeps_the_farmer_settings():
    p = _search()
    assert API <= set(p) and (p["apiEndpoint"], p["apiId"], p["apiKey"]) == ("", "", "")
    # the order of the farmer search's properties is as it was: the settings after displayColumns
    keys = list(p)
    assert keys.index("apiEndpoint") == keys.index("displayColumns") + 1
    assert keys.index("nationalIdPattern") == keys.index("apiKey") + 1
