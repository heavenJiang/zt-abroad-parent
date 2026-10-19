package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 线索分配规则测试：上限、按国家与学段匹配与退回、轮询与按负载、SLA 到期
 *
 * @author ygb
 */
class AbrLeadAssignerTest
{
    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 19, 10, 0);

    private static AbrLeadAssigner.Candidate c(long id, Set<String> countries, Set<String> degrees, int max, int active, LocalDateTime last)
    {
        return new AbrLeadAssigner.Candidate(id, countries, degrees, max, active, last);
    }

    @Test
    void roundRobinAndLoad()
    {
        List<AbrLeadAssigner.Candidate> cs = List.of(c(1, Set.of(), Set.of(), 10, 5, T.minusHours(1)), c(2, Set.of(), Set.of(), 10, 1, T),
                c(3, Set.of(), Set.of(), 10, 3, null));
        assertEquals(3L, AbrLeadAssigner.pick(cs, Set.of("UK"), null, true, "round_robin"), "从未分配的优先");
        assertEquals(2L, AbrLeadAssigner.pick(cs, Set.of("UK"), null, true, "load"), "负载最少");
        List<AbrLeadAssigner.Candidate> full = List.of(c(1, Set.of(), Set.of(), 5, 5, null));
        assertNull(AbrLeadAssigner.pick(full, Set.of(), null, false, "load"), "都满了");
    }

    @Test
    void matchCountryAndDegree()
    {
        List<AbrLeadAssigner.Candidate> cs = List.of(c(1, Set.of("UK", "HK"), Set.of("master"), 10, 0, null), c(2, Set.of("US"), Set.of(), 10, 0, null));
        assertEquals(2L, AbrLeadAssigner.pick(cs, Set.of("US"), "master", true, "round_robin"));
        assertEquals(1L, AbrLeadAssigner.pick(cs, Set.of("HK"), null, true, "round_robin"));
        assertEquals(1L, AbrLeadAssigner.pick(cs, Set.of("JP"), null, true, "round_robin"), "匹配不到时退回全部，按ID");
        assertEquals(1L, AbrLeadAssigner.pick(cs, Set.of("UK"), "phd", true, "round_robin"), "国家与学段都没有完全匹配的顾问时退回全部");
        assertEquals(2L, AbrLeadAssigner.pick(cs, Set.of("UK", "US"), "phd", true, "round_robin"), "学段不符的不选");
        assertEquals(1L, AbrLeadAssigner.pick(cs, Set.of("US"), null, false, "round_robin"), "不匹配时按轮询");
    }

    @Test
    void slaDue()
    {
        Map<String, Integer> h = Map.of("A", 2, "B", 24);
        assertEquals(T.plusHours(2), AbrLeadAssigner.slaDue(T, "A", h));
        assertNull(AbrLeadAssigner.slaDue(T, "C", h));
        assertNull(AbrLeadAssigner.slaDue(T, null, h));
    }
}
