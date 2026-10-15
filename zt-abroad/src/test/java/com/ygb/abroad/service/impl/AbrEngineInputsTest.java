package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.engine.ProgramFacts;

/**
 * 院校库实体 → 引擎项目事实的转换测试
 *
 * @author ygb
 */
class AbrEngineInputsTest
{
    private static AbrProgram program()
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(9L);
        p.setCountry("UK");
        p.setDifficulty(" D2 ");
        p.setTeachingLanguage("en");
        p.setApsRequired("N");
        return p;
    }

    @Test
    void convertsRequirement()
    {
        AbrRequirement r = new AbrRequirement();
        r.setMinGpa("{\"211\": 85, \"985\": 82.5, \"note\": \"x\"}");
        r.setTiersNotAccepted("non_key, double_first_class,");
        r.setIeltsOverall(new BigDecimal("6.5"));
        r.setIeltsMinSub(new BigDecimal("6.0"));
        r.setOtherLanguage("{\"toefl\": {\"overall\": 92}}");
        r.setPrerequisites("微积分");
        r.setPrerequisitesRequired("Y");
        r.setWorkYears(2);
        r.setAcceptCrossMajor("0");
        r.setAcceptPreSessional("1");

        ProgramFacts f = AbrEngineInputs.programFacts(program(), r);
        assertEquals(9L, f.programId());
        assertEquals("UK", f.country());
        assertEquals("D2", f.difficulty());
        assertEquals("en", f.teachingLanguage());
        assertFalse(f.apsRequired());
        ProgramFacts.Requirement q = f.requirement();
        assertEquals(Map.of("211", new BigDecimal("85"), "985", new BigDecimal("82.5")), q.minGpa());
        assertEquals(Set.of("non_key", "double_first_class"), q.tiersNotAccepted());
        assertEquals(new BigDecimal("6.5"), q.ieltsOverall());
        assertEquals(92, q.otherLanguage().path("toefl").path("overall").asInt());
        assertTrue(q.prerequisitesRequired());
        assertEquals(2, q.workYears());
        assertEquals("0", q.acceptCrossMajor());
        assertEquals("1", q.acceptPreSessional());
    }

    @Test
    void emptyFieldsAndDefaults()
    {
        AbrProgram p = program();
        p.setDifficulty("");
        p.setTeachingLanguage(null);
        p.setApsRequired("Y");
        ProgramFacts f = AbrEngineInputs.programFacts(p, null);
        assertNull(f.difficulty());
        assertEquals("en", f.teachingLanguage());
        assertTrue(f.apsRequired());
        assertNull(f.requirement());

        AbrRequirement r = new AbrRequirement();
        r.setOtherLanguage("[]");
        ProgramFacts.Requirement q = AbrEngineInputs.programFacts(program(), r).requirement();
        assertTrue(q.minGpa().isEmpty());
        assertTrue(q.tiersNotAccepted().isEmpty());
        assertNull(q.otherLanguage());
        assertFalse(q.prerequisitesRequired());
    }
}
