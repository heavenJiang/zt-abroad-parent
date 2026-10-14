package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 输出过滤测试（使用 prompts/_filters 下的真实词表）
 *
 * @author ygb
 */
class AiOutputFilterTest
{
    private final AiOutputFilter filter = new AiOutputFilter(AiTestSupport.LOADER.loadFilterLists(), 0);

    private List<AiFilterHit> check(String json)
    {
        return filter.check(AiJson.parseOrNull(json));
    }

    @Test
    void cleanOutputPasses()
    {
        assertEquals(List.of(), check("{\"summary\":\"背景与项目要求基本匹配，建议第一年先补数学先修课\",\"tags\":[\"稳\"],\"score\":90}"));
        assertEquals(AiOutputFilter.DEFAULT_MAX_FIELD_LENGTH, filter.getMaxFieldLength());
    }

    @Test
    void admissionPromises()
    {
        for (String text : new String[] { "保证录取", "包 录 取", "100%录取", "该项目必录", "稳拿Offer", "不录取全额退款" })
        {
            List<AiFilterHit> hits = check("{\"a\":{\"b\":[\"" + text + "\"]}}");
            assertEquals(1, hits.size(), text + " → " + hits);
            assertEquals("admission_promise", hits.get(0).getType());
            assertEquals("录取承诺", hits.get(0).getLabel());
            assertEquals("/a/b/0", hits.get(0).getPath());
        }
    }

    @Test
    void advertisingWordsWithExceptions()
    {
        List<AiFilterHit> hits = check("{\"x\":\"我们是业内顶级、国家级的最佳机构\"}");
        assertEquals(3, hits.size(), hits.toString());
        assertTrue(hits.stream().allMatch(h -> "广告违禁词".equals(h.getLabel())));
        assertEquals(List.of(), check("{\"x\":\"第一学期与第一轮截止日\"}"));
        assertEquals(1, check("{\"x\":\"第一学期就是全国第一\"}").size());
    }

    @Test
    void essayLength()
    {
        String longText = "啊".repeat(1501);
        List<AiFilterHit> hits = check("{\"essay\":\"" + longText + "\",\"ok\":\"" + "啊".repeat(1500) + "\"}");
        assertEquals(1, hits.size());
        assertEquals(AiOutputFilter.TYPE_ESSAY, hits.get(0).getType());
        assertEquals("/essay", hits.get(0).getPath());

        AiOutputFilter strict = new AiOutputFilter(List.of(Map.entry("custom", "# 类别：自定义\n禁词\n")), 10);
        List<AiFilterHit> h2 = strict.check(AiJson.parseOrNull("[\"这是一个超过十个字的禁词句子\"]"));
        assertEquals(2, h2.size());
        assertEquals("自定义", h2.get(1).getLabel());
        assertEquals(List.of(), strict.check(null));
    }
}
