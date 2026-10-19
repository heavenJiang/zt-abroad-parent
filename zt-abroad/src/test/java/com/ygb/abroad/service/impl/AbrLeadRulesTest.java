package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.common.exception.ServiceException;

/**
 * 线索规则测试：手机号规范化、去重保留哪一条、时间线说明
 *
 * @author ygb
 */
class AbrLeadRulesTest
{
    @Test
    void normalizePhone()
    {
        assertEquals("13812345678", AbrLeadRules.normalizePhone("138 1234 5678"));
        assertEquals("13812345678", AbrLeadRules.normalizePhone("+86 138-1234-5678"));
        assertEquals("13812345678", AbrLeadRules.normalizePhone("8613812345678"));
        assertEquals("13812345678", AbrLeadRules.normalizePhone("（138）12345678"));
        assertNull(AbrLeadRules.normalizePhone("  "));
        ServiceException e = assertThrows(ServiceException.class, () -> AbrLeadRules.normalizePhone("12812345678"));
        assertTrue(e.getMessage().contains("128****5678"), "报错信息中的手机号脱敏");
        assertThrows(ServiceException.class, () -> AbrLeadRules.normalizePhone("abc"));
    }

    private static AbrLead lead(long id, long time)
    {
        AbrLead l = new AbrLead();
        l.setLeadId(id);
        l.setFirstTouchTime(new Date(time));
        return l;
    }

    @Test
    void masterIsEarliest()
    {
        assertEquals(3L, AbrLeadRules.master(List.of(lead(1, 200), lead(3, 100), lead(2, 300))).getLeadId());
        assertEquals(1L, AbrLeadRules.master(List.of(lead(2, 100), lead(1, 100))).getLeadId(), "同时按ID");
        assertNull(AbrLeadRules.master(List.of()));
    }

    @Test
    void timelineLabels()
    {
        assertEquals("加入选校单", AbrLeadRules.label("event", "plan_item_added", ""));
        assertEquals("custom_event", AbrLeadRules.label("event", "custom_event", ""));
        assertEquals("使用 30 秒快测", AbrLeadRules.label("tool", "quick", ""));
        assertEquals("使用工具 japan_path", AbrLeadRules.label("tool", "japan_path", ""));
        assertEquals("提交完整测评", AbrLeadRules.label("assessment", "submitted", ""));
        assertEquals("开始完整测评", AbrLeadRules.label("assessment", "in_progress", ""));
        assertEquals("讲座报名", AbrLeadRules.label("touch", "import", "讲座报名"));
        assertEquals("员工录入", AbrLeadRules.label("touch", "manual", ""));
        assertTrue(AbrLeadRules.STAFF_SOURCES.stream().allMatch(AbrLeadRules.ALL_SOURCES::contains));
    }

    @Test
    void followLabelsAndNextFollow()
    {
        assertEquals("跟进：电话沟通", AbrLeadRules.label("follow", "follow", "电话沟通"));
        assertEquals("阶段变更：预算不够", AbrLeadRules.label("follow", "stage", "预算不够"));
        assertEquals("转给同事", AbrLeadRules.label("follow", "assign", ""));
        Date now = new Date(1_800_000_000_000L);
        AbrLeadRules.checkNextFollow(null, now);
        AbrLeadRules.checkNextFollow(new Date(now.getTime() - 60_000), now);
        assertThrows(ServiceException.class, () -> AbrLeadRules.checkNextFollow(new Date(now.getTime() - 3_600_000), now));
        assertThrows(ServiceException.class, () -> AbrLeadRules.checkNextFollow(new Date(now.getTime() + 400L * 24 * 3600 * 1000), now));
    }
}
