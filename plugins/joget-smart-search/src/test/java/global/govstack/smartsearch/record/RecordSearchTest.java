package global.govstack.smartsearch.record;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The search of a record the element's settings name, over an invented register held in an
 * in-memory database: found exactly by its number, or by a fragment of its name; a result that
 * tells two of the same name apart; the fields a choice fills; the words when nothing is found.
 * Nothing here names a domain: a second register, of vessels, is searched by the same code.
 */
public class RecordSearchTest {

    private static DataSource db;

    @BeforeClass
    public static void register() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:record_search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        db = h2;
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE app_fd_person (id VARCHAR(40) PRIMARY KEY, c_number VARCHAR(20),"
                    + " c_name VARCHAR(100), c_trade_name VARCHAR(100), c_kind VARCHAR(20))");
            s.execute("INSERT INTO app_fd_person VALUES"
                    + " ('p1', '100000001', 'Ana Petrescu', '', 'natural'),"
                    + " ('p2', '100000002', 'Ana Petrescu', 'Petrescu Bakery', 'legal'),"
                    + " ('p3', '100000003', 'Ion Vasile', '', 'natural'),"
                    + " ('p4', '100000004', '50% Discount Stores', '', 'legal'),"
                    + " ('p5', '100000005', 'Route 50 Traders', '', 'legal'),"
                    + " ('p6', '100000006', 'Oak_Tree Partners', '', 'legal'),"
                    + " ('p7', '100000007', 'Oak Tree Partners', '', 'legal')");
            StringBuilder many = new StringBuilder("INSERT INTO app_fd_person VALUES ");
            for (int i = 0; i < 25; i++) {
                many.append(i == 0 ? "" : ", ").append("('m").append(i).append("', '2000000")
                    .append(String.format("%02d", i)).append("', 'Maria Munteanu ").append(i)
                    .append("', '', 'natural')");
            }
            s.execute(many.toString());
            s.execute("CREATE TABLE app_fd_vessel (id VARCHAR(40) PRIMARY KEY, c_hull VARCHAR(20),"
                    + " c_vessel_name VARCHAR(100), c_port VARCHAR(40))");
            s.execute("INSERT INTO app_fd_vessel VALUES ('v1', 'HN-0042', 'Northern Star', 'Harbour A'),"
                    + " ('v2', 'HN-0043', 'Northern Light', 'Harbour B')");
        }
    }

    /** The settings an application's model would give the element for its register of persons. */
    static Map<String, Object> personSettings() {
        Map<String, Object> p = new HashMap<>();
        p.put("recordTable", "app_fd_person");
        p.put("exactColumn", "c_number");
        p.put("fragmentColumn", "c_name");
        p.put("storeValue", "c_number");
        p.put("resultColumns", new Object[]{
            column("c_number", "Identification number"), column("c_name", "Name"),
            column("c_trade_name", "Trading name"), column("c_kind", "Kind of person")});
        p.put("populate", "c_name:shown_name,c_kind:shown_kind");
        p.put("locks", "shown_name,shown_kind");
        p.put("entityLabel", "person");
        p.put("notFound", "No person is registered under {searched}. Check the number against the"
                + " document it came from, or give it again.");
        return p;
    }

    static Map<String, String> column(String column, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("column", column);
        m.put("label", label);
        return m;
    }

    private static RecordSearch.Result search(Map<String, Object> props, String term) throws Exception {
        RecordSearchSettings s = RecordSearchSettings.from(props::get);
        assertTrue("the settings name a record: " + s.problems(), s.problems().isEmpty());
        return new RecordSearch(s, db).search(term);
    }

    @Test
    public void theNumberFindsTheOneRecordItNames() throws Exception {
        RecordSearch.Result r = search(personSettings(), " 100000003 ");
        assertEquals("exact", r.how);
        assertEquals(1, r.rows.size());
        assertEquals("100000003", r.rows.get(0).value);
        assertEquals("Ion Vasile", r.rows.get(0).label);
        assertNull(r.message);
    }

    @Test
    public void aFragmentOfTheNameFindsEveryRecordItIsPartOf() throws Exception {
        RecordSearch.Result r = search(personSettings(), "petrESCU");
        assertEquals("fragment", r.how);
        assertEquals(2, r.rows.size());
        assertEquals(Arrays.asList("100000001", "100000002"),
                Arrays.asList(r.rows.get(0).value, r.rows.get(1).value));
    }

    @Test
    public void theResultTellsTwoOfTheSameNameApartByTheColumnsTheSettingsName() throws Exception {
        RecordSearch.Result r = search(personSettings(), "Ana Petrescu");
        assertEquals(2, r.rows.size());
        RecordSearch.Row a = r.rows.get(0), b = r.rows.get(1);
        assertEquals(a.label, b.label);                                  // the same name
        assertEquals(Arrays.asList("100000001", "Ana Petrescu", "", "natural"), a.cells);
        assertEquals(Arrays.asList("100000002", "Ana Petrescu", "Petrescu Bakery", "legal"), b.cells);
        assertNotEquals(a.cells, b.cells);                               // told apart
        assertEquals(Arrays.asList("Identification number", "Name", "Trading name", "Kind of person"),
                RecordSearchSettings.from(personSettings()::get).columnLabels());
    }

    @Test
    public void theChoiceFillsTheFieldsTheSettingsNameFromTheRecordChosen() throws Exception {
        RecordSearch.Row row = search(personSettings(), "100000002").rows.get(0);
        Map<String, String> want = new LinkedHashMap<>();
        want.put("shown_name", "Ana Petrescu");
        want.put("shown_kind", "legal");
        assertEquals(want, row.fills);
        assertEquals(Arrays.asList("shown_name", "shown_kind"),
                RecordSearchSettings.from(personSettings()::get).locks());
    }

    @Test
    public void nothingFoundIsSaidInTheSettingsWordsWithWhatWasSearched() throws Exception {
        RecordSearch.Result r = search(personSettings(), "999999999");
        assertTrue(r.rows.isEmpty());
        assertEquals("none", r.how);
        assertEquals("No person is registered under 999999999. Check the number against the"
                + " document it came from, or give it again.", r.message);
        assertEquals("{\"rows\":[],\"more\":false,\"how\":\"none\",\"message\":\"No person is registered"
                + " under 999999999. Check the number against the document it came from, or give it"
                + " again.\"}", r.toJson());
    }

    @Test
    public void theAnswerIsWrittenAsThePageReadsIt() throws Exception {
        assertEquals("{\"rows\":[{\"value\":\"100000003\",\"label\":\"Ion Vasile\",\"cells\":[\"100000003\","
                + "\"Ion Vasile\",\"\",\"natural\"],\"fills\":{\"shown_name\":\"Ion Vasile\",\"shown_kind\":"
                + "\"natural\"}}],\"more\":false,\"how\":\"exact\"}",
                search(personSettings(), "100000003").toJson());
        assertEquals("\"a\\\"b\\\\c\\n\\u003c/script\\u003e\"", Json.str("a\"b\\c\n</script>"));
    }

    @Test
    public void withoutWordsOfItsOwnTheSearchSaysPlainlyThatNothingWasFound() throws Exception {
        Map<String, Object> p = personSettings();
        p.remove("notFound");
        assertEquals("Nothing was found for zzz.", search(p, "zzz").message);
    }

    @Test
    public void aWildcardTheOfficerTypesIsSearchedAsItself() throws Exception {
        RecordSearch.Result pct = search(personSettings(), "50%");
        assertEquals(1, pct.rows.size());
        assertEquals("50% Discount Stores", pct.rows.get(0).label);
        RecordSearch.Result under = search(personSettings(), "Oak_Tree");
        assertEquals(1, under.rows.size());
        assertEquals("Oak_Tree Partners", under.rows.get(0).label);
    }

    @Test
    public void moreRecordsThanAreShownAreSaidToBeMore() throws Exception {
        RecordSearch.Result r = search(personSettings(), "Maria Munteanu");
        assertEquals(RecordSearchSettings.MAX_ROWS, r.rows.size());
        assertTrue(r.more);
        assertFalse(search(personSettings(), "Ana Petrescu").more);
    }

    @Test
    public void anotherRecordNeedsNoChangeToTheElement() throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("recordTable", "app_fd_vessel");
        p.put("exactColumn", "c_hull");
        p.put("fragmentColumn", "c_vessel_name");
        p.put("resultColumns", new Object[]{column("c_hull", "Hull number"),
                column("c_vessel_name", "Vessel"), column("c_port", "Home port")});
        p.put("populate", "c_port:port");
        RecordSearch.Result exact = search(p, "HN-0043");
        assertEquals(1, exact.rows.size());
        assertEquals("HN-0043", exact.rows.get(0).value);             // stores the exact column
        assertEquals(Arrays.asList("HN-0043", "Northern Light", "Harbour B"), exact.rows.get(0).cells);
        assertEquals("Harbour B", exact.rows.get(0).fills.get("port"));
        assertEquals(2, search(p, "northern").rows.size());
        assertEquals("Nothing was found for Southern.", search(p, "Southern").message);
    }

    @Test
    public void aNameThatIsNotAPlainIdentifierIsRefusedBeforeAnySearch() {
        for (String[] bad : new String[][]{{"recordTable", "app_fd_person; DROP TABLE x"},
                {"exactColumn", "c_number OR 1=1"}, {"fragmentColumn", "LOWER(c_name)"},
                {"populate", "c_name--:shown_name"}}) {
            Map<String, Object> p = personSettings();
            p.put(bad[0], bad[1]);
            List<String> problems = RecordSearchSettings.from(p::get).problems();
            assertFalse(bad[0] + " " + bad[1] + " was not refused", problems.isEmpty());
        }
        Map<String, Object> noExact = personSettings();
        noExact.remove("exactColumn");
        noExact.remove("fragmentColumn");
        assertFalse(RecordSearchSettings.from(noExact::get).problems().isEmpty());
    }

    @Test
    public void theSettingsTravelToTheServiceAndBackUnchanged() {
        RecordSearchSettings s = RecordSearchSettings.from(personSettings()::get);
        RecordSearchSettings back = RecordSearchSettings.fromText(s.toText());
        assertEquals(s, back);
        assertEquals(s.toText(), back.toText());
        assertTrue(back.isRecordMode());
        assertEquals(s.columnLabels(), back.columnLabels());
        assertEquals(s.notFoundWords("7"), back.notFoundWords("7"));
    }

    @Test
    public void withNoRecordNamedTheElementIsNotInRecordMode() {
        assertFalse(RecordSearchSettings.from(new HashMap<String, Object>()::get).isRecordMode());
        Map<String, Object> farmer = new HashMap<>();
        farmer.put("apiEndpoint", "/jw/api/fss");
        farmer.put("populate", "firstName:first_name");
        assertFalse(RecordSearchSettings.from(farmer::get).isRecordMode());
    }
}
