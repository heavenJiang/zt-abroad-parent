package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.growth.card.QrCodes;
import com.ygb.common.exception.ServiceException;

/**
 * 合作方规则
 *
 * @author ygb
 */
class AbrPartnerRulesTest
{
    @Test
    void tools()
    {
        List<AbrPartnerRules.Tool> t = AbrPartnerRules.tools("home:首页:, quick:30 秒快测:/pages/quick/index ,bad,Bad:x:y,open::pages/tool/open");
        assertEquals(2, t.size());
        assertEquals("", t.get(0).path());
        assertEquals("pages/quick/index", t.get(1).path());
        assertEquals("?ch=partner&ct=abcd2345", AbrPartnerRules.pathAndQuery(t.get(0), "partner", "abcd2345"));
        assertEquals("pages/quick/index?ch=partner&ct=abcd2345", AbrPartnerRules.pathAndQuery(t.get(1), "partner", "abcd2345"));
        assertEquals("p?x=1&ch=partner&ct=c", AbrPartnerRules.pathAndQuery(new AbrPartnerRules.Tool("a", "b", "p?x=1"), "partner", "c"));
        assertTrue(AbrPartnerRules.tools(null).isEmpty());
    }

    @Test
    void monthAndText()
    {
        LocalDate today = LocalDate.of(2026, 10, 20);
        assertEquals("2026-10", AbrPartnerRules.month(null, today));
        assertEquals("2026-09", AbrPartnerRules.month(" 2026-09 ", today));
        assertThrows(ServiceException.class, () -> AbrPartnerRules.month("2026-9", today));
        assertEquals("张*", AbrPartnerRules.maskName(" 张三 "));
        assertEquals("𠀀*", AbrPartnerRules.maskName("𠀀同学"));
        assertEquals("", AbrPartnerRules.maskName(null));
        assertEquals("流水 123", AbrPartnerRules.note(" 流水 123 ", true, "发放说明"));
        assertEquals("", AbrPartnerRules.note(null, false, "说明"));
        assertThrows(ServiceException.class, () -> AbrPartnerRules.note(" ", true, "作废原因"));
        assertThrows(ServiceException.class, () -> AbrPartnerRules.note("字".repeat(91), false, "说明"));
    }

    @Test
    void qrPng()
    {
        byte[] png = QrCodes.png("https://app.example.com/?ch=partner&ct=abcd2345", 240);
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
    }
}
