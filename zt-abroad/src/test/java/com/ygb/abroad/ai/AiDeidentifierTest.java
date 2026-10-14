package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 去标识化与还原测试
 *
 * @author ygb
 */
class AiDeidentifierTest
{
    @Test
    void masksAllKinds()
    {
        AiDeidentifier d = new AiDeidentifier(List.of("张三", "张三丰", " "));
        String masked = d.mask("张三丰和张三，手机 138-1234-5678 与 +86 13912345678，身份证 11010519900307123X，"
                + "邮箱 zhang.san@example.com，微信：zs_2026abc，wechat id: Alice_01，另一个 wxid_abcdef123");
        assertEquals("[姓名1]和[姓名2]，手机 [手机1] 与 [手机2]，身份证 [证件1]，邮箱 [邮箱1]，微信：[微信1]，wechat id: [微信2]，另一个 [微信3]", masked);
        assertEquals(9, d.size());
    }

    @Test
    void sameValueSamePlaceholderAndRestore()
    {
        AiDeidentifier d = new AiDeidentifier(List.of("李雷"));
        assertEquals("[姓名1]说[姓名1]的电话是[手机1]", d.mask("李雷说李雷的电话是13800000000"));
        assertEquals("李雷说李雷的电话是13800000000", d.restore("[姓名1]说[姓名1]的电话是[手机1]"));
        assertEquals("未知 [姓名9] 保留", d.restore("未知 [姓名9] 保留"));
    }

    @Test
    void doesNotMaskOrdinaryNumbers()
    {
        AiDeidentifier d = new AiDeidentifier(null);
        String text = "雅思 7.0，GPA 3.6，学费 38000 英镑，编号 123456789012，20261014，手机号 12345678901";
        assertEquals(text, d.mask(text));
        assertEquals("微信 很好用", d.mask("微信 很好用"));
        assertEquals(0, d.size());
    }

    @Test
    void phoneInsideIdCardIsNotMaskedSeparately()
    {
        AiDeidentifier d = new AiDeidentifier(null);
        assertEquals("[证件1]", d.mask("110105199003071234"));
        assertEquals("[邮箱1]", d.mask("13812345678@qq.com"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void masksNestedInputAndRestoresJson()
    {
        AiDeidentifier d = new AiDeidentifier(List.of("韩梅梅"));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("studentName", "韩梅梅");
        input.put("gpa", 3.5);
        input.put("contacts", List.of("13812345678", Map.of("wx", "微信号 hmm_2026")));
        Map<String, Object> masked = d.maskMap(input);
        assertEquals("[姓名1]", masked.get("studentName"));
        assertEquals(3.5, masked.get("gpa"));
        List<Object> contacts = (List<Object>) masked.get("contacts");
        assertEquals("[手机1]", contacts.get(0));
        assertEquals("微信号 [微信1]", ((Map<String, Object>) contacts.get(1)).get("wx"));
        assertFalse(AiJson.toJson(masked).contains("13812345678"));
        // 原输入不变
        assertEquals("韩梅梅", input.get("studentName"));

        JsonNode output = AiJson.parseOrNull("{\"summary\":\"[姓名1]，请联系 [手机1]\",\"items\":[\"[微信1]\",1,null],\"n\":2}");
        JsonNode restored = d.restore(output);
        assertEquals("韩梅梅，请联系 13812345678", restored.get("summary").asString());
        assertEquals("hmm_2026", restored.get("items").get(0).asString());
        assertTrue(restored.get("items").get(2).isNull());
        assertEquals(2, restored.get("n").asInt());
        assertEquals("[姓名1]，请联系 [手机1]", output.get("summary").asString());
    }

    @Test
    void jsonNodeInputAndNulls()
    {
        AiDeidentifier d = new AiDeidentifier(List.of("王五"));
        JsonNode masked = (JsonNode) d.maskValue(AiJson.parseOrNull("{\"a\":[\"王五 13900001111\"]}"));
        assertEquals("[姓名1] [手机1]", masked.get("a").get(0).asString());
        assertEquals(null, d.mask(null));
        assertEquals(null, d.restore((JsonNode) null));
        assertTrue(d.maskMap(null).isEmpty());
    }
}
