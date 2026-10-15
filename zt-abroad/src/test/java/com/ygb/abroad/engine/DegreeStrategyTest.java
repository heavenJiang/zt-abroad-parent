package com.ygb.abroad.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 学段策略：1a 只实现硕士；博士不分档
 *
 * @author ygb
 */
class DegreeStrategyTest
{
    @Test
    void onlyMasterImplemented()
    {
        assertEquals(List.of("secondary", "bachelor", "master", "phd"), Arrays.stream(DegreeStrategy.values()).map(DegreeStrategy::degree).toList());
        assertTrue(DegreeStrategy.of("master").implemented());
        assertEquals("tiered", DegreeStrategy.of("master").method());
        assertFalse(DegreeStrategy.of("bachelor").implemented());
        assertEquals("academic_match", DegreeStrategy.of("secondary").method());
        assertFalse(DegreeStrategy.of("phd").tiered());
        assertTrue(DegreeStrategy.of("bachelor").tiered());
        assertNull(DegreeStrategy.of("mba"));
        assertNull(DegreeStrategy.of(null));
    }
}
