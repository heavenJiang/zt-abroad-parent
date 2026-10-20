package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrBookingAgentService;
import com.ygb.abroad.service.IAbrMessageService;

/**
 * booking_agent 处理器测试：按动作取任务内容中的参数、结果映射（完成 / 跳过 / 推迟）
 *
 * @author ygb
 */
class BookingAgentHandlerTest
{
    /** 记录调用的服务桩 */
    static class FakeService implements IAbrBookingAgentService
    {
        final List<String> calls = new ArrayList<>();

        IAbrMessageService.Result next = new IAbrMessageService.Result("mock", "sms", 1L, "", null);

        Map<String, Object> inviteResult = new LinkedHashMap<>(Map.of("action", "sent", "reason", ""));

        @Override
        public IAbrMessageService.Result confirm(Long bookingId, Long taskId)
        {
            calls.add("confirm:" + bookingId + ":" + taskId);
            return next;
        }

        @Override
        public IAbrMessageService.Result notifyChange(Long bookingId, String change, Long taskId)
        {
            calls.add("change:" + bookingId + ":" + change);
            return next;
        }

        @Override
        public Map<String, Object> remindDue(int hoursBefore, Long taskId)
        {
            calls.add("remind:" + hoursBefore);
            return Map.of("due", 0);
        }

        @Override
        public Map<String, Object> invite(Long leadId, Long appUserId, Long visitorId, String moment, String productCode, Long taskId)
        {
            calls.add("invite:" + leadId + ":" + appUserId + ":" + visitorId + ":" + moment + ":" + productCode);
            return inviteResult;
        }
    }

    private static BookingAgentHandler handler(FakeService s) throws Exception
    {
        BookingAgentHandler h = new BookingAgentHandler();
        Field f = BookingAgentHandler.class.getDeclaredField("bookingAgentService");
        f.setAccessible(true);
        f.set(h, s);
        return h;
    }

    private static AbrGrowthTask task(String action, String payload)
    {
        AbrGrowthTask t = new AbrGrowthTask();
        t.setTaskId(9L);
        t.setAction(action);
        t.setPayload(payload);
        return t;
    }

    @Test
    void routesBookingActions() throws Exception
    {
        FakeService s = new FakeService();
        BookingAgentHandler h = handler(s);
        assertEquals("booking_agent", h.agentCode());
        assertEquals(GrowthTaskOutcome.Kind.DONE, h.handle(task("confirm", "{\"event\": {\"booking_id\": 5}}")).kind());
        h.handle(task("notify_change", "{\"event\": {\"booking_id\": 5, \"change\": \"no_show\"}}"));
        h.handle(task("remind", "{\"params\": {\"hours_before\": 6}}"));
        h.handle(task("remind", "{}"));
        assertEquals(List.of("confirm:5:9", "change:5:no_show", "remind:6", "remind:24"), s.calls);
    }

    @Test
    void messageOutcomes() throws Exception
    {
        FakeService s = new FakeService();
        BookingAgentHandler h = handler(s);
        s.next = null;
        assertEquals(GrowthTaskOutcome.Kind.SKIPPED, h.handle(task("confirm", "{\"event\": {}}")).kind());
        Date until = new Date(System.currentTimeMillis() + 3600_000L);
        s.next = new IAbrMessageService.Result("deferred", "sms", 2L, "免打扰", until);
        GrowthTaskOutcome o = h.handle(task("confirm", "{\"event\": {\"booking_id\": 5}}"));
        assertEquals(GrowthTaskOutcome.Kind.DEFER, o.kind());
        assertEquals(until, o.deferUntil());
        s.next = new IAbrMessageService.Result("off", "", 3L, "未开通", null);
        assertEquals(GrowthTaskOutcome.Kind.DONE, h.handle(task("confirm", "{\"event\": {\"booking_id\": 5}}")).kind());
        assertEquals(GrowthTaskOutcome.Kind.SKIPPED, h.handle(task("other", "{}")).kind());
    }

    @Test
    void inviteReadsTargetsAndMoment() throws Exception
    {
        FakeService s = new FakeService();
        BookingAgentHandler h = handler(s);
        h.handle(task("invite", "{\"event\": {\"lead_id\": 3, \"to\": \"A\"}, \"params\": {\"product\": \"consult_position\"}}"));
        h.handle(task("recommend", "{\"event\": {\"moment\": \"imbalance\"}, \"app_user_id\": 7, \"visitor_id\": 8, \"params\": {}}"));
        assertEquals(List.of("invite:3:null:null:null:consult_position", "invite:null:7:8:imbalance:"), s.calls);
        s.inviteResult = new LinkedHashMap<>(Map.of("action", "skipped", "reason", "已有进行中的预约"));
        assertEquals(GrowthTaskOutcome.Kind.SKIPPED, h.handle(task("invite", "{\"event\": {\"lead_id\": 3}}")).kind());
        Date until = new Date();
        s.inviteResult = new LinkedHashMap<>(Map.of("action", "deferred", "reason", "免打扰", "deferUntil", until));
        assertEquals(GrowthTaskOutcome.Kind.DEFER, h.handle(task("invite", "{\"event\": {\"lead_id\": 3}}")).kind());
        s.inviteResult = new LinkedHashMap<>(Map.of("action", "manual", "reason", "人工确认"));
        assertEquals(GrowthTaskOutcome.Kind.DONE, h.handle(task("invite", "{\"event\": {\"lead_id\": 3}}")).kind());
    }
}
