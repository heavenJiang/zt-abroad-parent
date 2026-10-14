package com.ygb.abroad.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 模板渲染测试
 *
 * @author ygb
 */
class AiTemplateTest
{
    @Test
    void rendersScalarsNestedAndMissing()
    {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("gpa", 85);
        profile.put("tier", "985");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("name", "[姓名1]");
        vars.put("profile", profile);
        vars.put("schools", List.of("UCL", "KCL"));
        vars.put("ok", true);
        String out = AiTemplate.render("学生 {{name}}，{{ profile.gpa }} 分，档案 {{profile}}，学校 {{schools}}，{{ok}}，缺失[{{none}}]{{a.b.c}}", vars);
        assertEquals("学生 [姓名1]，85 分，档案 {\"gpa\":85,\"tier\":\"985\"}，学校 [\"UCL\",\"KCL\"]，true，缺失[]", out);
    }

    @Test
    void specialCharactersAreLiteral()
    {
        assertEquals("价格 $100 \\ 元", AiTemplate.render("价格 {{p}}", Map.of("p", "$100 \\ 元")));
        assertEquals("{ 单括号 } 不变", AiTemplate.render("{ 单括号 } 不变", Map.of()));
        assertEquals("", AiTemplate.render(null, Map.of()));
        assertEquals("x=", AiTemplate.render("x={{x}}", null));
    }

    @Test
    void jsonNodeValues()
    {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("node", AiJson.parseOrNull("{\"a\":{\"b\":\"深\"},\"n\":null}"));
        assertEquals("深|{\"b\":\"深\"}|", AiTemplate.render("{{node.a.b}}|{{node.a}}|{{node.n}}", vars));
    }
}
