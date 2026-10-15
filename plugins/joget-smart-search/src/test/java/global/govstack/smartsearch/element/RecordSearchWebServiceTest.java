package global.govstack.smartsearch.element;

import global.govstack.smartsearch.record.RecordSearchSettings;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The service the element's page asks: it searches only with settings this server sealed when it
 * drew the form, never with settings the page sends of its own; and it answers in the shape the
 * page reads.
 */
public class RecordSearchWebServiceTest {

    private static DataSource db;
    private static String sealed;

    @BeforeClass
    public static void register() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:record_service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        db = h2;
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE app_fd_holder (id VARCHAR(40) PRIMARY KEY, c_code VARCHAR(20),"
                    + " c_name VARCHAR(100))");
            s.execute("INSERT INTO app_fd_holder VALUES ('h1', 'A-1', 'First Holder'),"
                    + " ('h2', 'A-2', 'Second Holder')");
        }
        Map<String, Object> p = new HashMap<>();
        p.put("recordTable", "app_fd_holder");
        p.put("exactColumn", "c_code");
        p.put("fragmentColumn", "c_name");
        Map<String, String> col = new LinkedHashMap<>();
        col.put("column", "c_name");
        col.put("label", "Name");
        p.put("resultColumns", new Object[]{col});
        p.put("populate", "c_name:holder_name");
        sealed = RecordSearchSettings.from(p::get).toText();
    }

    /** A seal that opens only the one token this "server" issued. */
    private static final RecordSearchWebService.Seal SEAL =
            (token, nonce) -> "T1".equals(token) && "N1".equals(nonce) ? sealed : null;

    @Test
    public void aSearchTheServerDidNotSealIsRefused() {
        RecordSearchWebService.Answer a = RecordSearchWebService.answer("forged", "N1", "A-1", SEAL, () -> db);
        assertEquals(403, a.status);
        assertTrue(a.body, a.body.startsWith("{\"error\":"));
        assertEquals(403, RecordSearchWebService.answer(null, null, "A-1", SEAL, () -> db).status);
        // settings the page makes up are never read: only what the seal opens is
        assertEquals(403, RecordSearchWebService.answer(sealed, "N1", "A-1", SEAL, () -> db).status);
    }

    @Test
    public void aSealedSearchIsAnsweredWithTheRowsInTheShapeThePageReads() {
        RecordSearchWebService.Answer a = RecordSearchWebService.answer("T1", "N1", "holder", SEAL, () -> db);
        assertEquals(200, a.status);
        assertEquals("{\"rows\":[{\"value\":\"A-1\",\"label\":\"First Holder\",\"cells\":[\"First Holder\"],"
                + "\"fills\":{\"holder_name\":\"First Holder\"}},{\"value\":\"A-2\",\"label\":\"Second Holder\","
                + "\"cells\":[\"Second Holder\"],\"fills\":{\"holder_name\":\"Second Holder\"}}],\"more\":false,"
                + "\"how\":\"fragment\"}", a.body);
    }

    @Test
    public void nothingToSearchForIsSaidAndNothingIsSearched() {
        RecordSearchWebService.Answer a = RecordSearchWebService.answer("T1", "N1", "  ", SEAL, () -> {
            throw new AssertionError("the database was asked with nothing to search for");
        });
        assertEquals(400, a.status);
        assertTrue(a.body, a.body.startsWith("{\"error\":"));
    }

    @Test
    public void nothingFoundIsAnsweredWithTheWordsNotWithAnError() {
        RecordSearchWebService.Answer a = RecordSearchWebService.answer("T1", "N1", "Z-9", SEAL, () -> db);
        assertEquals(200, a.status);
        assertTrue(a.body, a.body.endsWith("\"message\":\"Nothing was found for Z-9.\"}"));
    }
}
