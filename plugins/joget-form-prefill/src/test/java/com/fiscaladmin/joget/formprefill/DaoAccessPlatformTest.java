package com.fiscaladmin.joget.formprefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.joget.apps.form.dao.FormDataDaoImpl;
import org.joget.apps.form.model.FormColumnCache;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;

/**
 * The data access run against the platform's own data layer, not a fake: {@link FormDataDaoImpl} from the
 * platform's {@code wflow-core}, the class the plugin is handed on the server, builds its Hibernate mapping of a
 * form's table, creates the table and runs its own queries, here on an in-memory H2 database.
 *
 * <p>What stands in for the server is only what the platform reads from its running context: the data source
 * (the server's {@code setupDataSource} bean), the folder for the mapping files, the caches, and the list of a
 * form's fields, which the platform reads from the app's form definitions. Every query, the mapping and the
 * reading of rows back into {@link FormRow} are the platform's.
 *
 * <p>The case is the one that failed on the platform with version 1.0.0: a match on {@code id}, a row's key,
 * which is no custom property. Version 1.0.0 asked for {@code WHERE e.customProperties.id = ?1}.
 */
public class DaoAccessPlatformTest {

    private static final String FORM = "m03";
    private static final String KEY = "7e6eb709-28a9";
    private static final List<String> FIELDS = Arrays.asList("name", "kind");

    private static CacheManager caches;
    private static File mappingDir;
    private static FormDataDaoImpl dao;

    @BeforeClass
    public static void platformDataLayerOnH2() throws Exception {
        // Hibernate finds the platform's transaction manager (Bitronix) on the class path and starts it;
        // without this it writes its two journal files, btm1.tlog and btm2.tlog, into the module's folder
        System.setProperty("bitronix.tm.journal", "null");
        final JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:prefill_platform_test;DB_CLOSE_DELAY=-1");
        mappingDir = Files.createTempDirectory("prefill-hbm").toFile();

        caches = new CacheManager(new net.sf.ehcache.config.Configuration().name("prefill-platform-test"));
        Cache sf = new Cache(new CacheConfiguration("sf", 100));
        Cache join = new Cache(new CacheConfiguration("join", 100));
        Cache pc = new Cache(new CacheConfiguration("pc", 100));
        caches.addCache(sf);
        caches.addCache(join);
        caches.addCache(pc);

        dao = new FormDataDaoImpl() {
            @Override
            protected MetadataSources createMetadataSources() {
                // as the platform's own method, with the server's setupDataSource bean replaced by the H2 source
                Configuration configuration = new Configuration();
                configuration.setProperty("show_sql", "false");
                configuration.getProperties().put(Environment.DATASOURCE, ds);
                configuration.getProperties().put(Environment.ALLOW_UPDATE_OUTSIDE_TRANSACTION, true);
                return new MetadataSources(new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties()).build());
            }

            @Override
            protected String getFormMappingPath() {
                return mappingDir.getPath();
            }

            @Override
            public Collection<String> getFormDefinitionColumnNames(String tableName) {
                return FIELDS; // on the server: the fields of the app's forms mapped to this table
            }

            @Override
            public Collection<String> syncFormDefinitionColumnNamesCache(String tableName) {
                return FIELDS;
            }

            @Override
            protected String getSessionFactoryCacheKey(String entityName, String tableName, FormRowSet rowSet) {
                return "test;" + entityName + ";" + tableName + ";" + (rowSet == null || rowSet.isEmpty() ? "" : rowSet.get(0).keySet());
            }

            @Override
            protected String getPersistentClassCacheKey(String entityName) {
                return "test;PC:" + entityName + ";";
            }
        };
        dao.setFormSessionFactoryCache(sf);
        dao.setJoinFormSessionFactoryCache(join);
        dao.setFormPersistentClassCache(pc);
        dao.setFormColumnCache(mock(FormColumnCache.class)); // no indexes

        // two rows, stored by the platform's own save
        FormRowSet rows = new FormRowSet();
        rows.add(row(KEY, "The Institute", "college"));
        rows.add(row("a1b2c3d4-0000", "The Academy", "college"));
        dao.saveOrUpdate(FORM, FORM, rows);
    }

    @AfterClass
    public static void close() {
        if (caches != null) caches.shutdown();
        File[] written = mappingDir == null ? null : mappingDir.listFiles();
        if (written != null) for (File f : written) f.delete();
        if (mappingDir != null) mappingDir.delete();
    }

    private static FormRow row(String id, String name, String kind) {
        FormRow r = new FormRow();
        r.setId(id);
        r.setProperty("name", name);
        r.setProperty("kind", kind);
        return r;
    }

    @Test
    public void matchOnId_readsTheRowByItsKey_onThePlatformsDataLayer() {
        DataAccess access = new FormPrefillLoadBinder.DaoAccess(dao);

        List<Map<String, String>> found = access.findByField(FORM, FORM, "id", KEY);
        assertEquals("the one row whose key is the id", 1, found.size());
        assertEquals(KEY, found.get(0).get("id"));
        assertEquals("The Institute", found.get(0).get("name"));
        assertEquals("college", found.get(0).get("kind"));

        assertTrue("no row for an id that is not there", access.findByField(FORM, FORM, "id", "no-such-id").isEmpty());
    }
}
