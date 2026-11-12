package com.fiscaladmin.joget.lists;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.junit.Assert.*;

/**
 * Every plugin class of this bundle reports the bundle's version, as the library's convention holds it
 * (METHOD-2026-11-05-67, R40, joget-decision-approval): the test reads the pom's own version (the {@code version} element
 * that is a child of {@code project} in the pom.xml of the module being built), then every class the build compiled into
 * {@code target/classes}, and for each that has a public {@code String getVersion()} makes an instance by its no-argument
 * constructor and asks it. It fails naming each class whose answer is not the pom's version.
 */
public class EveryClassReportsItsBundleVersionTest {

    private static File basedir() {
        String b = System.getProperty("basedir");
        return new File(b != null ? b : System.getProperty("user.dir")).getAbsoluteFile();
    }

    /** The version of the pom at {@code pom}: its {@code project/version} element, not a parent's or a dependency's. */
    static String pomVersion(File pom) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Element project = f.newDocumentBuilder().parse(pom).getDocumentElement();
        for (Node n = project.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && "version".equals(n.getNodeName())) {
                return n.getTextContent().trim();
            }
        }
        return "";
    }

    @Test
    public void everyClassWithAGetVersionReportsThePomsVersion() throws Exception {
        File base = basedir();
        String pom = pomVersion(new File(base, "pom.xml"));
        assertTrue("the pom's own version must be read as x.y.z, it read '" + pom + "'",
                pom.matches("\\d+\\.\\d+\\.\\d+"));
        File classes = new File(base, "target/classes");
        assertTrue("no compiled classes at " + classes + " (the test reads what the build compiled)",
                classes.isDirectory());

        List<String> names = new ArrayList<String>();
        try (Stream<Path> walk = Files.walk(classes.toPath())) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".class")).collect(Collectors.toList())) {
                String rel = classes.toPath().relativize(p).toString().replace(File.separatorChar, '.');
                String name = rel.substring(0, rel.length() - ".class".length());
                if (!name.endsWith("module-info") && !name.endsWith("package-info")) {
                    names.add(name);
                }
            }
        }
        java.util.Collections.sort(names);

        List<String> checked = new ArrayList<String>();
        List<String> wrong = new ArrayList<String>();
        for (String name : names) {
            Class<?> c = Class.forName(name, false, EveryClassReportsItsBundleVersionTest.class.getClassLoader());
            if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
                continue;
            }
            Method m;
            try {
                m = c.getMethod("getVersion");
            } catch (NoSuchMethodException e) {
                continue;
            }
            if (m.getReturnType() != String.class || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            String reported;
            try {
                Constructor<?> k = c.getDeclaredConstructor();
                k.setAccessible(true);
                reported = (String) m.invoke(k.newInstance());
            } catch (Exception e) {
                wrong.add(name + " has a getVersion and could not be asked: " + e);
                continue;
            }
            checked.add(name + "=" + reported);
            if (!pom.equals(reported)) {
                wrong.add(name + " reports \"" + reported + "\", the pom's version is \"" + pom + "\"");
            }
        }
        System.out.println("EveryClassReportsItsBundleVersionTest: pom version " + pom + "; "
                + checked.size() + " class(es) with a getVersion checked: " + checked);
        assertTrue("at least the formatter is checked", checked.size() >= 1);
        assertTrue(wrong.size() + " class(es) do not report the bundle's version: " + wrong, wrong.isEmpty());
    }
}
