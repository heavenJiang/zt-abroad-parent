package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 学校英语等级说明页解析（原句取自 UCL、KCL 官方页面）
 *
 * @author ygb
 */
class AbrLanguageBandRulesTest
{
    @Test
    void uclInline()
    {
        String t = String.join("\n", "Pearson Test of English Academic (PTE Academic)", "Level 1 : Overall score of 65% and a minimum of 60% in each component",
                "International English Language Testing System (IELTS) Academic Version", "Level 1 : Overall score of 6.5 and a minimum of 6.0 in each component",
                "Level 2 : Overall score of 7.0 and a minimum of 6.5 in each component", "Level 3 : Overall score of 7.0 and a minimum of 7.0 in each component",
                "TOEFL iBT", "Level 1 : Overall score of 92 with ...");
        Map<String, AbrLanguageBandRules.Band> m = AbrLanguageBandRules.parse(t, "inline", "IELTS", "Level [1-5]");
        assertEquals(3, m.size());
        assertEquals("6.5", m.get("Level 1").overall().toPlainString());
        assertEquals("6.0", m.get("Level 1").minSub().toPlainString());
        assertEquals("7.0", m.get("Level 3").minSub().toPlainString());
        assertEquals("Level 2 : Overall score of 7.0 and a minimum of 6.5 in each component", m.get("Level 2").quote());
    }

    @Test
    void kclSection()
    {
        String t = String.join("\n", "Band B", "This band applies courses in the following faculties:", "IELTS (Academic)",
                "7.0 overall with a minimum of 6.5 in each skill", "IELTS (Online)", "7.0 overall with a minimum of 6.5 in each skill", "Band D",
                "Cambridge C2 Proficiency", "185 overall", "IELTS (Academic)", "6.5 overall with a minimum of 6.0 in reading & writing and 5.5 in listening & speaking");
        Map<String, AbrLanguageBandRules.Band> m = AbrLanguageBandRules.parse(t, "section", "IELTS (Academic)", "Band [A-E]");
        assertEquals("7.0", m.get("Band B").overall().toPlainString());
        assertEquals("6.5", m.get("Band B").minSub().toPlainString());
        assertEquals("5.5", m.get("Band D").minSub().toPlainString());
        assertNull(AbrLanguageBandRules.scores("185 overall", "x"));
        assertEquals("Band D", AbrLanguageBandRules.normalizeLabel("band d"));
    }
}
