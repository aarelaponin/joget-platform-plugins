package com.fiscaladmin.joget.approval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/** Unit tests for {@link MatrixValidator} — pure band-consistency checks. */
public class MatrixValidatorTest {

    private static Map<String, String> b(String level, String body, String quorum, String min, String max) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("level", level);
        m.put("bodyType", body);
        m.put("quorum", quorum);
        m.put("amountMin", min);
        m.put("amountMax", max);
        return m;
    }

    @Test
    public void contiguousValidBands_areValid() {
        List<Map<String, String>> bands = Arrays.asList(
                b("OFFICER", "SINGLE", "", "0", "1000"),
                b("MANAGER", "SINGLE", "", "1000.01", ""));
        MatrixValidator.Result r = MatrixValidator.validate(bands, null);
        assertTrue(r.issues.toString(), r.valid);
    }

    @Test
    public void overlappingBands_flagged() {
        List<Map<String, String>> bands = Arrays.asList(
                b("OFFICER", "SINGLE", "", "0", "2000"),
                b("MANAGER", "SINGLE", "", "1000", "3000"));
        MatrixValidator.Result r = MatrixValidator.validate(bands, null);
        assertFalse(r.valid);
        assertTrue(r.issues.toString().contains("overlap"));
    }

    @Test
    public void gapBetweenBands_flagged() {
        List<Map<String, String>> bands = Arrays.asList(
                b("OFFICER", "SINGLE", "", "0", "1000"),
                b("MANAGER", "SINGLE", "", "5000", "9000"));
        MatrixValidator.Result r = MatrixValidator.validate(bands, null);
        assertFalse(r.valid);
        assertTrue(r.issues.toString().contains("gap"));
    }

    @Test
    public void unknownLevel_flagged() {
        MatrixValidator.Result r = MatrixValidator.validate(
                new ArrayList<Map<String, String>>(Arrays.asList(b("BOSS", "SINGLE", "", "0", ""))), null);
        assertFalse(r.valid);
        assertTrue(r.issues.toString().contains("unknown level"));
    }

    @Test
    public void chainMustBeAscending() {
        MatrixValidator.Result r = MatrixValidator.validate(
                new ArrayList<Map<String, String>>(Arrays.asList(
                        b("MANAGER,OFFICER", "CHAIN", "", "0", ""))), null);
        assertFalse(r.valid);
        assertTrue(r.issues.toString().contains("ascending"));
    }

    @Test
    public void collegialNeedsQuorumAtLeastTwo() {
        MatrixValidator.Result r = MatrixValidator.validate(
                new ArrayList<Map<String, String>>(Arrays.asList(
                        b("MANAGER", "COLLEGIAL", "1", "0", ""))), null);
        assertFalse(r.valid);
        assertTrue(r.issues.toString().contains("quorum"));
    }

    @Test
    public void emptyBands_flagged() {
        assertFalse(MatrixValidator.validate(new ArrayList<Map<String, String>>(), null).valid);
    }
}
