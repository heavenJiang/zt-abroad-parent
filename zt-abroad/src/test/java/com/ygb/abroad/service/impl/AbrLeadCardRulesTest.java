package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 线索卡片规则测试：英语说明、每档代表项目、AI 输出校验（长度、条数、百分比 / 概率 / 手机号）
 *
 * @author ygb
 */
class AbrLeadCardRulesTest
{
    private static JsonNode json(String s) throws Exception
    {
        return JsonMapper.builder().build().readTree(s);
    }

    @Test
    void english()
    {
        assertEquals("雅思 7.0（小分 6.5）", AbrLeadCardRules.english(new BigDecimal("7"), new BigDecimal("6.5"), "taken"));
        assertEquals("雅思 6.5", AbrLeadCardRules.english(6.5, null, null));
        assertEquals("planned", AbrLeadCardRules.english(null, null, "planned"));
        assertEquals("", AbrLeadCardRules.english(null, null, null));
    }

    @Test
    void representativesTwoPerTier() throws Exception
    {
        List<Object[]> r = AbrLeadCardRules.representatives(json("""
                [{"programId":1,"tier":"match","missing":[]},{"programId":2,"tier":"match"},{"programId":3,"tier":"match"},
                 {"programId":4,"tier":"reach","missing":["雅思 7.0","x",1]},{"programId":5,"tier":"other"},{"tier":"safety"}]"""));
        assertEquals(3, r.size());
        assertEquals("主攻", r.get(0)[1]);
        assertEquals(4L, r.get(2)[0]);
        assertEquals(List.of("雅思 7.0", "x"), r.get(2)[2]);
        assertTrue(AbrLeadCardRules.representatives(null).isEmpty());
    }

    @Test
    void validateOutput() throws Exception
    {
        String ok = "{\"summary\":\"这位同学 211 本科，目标英国管理类硕士。\",\"concerns\":[{\"text\":\"担心材料来不及\",\"basis\":\"截止前材料不齐\"}],"
                + "\"openers\":[\"材料里最卡的是哪一项？\"]}";
        assertNull(AbrLeadCardRules.validate(json(ok)));
        assertTrue(AbrLeadCardRules.validate(json(ok.replace("目标英国", "录取率 80% 的英国"))).contains("百分比"));
        assertTrue(AbrLeadCardRules.validate(json(ok.replace("目标英国", "有七成把握的英国"))).contains("百分比"));
        assertTrue(AbrLeadCardRules.validate(json(ok.replace("材料里最卡", "打 13812345678 问材料里最卡"))).contains("手机号"));
        assertTrue(AbrLeadCardRules.validate(json(ok.replace("\"concerns\":[{\"text\":\"担心材料来不及\",\"basis\":\"截止前材料不齐\"}]", "\"concerns\":[]")))
                .contains("顾虑"));
        assertTrue(AbrLeadCardRules.validate(json(ok.replace("这位同学 211 本科，目标英国管理类硕士。", "短"))).contains("摘要"));
        assertTrue(AbrLeadCardRules.validate(json("[]")).contains("对象"));
    }
}
