package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrCalendarEntry;
import com.ygb.abroad.domain.AbrGrowthTrigger;
import com.ygb.abroad.domain.AbrPlaybook;
import com.ygb.common.exception.ServiceException;

/**
 * 编排器配置校验测试：剧本取值、内容配比合计、价格写法；日历时间窗与节点；触发规则的事件与定时
 *
 * @author ygb
 */
class AbrGrowthConfigValidatorTest
{
    private static final Set<String> DEGREES = Set.of("master", "bachelor");

    private static final Set<String> COUNTRIES = Set.of("UK", "HK");

    private static final Set<String> HOOKS = Set.of("quick", "open");

    private static final Set<String> KINDS = Set.of("case", "flash", "longtail", "invite");

    private static final Set<String> AGENTS = Set.of("topic_planner", "lead_scorer");

    private static AbrPlaybook playbook()
    {
        AbrPlaybook p = new AbrPlaybook();
        p.setPlaybookCode("season_open");
        p.setPlaybookName("申请季开启");
        p.setDegrees("[\"master\", \"master\"]");
        p.setCountries("[\"UK\",\"HK\"]");
        p.setHookTools("[\"open\"]");
        p.setProducts("[\"positioning\"]");
        p.setContentMix("{\"case\":35,\"flash\":30,\"longtail\":20,\"invite\":15}");
        p.setBackupTopics("[\" 时间线 \",\"时间线\",\"认可度怎么查\"]");
        return p;
    }

    private static void check(AbrPlaybook p)
    {
        AbrGrowthConfigValidator.checkPlaybook(p, DEGREES, COUNTRIES, HOOKS, KINDS);
    }

    @Test
    void playbookNormalized()
    {
        AbrPlaybook p = playbook();
        check(p);
        assertEquals("[\"master\"]", p.getDegrees());
        assertEquals("[\"时间线\",\"认可度怎么查\"]", p.getBackupTopics());
        assertEquals("0", p.getStatus());

        AbrPlaybook empty = new AbrPlaybook();
        check(empty);
        assertEquals("[]", empty.getCountries());
        assertEquals("{}", empty.getContentMix());
    }

    @Test
    void playbookRejectsBadValues()
    {
        AbrPlaybook p = playbook();
        p.setCountries("[\"US\"]");
        assertThrows(ServiceException.class, () -> check(p));

        AbrPlaybook mix = playbook();
        mix.setContentMix("{\"case\":50,\"flash\":30}");
        ServiceException e = assertThrows(ServiceException.class, () -> check(mix));
        assertTrue(e.getMessage().contains("80"));

        AbrPlaybook kind = playbook();
        kind.setContentMix("{\"ads\":100}");
        assertThrows(ServiceException.class, () -> check(kind));

        AbrPlaybook decimal = playbook();
        decimal.setContentMix("{\"case\":99.5,\"flash\":0.5}");
        assertThrows(ServiceException.class, () -> check(decimal));

        AbrPlaybook price = playbook();
        price.setContentFocus("定位咨询只要 999 元");
        assertThrows(ServiceException.class, () -> check(price));

        AbrPlaybook json = playbook();
        json.setHookTools("quick");
        assertThrows(ServiceException.class, () -> check(json));

        AbrPlaybook product = playbook();
        product.setProducts("[\"Bad Code\"]");
        assertThrows(ServiceException.class, () -> check(product));

        AbrPlaybook tag = playbook();
        tag.setBackupTopics("[\"<script>x\"]");
        assertThrows(ServiceException.class, () -> check(tag));
        AbrPlaybook arrow = playbook();
        arrow.setBackupTopics("[\"均分 > 85 能申哪些\"]");
        check(arrow);
        assertEquals("[\"均分 > 85 能申哪些\"]", arrow.getBackupTopics());

        AbrPlaybook status = playbook();
        status.setStatus("2");
        assertThrows(ServiceException.class, () -> check(status));
    }

    private static AbrCalendarEntry entry(String type, String start, String end, String repeat)
    {
        AbrCalendarEntry e = new AbrCalendarEntry();
        e.setEntryType(type);
        e.setEntryName("x");
        e.setPlaybookId(1L);
        e.setNodeCode("kaoyan_score");
        e.setStartDate(start == null ? null : java.sql.Date.valueOf(start));
        e.setEndDate(end == null ? null : java.sql.Date.valueOf(end));
        e.setRepeatYearly(repeat);
        return e;
    }

    @Test
    void calendarWindow()
    {
        AbrCalendarEntry ok = entry("window", "2026-10-01", "2027-03-31", null);
        AbrGrowthConfigValidator.checkCalendarEntry(ok, true);
        assertEquals("Y", ok.getRepeatYearly());
        assertNull(ok.getNodeCode());

        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2026-10-01", "2027-03-31", "Y"), false));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2026-10-01", "2026-03-31", "Y"), true));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2026-10-01", "2027-10-01", "Y"), true));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2028-02-29", "2028-03-31", "Y"), true));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2026-10-01", "2026-09-30", "N"), true));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2026-10-01", null, "N"), true));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("window", null, "2026-10-01", "N"), true));
        AbrGrowthConfigValidator.checkCalendarEntry(entry("window", "2026-10-01", "2028-10-01", "N"), true);
    }

    @Test
    void calendarNode()
    {
        AbrCalendarEntry node = entry("node", "2027-02-20", "2027-03-01", "N");
        node.setPlaybookId(null);
        AbrGrowthConfigValidator.checkCalendarEntry(node, false);
        assertEquals(node.getStartDate(), node.getEndDate());

        AbrCalendarEntry badCode = entry("node", "2027-02-20", null, "Y");
        badCode.setNodeCode("考研");
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(badCode, true));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("node", "2027-02-20", null, "Y"), false));
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkCalendarEntry(entry("other", "2027-02-20", null, "Y"), true));
    }

    private static AbrGrowthTrigger trigger(String type)
    {
        AbrGrowthTrigger t = new AbrGrowthTrigger();
        t.setTriggerName("x");
        t.setTriggerType(type);
        t.setAgentCode("lead_scorer");
        t.setAction("score");
        t.setEventType("plan_moment");
        t.setEventFilter("{\"moment\": [\"imbalance\"]}");
        t.setCronExpression("0  0 7 * * *");
        return t;
    }

    @Test
    void triggerEventAndSchedule()
    {
        AbrGrowthTrigger ev = trigger("event");
        AbrGrowthConfigValidator.checkTrigger(ev, AGENTS, true);
        assertNull(ev.getCronExpression());
        assertEquals("{\"moment\":[\"imbalance\"]}", ev.getEventFilter());
        assertEquals("{}", ev.getActionParams());

        AbrGrowthTrigger sc = trigger("schedule");
        AbrGrowthConfigValidator.checkTrigger(sc, AGENTS, true);
        assertEquals("0 0 7 * * *", sc.getCronExpression());
        assertNull(sc.getEventType());
        assertEquals("{}", sc.getEventFilter());
    }

    @Test
    void triggerRejects()
    {
        AbrGrowthTrigger agent = trigger("event");
        agent.setAgentCode("unknown");
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(agent, AGENTS, true));
        AbrGrowthTrigger playbook = trigger("event");
        playbook.setPlaybookId(99L);
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(playbook, AGENTS, false));

        AbrGrowthTrigger noType = trigger("event");
        noType.setEventType(null);
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(noType, AGENTS, true));

        AbrGrowthTrigger filter = trigger("event");
        filter.setEventFilter("{\"a\":{\"b\":1}}");
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(filter, AGENTS, true));

        AbrGrowthTrigger cron = trigger("schedule");
        cron.setCronExpression("* * * * * *");
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(cron, AGENTS, true));

        AbrGrowthTrigger params = trigger("event");
        params.setActionParams("[1]");
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(params, AGENTS, true));

        AbrGrowthTrigger type = trigger("webhook");
        assertThrows(ServiceException.class, () -> AbrGrowthConfigValidator.checkTrigger(type, AGENTS, true));
    }
}
