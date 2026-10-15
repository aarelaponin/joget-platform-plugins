"""A row act drawn only on a row whose record column holds a value (METHOD-2026-10-09-05, item 8;
the debt management engagement's LS-99).

A row act that carries the record a column of the row names was drawn on every row; on a row whose
column is empty it opened "Record cannot be found". The spec's `shownWhen: <column>` on a row action
writes the platform's own rule on a row action (`rules`, read by DataListDecorator
.isRowActionVisible, jw-community 8 and 9): the act is shown where the column is not empty
(operator `<>`, value ''; the platform's `IS NOT EMPTY` holds on every row, since it reads a
missing value as ''). Absent the key, the row action is emitted exactly as before.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gen_datalists as g


def test_a_row_act_shown_when_its_column_holds_a_value():
    ra = g.row_action(1, {"label": "Record the telephone contact", "to": "lsCaseActions_crud",
                          "hrefColumn": "telephone_contact", "hrefParam": "id",
                          "shownWhen": "telephone_contact"})
    assert ra["properties"]["rules"] == [
        {"join": "AND", "field": "telephone_contact", "operator": "<>", "value": ""}]
    assert ra["properties"]["hrefColumn"] == "telephone_contact"


def test_a_row_act_without_the_key_is_as_before():
    ra = g.row_action(0, {"label": "Open", "to": "lsMine_crud", "hrefColumn": "id",
                          "hrefParam": "id"})
    assert "rules" not in ra["properties"]
    assert set(ra["properties"]) == {"label", "href", "hrefColumn", "hrefParam", "target",
                                     "confirmation"}
