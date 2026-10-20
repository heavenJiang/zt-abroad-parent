package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrSchool;

/**
 * M2-09 / M2-12：存档与历史的取值比较、院校建档规则
 *
 * @author ygb
 */
class AbrSourceArchiveRulesTest
{
    @Test
    void valueText()
    {
        assertEquals("6.5", AbrSourceArchiveServiceImpl.text(new BigDecimal("6.50")));
        assertNull(AbrSourceArchiveServiceImpl.text("  "));
        assertEquals("{\"a\":1,\"b\":[1,2]}", AbrSourceArchiveServiceImpl.text("{ \"a\": 1, \"b\": [1, 2] }"));
        assertTrue(AbrSourceArchiveServiceImpl.same("{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}"));
        assertFalse(AbrSourceArchiveServiceImpl.same("1", "2"));
        assertFalse(AbrSourceArchiveServiceImpl.same(null, "2"));
        assertEquals(64, AbrSourceArchiveServiceImpl.sha256("abc").length());
    }

    @Test
    void onboardRules()
    {
        assertTrue(AbrSchoolOnboardRules.sameSchool("Columbia University", "Columbia University in the City of New York"));
        assertTrue(AbrSchoolOnboardRules.sameSchool("Texas A&M University", "Texas A&M University-College Station"));
        assertTrue(AbrSchoolOnboardRules.sameSchool("The Ohio State University", "Ohio State University-Main Campus"));
        assertFalse(AbrSchoolOnboardRules.sameSchool("University of Washington", "Washington University in St Louis"));
        assertTrue(AbrSchoolOnboardRules.sameSchool("Virginia Tech", "Virginia Polytechnic Institute and State University", "Virginia Tech | VT"));
        assertFalse(AbrSchoolOnboardRules.sameSchool("Virginia Tech", "Virginia Polytechnic Institute and State University", ""));
        assertEquals("https://www.aamu.edu/", AbrSchoolOnboardRules.website("www.aamu.edu/", ""));
        assertEquals("https://www.mit.edu/", AbrSchoolOnboardRules.website("http://www.mit.edu", ""));
        assertEquals("https://x.edu/", AbrSchoolOnboardRules.website("", "https://x.edu/"));
        assertTrue(AbrSchoolOnboardRules.sameHost("https://home.dartmouth.edu/", "https://www.dartmouth.edu"));
        assertFalse(AbrSchoolOnboardRules.sameHost("https://bloomington.iu.edu/", "https://www.indiana.edu/"));
        assertEquals(List.of("Columbia University in the City of New York", "Columbia"),
                AbrSchoolOnboardRules.aliases("Columbia University", "Columbia University in the City of New York", "Columbia, columbia university"));
        String body = "{\"results\":[{\"school.name\":\"A\",\"school.school_url\":\"www.a.edu\\/\",\"id\":1},{\"school.name\":\"B\",\"id\":22}]}";
        assertEquals("{\"school.name\":\"A\",\"school.school_url\":\"www.a.edu\\/\",\"id\":1}", AbrSchoolOnboardRules.rawRecord(body, "1"));
        assertEquals("{\"school.name\":\"B\",\"id\":22}", AbrSchoolOnboardRules.rawRecord(body, "22"));
        assertNull(AbrSchoolOnboardRules.rawRecord(body, "2"));
        assertEquals("{\"x\":\"1\",\"ipeds\":\"190150\"}", AbrSchoolOnboardRules.extIds("{\"x\":\"1\"}", "ipeds", "190150"));
        AbrSchool a = new AbrSchool();
        a.setSchoolName("A");
        a.setAliases("[\"x\"]");
        AbrSchool b = new AbrSchool();
        b.setSchoolName("A");
        b.setAliases("[ \"x\" ]");
        assertFalse(AbrSchoolOnboardRules.changed(a, b));
        b.setWebsite("https://a.edu/");
        assertTrue(AbrSchoolOnboardRules.changed(a, b));
    }
}
