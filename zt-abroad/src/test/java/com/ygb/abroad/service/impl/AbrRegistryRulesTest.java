package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 官方高校名录核对规则
 *
 * @author ygb
 */
class AbrRegistryRulesTest
{
    @Test
    void normAndQuote()
    {
        assertEquals("university of nottingham", AbrRegistryRules.norm("University of Nottingham, the"));
        assertEquals("king's college london", AbrRegistryRules.norm("King’s College London"));
        String page = "Recognised bodies: University of Aberdeen Abertay University The University of Hong Kong; Queen’s University Belfast and Heriot-Watt University";
        assertEquals("University of Aberdeen", AbrRegistryRules.quoteIn(page, "University of Aberdeen"));
        assertEquals("The University of Hong Kong", AbrRegistryRules.quoteIn(page, "The University of Hong Kong"));
        assertEquals("Queen’s University Belfast", AbrRegistryRules.quoteIn(page, "Queen's University Belfast"));
        assertEquals("Heriot-Watt University", AbrRegistryRules.quoteIn(page, "Heriot-Watt University"));
        assertNull(AbrRegistryRules.quoteIn(page, "University of Dundee"));
        // 不能把 “Abertay University” 的一部分当成别的校名
        assertNull(AbrRegistryRules.quoteIn("Abertay University", "Tay University"));
    }

    @Test
    void ofs()
    {
        List<AbrRegistryRules.OfsRow> rows = List.of(
                new AbrRegistryRules.OfsRow("Imperial College of Science, Technology and Medicine", List.of("Imperial College London"), "10003270", "https://www.imperial.ac.uk", "l1"),
                new AbrRegistryRules.OfsRow("University of Durham", List.of(), "10007143", "https://www.durham.ac.uk", "l2"),
                new AbrRegistryRules.OfsRow("The University of Manchester", List.of(), "10007798", "https://www.manchester.ac.uk", "l3"),
                new AbrRegistryRules.OfsRow("Manchester Metropolitan University", List.of(), "10004180", "https://www.mmu.ac.uk", "l4"));
        assertEquals("10003270", AbrRegistryRules.findOfs(rows, "Imperial College London").ukprn());
        assertEquals("10007798", AbrRegistryRules.findOfs(rows, "University of Manchester").ukprn());
        assertNull(AbrRegistryRules.findOfs(rows, "Durham University"));
        assertEquals(List.of("A", "B"), AbrRegistryRules.splitNames("A\nNot applicable; B"));
    }
}
