package com.ygb.abroad.growth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 事件过滤测试：空过滤、相等、可选值数组、数字与字符串、缺字段、格式校验
 *
 * @author ygb
 */
class EventFilterTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode j(String json)
    {
        return MAPPER.readTree(json);
    }

    @Test
    void emptyFilterMatchesAll()
    {
        assertTrue(EventFilter.matches(null, j("{\"moment\":\"imbalance\"}")));
        assertTrue(EventFilter.matches(j("{}"), j("{}")));
        assertTrue(EventFilter.matches(j("{}"), null));
    }

    @Test
    void equalityAndArrays()
    {
        JsonNode payload = j("{\"moment\":\"imbalance\",\"count\":3,\"minor\":false,\"nested\":{\"a\":1}}");
        assertTrue(EventFilter.matches(j("{\"moment\":\"imbalance\"}"), payload));
        assertTrue(EventFilter.matches(j("{\"moment\":[\"parent_view\",\"imbalance\"]}"), payload));
        assertFalse(EventFilter.matches(j("{\"moment\":[\"parent_view\"]}"), payload));
        assertTrue(EventFilter.matches(j("{\"count\":\"3\"}"), payload));
        assertTrue(EventFilter.matches(j("{\"count\":3,\"minor\":false}"), payload));
        assertFalse(EventFilter.matches(j("{\"count\":3,\"minor\":true}"), payload));
        assertFalse(EventFilter.matches(j("{\"missing\":\"x\"}"), payload));
        assertFalse(EventFilter.matches(j("{\"nested\":\"x\"}"), payload));
        assertFalse(EventFilter.matches(j("{\"moment\":\"imbalance\"}"), j("[]")));
    }

    @Test
    void validate()
    {
        assertNull(EventFilter.validate(null));
        assertNull(EventFilter.validate(j("{\"moment\":[\"a\",\"b\"],\"n\":1,\"b\":true}")));
        assertNotNull(EventFilter.validate(j("[]")));
        assertNotNull(EventFilter.validate(j("{\"moment\":[]}")));
        assertNotNull(EventFilter.validate(j("{\"moment\":{\"a\":1}}")));
        assertNotNull(EventFilter.validate(j("{\"moment\":[{\"a\":1}]}")));
        assertNotNull(EventFilter.validate(j("{\"moment\":null}")));
    }
}
