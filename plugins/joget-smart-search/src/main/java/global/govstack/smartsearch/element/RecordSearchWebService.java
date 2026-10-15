package global.govstack.smartsearch.element;

import global.govstack.smartsearch.record.Json;
import global.govstack.smartsearch.record.RecordSearch;
import global.govstack.smartsearch.record.RecordSearchSettings;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * The service the smart search element's page asks when the element searches a record its
 * properties name: {@code /jw/web/json/plugin/global.govstack.smartsearch.element.RecordSearchWebService/service}
 * with {@code _s} (the sealed settings), {@code _n} (the nonce) and {@code q} (what was typed).
 *
 * <p>The settings are the element's, sealed by this server when it drew the form
 * ({@link #seal(String)}): encrypted, and bound to a nonce the platform issued. The service reads
 * settings only by opening that seal, so a page cannot make it search a table or a column of its
 * own choosing. It needs no API definition, API identifier or API key.
 */
public class RecordSearchWebService extends ExtDefaultPlugin implements PluginWebSupport {

    private static final String CLASS_NAME = RecordSearchWebService.class.getName();
    static final String NONCE_NAME = "RecordSearch";
    /** How long a drawn form's search stays usable, in hours. */
    static final int NONCE_HOURS = 4;

    /** Opens the settings a server sealed; null when this server did not seal them, or the seal
     *  has expired. */
    public interface Seal {
        String open(String token, String nonce);
    }

    /** An answer: its status and its body, JSON. */
    public static final class Answer {
        public final int status;
        public final String body;

        Answer(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** The platform's own seal: its encryption and its nonces. */
    static final Seal PLATFORM = (token, nonce) ->
            SecurityUtil.verifyNonce(nonce, new String[]{NONCE_NAME, token}) ? SecurityUtil.decrypt(token) : null;

    @Override
    public String getName() {
        return "Record Search Service";
    }

    @Override
    public String getVersion() {
        return "8.2.0";
    }

    @Override
    public String getDescription() {
        return "Answers the smart search element's page when the element searches a record its properties name";
    }

    /** Seal the settings for the page: [the token, the nonce]. Needs the running platform. */
    public static String[] seal(String settingsText) {
        String token = SecurityUtil.encrypt(settingsText);
        String nonce = SecurityUtil.generateNonce(new String[]{NONCE_NAME, token}, NONCE_HOURS);
        return new String[]{token, nonce};
    }

    /** The answer to a request, from the sealed settings and what was typed. */
    public static Answer answer(String token, String nonce, String typed, Seal seal, Supplier<DataSource> ds) {
        String text = null;
        if (token != null && !token.isEmpty() && nonce != null && !nonce.isEmpty()) {
            try {
                text = seal.open(token, nonce);
            } catch (RuntimeException e) {
                text = null;
            }
        }
        if (text == null) {
            return new Answer(403, error("This search was not issued by this server, or it has expired."
                    + " Open the form again."));
        }
        RecordSearchSettings s = RecordSearchSettings.fromText(text);
        if (!s.isRecordMode() || !s.problems().isEmpty()) {
            return new Answer(500, error("The search is not set up: " + String.join("; ", s.problems())));
        }
        String term = typed == null ? "" : typed.trim();
        if (term.isEmpty()) {
            return new Answer(400, error("Nothing to search for."));
        }
        try {
            return new Answer(200, new RecordSearch(s, ds.get()).search(term).toJson());
        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "The record search failed on " + s.table());
            return new Answer(500, error("The search could not be made."));
        }
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Answer a = answer(request.getParameter("_s"), request.getParameter("_n"), request.getParameter("q"),
                PLATFORM, () -> (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource"));
        response.setStatus(a.status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(a.body);
    }

    private static String error(String words) {
        return Json.of(Collections.singletonMap("error", words));
    }
}
