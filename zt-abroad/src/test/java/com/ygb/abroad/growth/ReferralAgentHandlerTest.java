package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrGrowthTask;
import com.ygb.abroad.service.IAbrReferralService;

/**
 * referral_agent 处理器测试：动作对应的服务方法与参数、结果映射
 *
 * @author ygb
 */
class ReferralAgentHandlerTest
{
    private static AbrGrowthTask task(String action, String payload)
    {
        AbrGrowthTask t = new AbrGrowthTask();
        t.setTaskId(3L);
        t.setAction(action);
        t.setPayload(payload);
        return t;
    }

    @Test
    void routes() throws Exception
    {
        List<String> calls = new ArrayList<>();
        IAbrReferralService service = (IAbrReferralService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IAbrReferralService.class },
                (proxy, method, args) -> {
                    calls.add(method.getName() + ":" + args[0]);
                    return "attribute".equals(method.getName()) ? Map.of("action", "skipped", "reason", "x") : Map.of("action", "valid", "reason", "");
                });
        ReferralAgentHandler h = new ReferralAgentHandler();
        Field f = ReferralAgentHandler.class.getDeclaredField("referralService");
        f.setAccessible(true);
        f.set(h, service);
        assertEquals("referral_agent", h.agentCode());
        assertEquals(GrowthTaskOutcome.Kind.SKIPPED, h.handle(task("attribute", "{\"event\": {\"lead_id\": 5}}")).kind());
        assertEquals(GrowthTaskOutcome.Kind.DONE, h.handle(task("assessment", "{\"event\": {\"assessment_id\": 9}}")).kind());
        assertEquals(GrowthTaskOutcome.Kind.DONE, h.handle(task("signed", "{\"event\": {\"lead_id\": 5, \"to\": \"won\"}}")).kind());
        assertEquals(GrowthTaskOutcome.Kind.DONE, h.handle(task("invite_code", "{\"event\": {\"lead_id\": 5}}")).kind());
        assertEquals(GrowthTaskOutcome.Kind.SKIPPED, h.handle(task("other", "{}")).kind());
        assertEquals(List.of("attribute:5", "assessmentSubmitted:9", "signed:5", "inviteCode:5"), calls);
    }
}
