package com.ygb.abroad.ai.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.ai.AiJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 评测样例定义测试：写法校验、能否启用、由命中项生成 notContains 断言、notContains 断言执行
 *
 * @author ygb
 */
class AiEvalCasesTest
{
    private static JsonNode json(String s)
    {
        return AiJson.parseOrNull(s);
    }

    @Test
    void validateRejectsBadShapes()
    {
        assertEquals(1, AiEvalCases.validate(json("[]")).size());
        assertTrue(AiEvalCases.validate(json("{\"input\":{\"a\":1},\"expect\":[{\"path\":\"/a\",\"equals\":1}]}")).isEmpty());
        assertFalse(AiEvalCases.validate(json("{\"input\":[1]}")).isEmpty());
        assertFalse(AiEvalCases.validate(json("{\"expect\":{}}")).isEmpty());
        assertFalse(AiEvalCases.validate(json("{\"expect\":[{\"path\":\"a\",\"equals\":1}]}")).isEmpty(), "path 须以 / 开头");
        assertFalse(AiEvalCases.validate(json("{\"expect\":[{\"path\":\"/a\"}]}")).isEmpty(), "没有断言");
        assertFalse(AiEvalCases.validate(json("{\"expect\":[{\"path\":\"/a\",\"equals\":1,\"contains\":1}]}")).isEmpty(), "两个断言");
        assertFalse(AiEvalCases.validate(json("{\"expect\":[{\"path\":\"/a\",\"like\":1}]}")).isEmpty(), "未知断言");
    }

    @Test
    void runnableNeedsInputAndExpect()
    {
        assertTrue(AiEvalCases.runnable(json("{\"input\":{\"a\":1},\"expect\":[{\"path\":\"/a\",\"exists\":true}]}")));
        assertFalse(AiEvalCases.runnable(json("{\"input\":{},\"expect\":[{\"path\":\"/a\",\"exists\":true}]}")));
        assertFalse(AiEvalCases.runnable(json("{\"input\":{\"a\":1},\"expect\":[]}")));
        assertFalse(AiEvalCases.runnable(null));
    }

    @Test
    void fromFlagsBuildsNotContains()
    {
        JsonNode flags = json("[{\"field\":\"title\",\"matched\":\"保录\"},{\"field\":\"body\",\"matched\":\"加V\"},{\"field\":\"body\",\"matched\":\"加V\"},"
                + "{\"field\":\"cards\",\"matched\":\"第一\"},{\"field\":\"body\",\"matched\":null},{\"category\":\"citation\"}]");
        ObjectNode spec = AiEvalCases.fromFlags("抽检不合格：x", json("{\"topic\":\"英国硕士\"}"), flags, Map.of("title", "/title", "body", "/body"));
        assertEquals("抽检不合格：x", spec.path("name").asString());
        assertEquals(2, spec.path("expect").size(), "重复、映射不到、没有文字的不生成");
        assertEquals("/title", spec.path("expect").get(0).path("path").asString());
        assertEquals("保录", spec.path("expect").get(0).path("notContains").asString());
        assertTrue(AiEvalCases.validate(spec).isEmpty());
        assertTrue(AiEvalCases.runnable(spec));
        assertFalse(AiEvalCases.runnable(AiEvalCases.fromFlags("x", null, flags, Map.of("title", "/title"))), "没有模型输入");
    }

    @Test
    void notContainsAssertion()
    {
        JsonNode out = json("{\"body\":\"签约保录\",\"tags\":[\"留学\",\"加V了解\"]}");
        assertFalse(AiEvalRunner.check(out, json("{\"path\":\"/body\",\"notContains\":\"保录\"}")).isPassed());
        assertTrue(AiEvalRunner.check(out, json("{\"path\":\"/body\",\"notContains\":\"包过\"}")).isPassed());
        assertFalse(AiEvalRunner.check(out, json("{\"path\":\"/tags\",\"notContains\":\"加V\"}")).isPassed());
        assertTrue(AiEvalRunner.check(out, json("{\"path\":\"/missing\",\"notContains\":\"x\"}")).isPassed());
    }
}
