package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * 编排任务结果测试：重试间隔
 *
 * @author ygb
 */
class GrowthTaskOutcomeTest
{
    @Test
    void retryDelay()
    {
        assertEquals(5, GrowthTaskOutcome.retryDelayMinutes(0));
        assertEquals(5, GrowthTaskOutcome.retryDelayMinutes(1));
        assertEquals(10, GrowthTaskOutcome.retryDelayMinutes(2));
        assertEquals(20, GrowthTaskOutcome.retryDelayMinutes(3));
        assertEquals(40, GrowthTaskOutcome.retryDelayMinutes(4));
        assertEquals(60, GrowthTaskOutcome.retryDelayMinutes(5));
        assertEquals(60, GrowthTaskOutcome.retryDelayMinutes(9));
        assertEquals(GrowthTaskOutcome.Kind.SKIPPED, GrowthTaskOutcome.skipped("x").kind());
        assertEquals("x", GrowthTaskOutcome.retry("x").message());
    }
}
