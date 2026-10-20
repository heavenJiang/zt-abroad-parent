package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 推荐规则测试：推荐码、归因、有效测评防刷
 *
 * @author ygb
 */
class AbrReferralRulesTest
{
    @Test
    void newCode()
    {
        String a = AbrReferralRules.newCode();
        assertEquals(8, a.length());
        assertTrue(a.chars().allMatch(c -> AbrReferralRules.ALPHABET.indexOf(c) >= 0), a);
        assertNotEquals(a, AbrReferralRules.newCode());
    }

    @Test
    void refCode()
    {
        assertEquals("abcd2345", AbrReferralRules.refCode("ref", " ABCD2345 "));
        assertNull(AbrReferralRules.refCode("share", "abcd2345"));
        assertNull(AbrReferralRules.refCode("ref", " "));
        assertNull(AbrReferralRules.refCode(null, null));
        assertEquals("abcd2345", AbrReferralRules.refCode("partner", "abcd2345"));
        assertEquals("partner", AbrReferralRules.channel("partner"));
        assertEquals("ref", AbrReferralRules.channel("student"));
    }

    @Test
    void invalidReason()
    {
        assertEquals("自己推荐自己", AbrReferralRules.invalidReason(true, 600, 120, false));
        assertEquals("未成年人不参加推荐奖励", AbrReferralRules.invalidReason(false, 600, 120, true));
        assertTrue(AbrReferralRules.invalidReason(false, 60, 120, false).startsWith("测评用时过短"));
        assertNull(AbrReferralRules.invalidReason(false, 120, 120, false));
    }
}
