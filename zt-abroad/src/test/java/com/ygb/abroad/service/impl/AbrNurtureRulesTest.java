package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrNurtureSequence;
import com.ygb.abroad.domain.AbrNurtureStep;
import com.ygb.abroad.growth.MarketingCalendar;
import com.ygb.common.exception.ServiceException;

/**
 * 培育序列规则测试：到期时间、下一步、退出条件、被拦下的处理、剧本限制、序列校验
 *
 * @author ygb
 */
class AbrNurtureRulesTest
{
    private static Date at(String t)
    {
        return Date.from(LocalDateTime.parse(t).atZone(MarketingCalendar.ZONE).toInstant());
    }

    @Test
    void dueTime()
    {
        Date enrolled = at("2026-10-20T15:30:00");
        assertEquals(enrolled, AbrNurtureRules.dueTime(enrolled, 0, null, null));
        assertEquals(at("2026-10-23T10:00:00"), AbrNurtureRules.dueTime(enrolled, 3, 10, null));
        assertEquals(at("2026-10-23T15:30:00"), AbrNurtureRules.dueTime(enrolled, 3, null, null));
        // 当天几点已过：不早于 notBefore
        assertEquals(enrolled, AbrNurtureRules.dueTime(enrolled, 0, 10, enrolled));
        assertEquals(at("2026-10-20T23:00:00"), AbrNurtureRules.dueTime(enrolled, -1, 30, null));
    }

    private static AbrNurtureStep step(int no, String status)
    {
        AbrNurtureStep s = new AbrNurtureStep();
        s.setStepNo(no);
        s.setStepName("第 " + no + " 步");
        s.setStatus(status);
        s.setTemplateCode("nurture_x");
        s.setDelayDays(no);
        return s;
    }

    @Test
    void nextStepSkipsDisabled()
    {
        List<AbrNurtureStep> steps = List.of(step(1, "0"), step(2, "1"), step(3, "0"));
        assertEquals(1, AbrNurtureRules.nextStep(steps, 1).getStepNo());
        assertEquals(3, AbrNurtureRules.nextStep(steps, 2).getStepNo());
        assertNull(AbrNurtureRules.nextStep(steps, 4));
    }

    @Test
    void exitReasons()
    {
        Date now = at("2026-12-01T10:00:00");
        Date enrolled = at("2026-10-20T10:00:00");
        assertTrue(AbrNurtureRules.exitReason("booked", 0, null, enrolled, 30, now).contains("booked"));
        assertEquals("已有进行中的预约", AbrNurtureRules.exitReason("new", 1, null, enrolled, 30, now));
        assertTrue(AbrNurtureRules.exitReason("new", 0, at("2026-10-25T10:00:00"), enrolled, 30, now).startsWith("30 天无互动"));
        assertNull(AbrNurtureRules.exitReason("contacted", 0, at("2026-11-20T10:00:00"), enrolled, 30, now));
        // 没有互动记录或早于入组时按入组时间算
        assertNull(AbrNurtureRules.exitReason(null, 0, null, at("2026-11-25T10:00:00"), 30, now));
        assertNull(AbrNurtureRules.exitReason(null, 0, null, enrolled, 0, now));
    }

    @Test
    void blockedActions()
    {
        assertEquals(AbrNurtureRules.BlockAction.RETRY_TOMORROW, AbrNurtureRules.onBlocked("频控：7 天内营销触达已达 3 次"));
        assertEquals(AbrNurtureRules.BlockAction.RETRY_LATER, AbrNurtureRules.onBlocked("已暂停：熔断"));
        assertEquals(AbrNurtureRules.BlockAction.RETRY_LATER, AbrNurtureRules.onBlocked("获客自动化已全局暂停：x"));
        assertEquals(AbrNurtureRules.BlockAction.RETRY_LATER, AbrNurtureRules.onBlocked("执行模式为停用"));
        assertEquals(AbrNurtureRules.BlockAction.EXIT, AbrNurtureRules.onBlocked("已退订营销消息"));
        assertEquals(AbrNurtureRules.BlockAction.EXIT, AbrNurtureRules.onBlocked("未成年人不接收营销消息"));
        assertEquals(AbrNurtureRules.BlockAction.EXIT, AbrNurtureRules.onBlocked("合规闸门拦截"));
        assertEquals(AbrNurtureRules.BlockAction.EXIT, AbrNurtureRules.onBlocked(null));
    }

    @Test
    void playbookRestriction()
    {
        assertTrue(AbrNurtureRules.playbookAllowed("[]", Set.of()));
        assertTrue(AbrNurtureRules.playbookAllowed(null, Set.of()));
        assertTrue(AbrNurtureRules.playbookAllowed("[\"language_sprint\"]", Set.of("season_open", "language_sprint")));
        assertEquals(false, AbrNurtureRules.playbookAllowed("[\"language_sprint\"]", Set.of("season_open")));
        assertThrows(ServiceException.class, () -> AbrNurtureRules.codes("{}"));
        assertThrows(ServiceException.class, () -> AbrNurtureRules.codes("[1]"));
        assertThrows(ServiceException.class, () -> AbrNurtureRules.codes("[bad"));
    }

    private static AbrNurtureSequence sequence(List<AbrNurtureStep> steps)
    {
        AbrNurtureSequence s = new AbrNurtureSequence();
        s.setSequenceCode(" new_lead ");
        s.setSequenceName("新线索培育");
        s.setEnrollEvent("lead_captured");
        s.setSteps(steps);
        return s;
    }

    @Test
    void validateNormalizes()
    {
        AbrNurtureStep a = step(5, null);
        a.setDelayDays(0);
        a.setSendHour(10);
        a.setPlaybookCodes("[\"b\", \"a\"]");
        AbrNurtureStep b = step(9, "1");
        b.setDelayDays(3);
        b.setConditionCode("has_plan");
        AbrNurtureSequence s = sequence(new ArrayList<>(List.of(a, b)));
        AbrNurtureRules.validate(s);
        assertEquals("new_lead", s.getSequenceCode());
        assertEquals(30, s.getInactiveDays());
        assertEquals("0", s.getStatus());
        assertEquals(1, a.getStepNo());
        assertEquals(2, b.getStepNo());
        assertEquals("none", a.getConditionCode());
        assertEquals("[\"a\",\"b\"]", a.getPlaybookCodes());
        assertEquals("[]", b.getPlaybookCodes());
        assertEquals("1", b.getStatus());
    }

    @Test
    void validateRejects()
    {
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(sequence(List.of())));
        AbrNurtureStep back = step(2, "0");
        back.setDelayDays(1);
        AbrNurtureStep first = step(1, "0");
        first.setDelayDays(3);
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(sequence(List.of(first, back))));
        AbrNurtureStep hour = step(1, "0");
        hour.setSendHour(24);
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(sequence(List.of(hour))));
        AbrNurtureStep cond = step(1, "0");
        cond.setConditionCode("other");
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(sequence(List.of(cond))));
        AbrNurtureStep tpl = step(1, "0");
        tpl.setTemplateCode("");
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(sequence(List.of(tpl))));
        AbrNurtureStep name = step(1, "0");
        name.setStepName(" ");
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(sequence(List.of(name))));
        AbrNurtureSequence code = sequence(List.of(step(1, "0")));
        code.setSequenceCode("New");
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(code));
        AbrNurtureSequence noName = sequence(List.of(step(1, "0")));
        noName.setSequenceName("");
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(noName));
        AbrNurtureSequence noEvent = sequence(List.of(step(1, "0")));
        noEvent.setEnrollEvent(null);
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(noEvent));
        AbrNurtureSequence days = sequence(List.of(step(1, "0")));
        days.setInactiveDays(400);
        assertThrows(ServiceException.class, () -> AbrNurtureRules.validate(days));
    }
}
