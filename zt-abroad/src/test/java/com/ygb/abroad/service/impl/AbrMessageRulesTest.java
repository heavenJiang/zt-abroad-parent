package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrMessageTemplate;
import com.ygb.common.exception.ServiceException;

/**
 * 消息模板规则测试：变量渲染与缺变量、字段映射、模板启用条件
 *
 * @author ygb
 */
class AbrMessageRulesTest
{
    @Test
    void renderReplacesVariables()
    {
        assertEquals("你已预约 选校咨询，时间 11-01 10:00。", AbrMessageRules.render("你已预约 {productName}，时间 {startTime}。",
                Map.of("productName", " 选校咨询 ", "startTime", "11-01 10:00")));
        assertEquals("价格 $1 不当作分组", AbrMessageRules.render("价格 {p} 不当作分组", Map.of("p", "$1")));
        assertEquals("", AbrMessageRules.render(null, Map.of()));
        assertEquals("没有变量 {1abc}", AbrMessageRules.render("没有变量 {1abc}", Map.of()));
    }

    @Test
    void renderMissingVariableThrows()
    {
        ServiceException e = assertThrows(ServiceException.class, () -> AbrMessageRules.render("{a} 与 {b} 与 {a}", Map.of("a", "x")));
        assertEquals("缺少变量：b", e.getMessage());
        assertThrows(ServiceException.class, () -> AbrMessageRules.render("{a}", Map.of("a", "  ")));
    }

    @Test
    void channelNames()
    {
        assertEquals("服务号：未关注", AbrMessageRules.channelName("service_account"));
        assertEquals("短信：没有手机号", AbrMessageRules.channelName("sms"));
        assertEquals("小程序：未授权", AbrMessageRules.channelName("mp_subscribe"));
        assertEquals("企业微信", AbrMessageRules.channelName("wecom"));
        assertEquals("x", AbrMessageRules.channelName("x"));
        assertEquals("", AbrMessageRules.channelName(null));
    }

    @Test
    void variablesAndFieldMap()
    {
        assertEquals(Set.of("productName", "appUrl"), AbrMessageRules.variables("{productName} 见 {appUrl}"));
        Map<String, String> f = AbrMessageRules.fieldMap("{\"thing1\": \"{productName}\", \"time2\": \"{startTime}\"}");
        assertEquals("[thing1, time2]", f.keySet().toString());
        assertEquals(Map.of("thing1", "A", "time2", "B"), AbrMessageRules.renderFields(f, Map.of("productName", "A", "startTime", "B")));
        assertTrue(AbrMessageRules.fieldMap(null).isEmpty());
        assertThrows(ServiceException.class, () -> AbrMessageRules.fieldMap("[]"));
        assertThrows(ServiceException.class, () -> AbrMessageRules.fieldMap("{\"thing1\": 1}"));
        assertThrows(ServiceException.class, () -> AbrMessageRules.fieldMap("{\"thing 1\": \"x\"}"));
        assertThrows(ServiceException.class, () -> AbrMessageRules.fieldMap("{bad"));
    }

    private static AbrMessageTemplate template(String channel, String status)
    {
        AbrMessageTemplate t = new AbrMessageTemplate();
        t.setTemplateCode("booking_confirmed");
        t.setChannel(channel);
        t.setTemplateName("预约成功");
        t.setCategory("service");
        t.setFieldMap("{\"thing1\": \"{productName}\"}");
        t.setStatus(status);
        return t;
    }

    @Test
    void validateEnableConditions()
    {
        AbrMessageTemplate draft = template("service_account", "1");
        AbrMessageRules.validate(draft);
        assertEquals("1", draft.getStatus());
        assertEquals("", draft.getPlatformTemplateId());
        AbrMessageTemplate noId = template("service_account", "0");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(noId));
        AbrMessageTemplate ok = template("service_account", "0");
        ok.setPlatformTemplateId(" TPL_1 ");
        AbrMessageRules.validate(ok);
        assertEquals("TPL_1", ok.getPlatformTemplateId());
        assertEquals("{\"thing1\":\"{productName}\"}", ok.getFieldMap());
        AbrMessageTemplate wecom = template("wecom", "0");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(wecom));
        wecom.setContent("{title}\n{content}");
        AbrMessageRules.validate(wecom);
        AbrMessageTemplate marketing = template("sms", "0");
        marketing.setCategory("marketing");
        marketing.setPlatformTemplateId("SMS_1");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(marketing));
        marketing.setContent("申请季开启，来测一下");
        AbrMessageRules.validate(marketing);
    }

    @Test
    void validateBasicFields()
    {
        AbrMessageTemplate badCode = template("sms", "1");
        badCode.setTemplateCode("Booking");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(badCode));
        AbrMessageTemplate badChannel = template("email", "1");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(badChannel));
        AbrMessageTemplate badCategory = template("sms", "1");
        badCategory.setCategory("other");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(badCategory));
        AbrMessageTemplate noName = template("sms", "1");
        noName.setTemplateName(" ");
        assertThrows(ServiceException.class, () -> AbrMessageRules.validate(noName));
        AbrMessageTemplate status = template("sms", "x");
        AbrMessageRules.validate(status);
        assertEquals("1", status.getStatus());
    }
}
