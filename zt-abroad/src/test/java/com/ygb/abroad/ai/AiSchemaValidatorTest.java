package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Schema 校验器测试
 *
 * @author ygb
 */
class AiSchemaValidatorTest
{
    private static final JsonNode SCHEMA = AiJson.parseOrNull("""
            {"type":"object","required":["name","level","score","tags"],"additionalProperties":false,
             "properties":{
               "name":{"type":"string","minLength":2,"maxLength":5},
               "level":{"type":"string","enum":["ok","warn"]},
               "score":{"type":"number","minimum":0,"maximum":100},
               "count":{"type":"integer"},
               "tags":{"type":"array","minItems":1,"items":{"type":"string","maxLength":3}},
               "note":{"type":["string","null"]},
               "detail":{"type":"object","required":["a"],"properties":{"a":{"type":"boolean"}}}
             }}""");

    private static List<String> v(String json)
    {
        return AiSchemaValidator.validate(SCHEMA, AiJson.parseOrNull(json));
    }

    @Test
    void validPasses()
    {
        assertEquals(List.of(), v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":88.5,\"count\":3,\"tags\":[\"a\"],\"note\":null,\"detail\":{\"a\":true}}"));
        assertEquals(List.of(), v("{\"name\":\"张三\",\"level\":\"warn\",\"score\":0,\"tags\":[\"abc\"],\"note\":\"x\"}"));
    }

    @Test
    void requiredMissing()
    {
        List<String> e = v("{\"name\":\"张三\",\"level\":\"ok\",\"tags\":[\"a\"]}");
        assertEquals(1, e.size());
        assertTrue(e.get(0).contains("缺少必填字段 score"), e.toString());
    }

    @Test
    void typeMismatchAndUnion()
    {
        assertTrue(v("{\"name\":1,\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"]}").get(0).contains("/name"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":\"1\",\"tags\":[\"a\"]}").get(0).contains("类型应为 number"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"],\"note\":5}").get(0).contains("[\"string\",\"null\"]"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"count\":1.5,\"tags\":[\"a\"]}").get(0).contains("integer"));
        assertEquals(List.of(), v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"count\":2.0,\"tags\":[\"a\"]}"));
        assertTrue(AiSchemaValidator.validate(SCHEMA, AiJson.parseOrNull("[1]")).get(0).contains("（根）"));
    }

    @Test
    void enumRangeLength()
    {
        assertTrue(v("{\"name\":\"张三\",\"level\":\"bad\",\"score\":1,\"tags\":[\"a\"]}").get(0).contains("不在允许范围"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":101,\"tags\":[\"a\"]}").get(0).contains("大于最大值"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":-1,\"tags\":[\"a\"]}").get(0).contains("小于最小值"));
        assertTrue(v("{\"name\":\"张\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"]}").get(0).contains("小于最小长度"));
        assertTrue(v("{\"name\":\"张三李四王五\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"]}").get(0).contains("超过最大长度"));
    }

    @Test
    void arraysAndNested()
    {
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"tags\":[]}").get(0).contains("少于"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\",\"abcd\"]}").get(0).contains("/tags/1"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"],\"detail\":{}}").get(0).contains("/detail"));
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"],\"detail\":{\"a\":\"yes\"}}").get(0).contains("/detail/a"));
    }

    @Test
    void additionalProperties()
    {
        assertTrue(v("{\"name\":\"张三\",\"level\":\"ok\",\"score\":1,\"tags\":[\"a\"],\"extra\":1}").get(0).contains("不允许的字段 extra"));
        JsonNode open = AiJson.parseOrNull("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"number\"}}");
        assertEquals(List.of(), AiSchemaValidator.validate(open, AiJson.parseOrNull("{\"a\":1,\"b\":2}")));
        assertEquals(1, AiSchemaValidator.validate(open, AiJson.parseOrNull("{\"a\":1,\"b\":\"x\"}")).size());
    }

    @Test
    void missingData()
    {
        assertEquals(1, AiSchemaValidator.validate(SCHEMA, null).size());
    }
}
