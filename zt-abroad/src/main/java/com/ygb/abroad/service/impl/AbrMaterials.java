package com.ygb.abroad.service.impl;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ygb.common.core.domain.entity.SysDictData;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 申请材料：材料类型（字典 abr_material）与材料列表 JSON 的校验
 *
 * 材料列表格式 [{code, count, note}]：code 为字典 abr_material 的值，count 为份数（1–10，可省略），note 为说明（最多 100 字，可省略）。
 * 字典样式属性 css_class 为准备方式：shared 各项目共用一份 / program 每个项目单独准备。
 *
 * @author ygb
 */
final class AbrMaterials
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 字典类型 */
    static final String DICT_TYPE = "abr_material";

    /** 准备方式：各项目共用 */
    static final String SCOPE_SHARED = "shared";

    /** 准备方式：每个项目单独准备 */
    static final String SCOPE_PROGRAM = "program";

    /** 字典未加载时的内置材料类型（与 update_20261016_materials.sql 种子一致） */
    static final Map<String, Type> BUILTIN = new LinkedHashMap<>();

    /** 说明最大长度 */
    static final int MAX_NOTE = 100;

    /** 份数上限 */
    static final int MAX_COUNT = 10;

    static
    {
        String[][] seed = { { "transcript", "成绩单", "shared" }, { "degree_cert", "学位 / 在读证明", "shared" }, { "language", "语言成绩", "shared" },
            { "gre_gmat", "GRE / GMAT 成绩", "shared" }, { "cv", "简历", "shared" }, { "ps", "个人陈述 / 文书", "program" },
            { "essay", "附加文书", "program" }, { "reference", "推荐信", "shared" }, { "portfolio", "作品集", "shared" },
            { "writing_sample", "写作样本", "shared" }, { "research_plan", "研究计划书", "program" }, { "wes", "学历认证", "shared" },
            { "aps", "APS 证书", "shared" }, { "passport", "护照", "shared" }, { "interview", "面试", "program" }, { "fee", "申请费", "program" } };
        for (String[] s : seed)
        {
            BUILTIN.put(s[0], new Type(s[0], s[1], s[2]));
        }
    }

    private AbrMaterials()
    {
    }

    /**
     * 材料类型（按字典顺序，只取正常状态；字典未加载时用内置类型）
     *
     * @return 编码 → 类型
     */
    static Map<String, Type> types()
    {
        List<SysDictData> datas = DictUtils.getDictCache(DICT_TYPE);
        if (datas == null || datas.isEmpty())
        {
            return BUILTIN;
        }
        Map<String, Type> map = new LinkedHashMap<>();
        for (SysDictData d : datas)
        {
            if (d.getStatus() == null || "0".equals(d.getStatus()))
            {
                String scope = SCOPE_PROGRAM.equals(StringUtils.trim(d.getCssClass())) ? SCOPE_PROGRAM : SCOPE_SHARED;
                map.put(d.getDictValue(), new Type(d.getDictValue(), d.getDictLabel(), scope));
            }
        }
        return map;
    }

    /**
     * 校验并规范化材料列表
     *
     * @param json 材料列表 JSON（null 表示未填写）
     * @param codes 允许的材料类型
     * @return 规范化后的 JSON 数组文本（未填写返回 null；空数组返回 "[]"）
     */
    static String normalize(String json, Set<String> codes)
    {
        JsonNode node = AbrJsonFields.parse(json, "申请材料");
        if (node == null)
        {
            return null;
        }
        if (!node.isArray())
        {
            throw new ServiceException("申请材料应为 JSON 数组");
        }
        ArrayNode out = MAPPER.createArrayNode();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : node)
        {
            String code = item.path("code").asString("").trim();
            if (!codes.contains(code))
            {
                throw new ServiceException("申请材料类型不正确：" + StringUtils.substring(code, 0, 32));
            }
            if (!seen.add(code))
            {
                throw new ServiceException("申请材料重复：" + code);
            }
            ObjectNode o = out.addObject();
            o.put("code", code);
            JsonNode count = item.get("count");
            if (count != null && !count.isNull())
            {
                if (!count.canConvertToInt() || !count.isIntegralNumber() || count.asInt() < 1 || count.asInt() > MAX_COUNT)
                {
                    throw new ServiceException("申请材料份数应为 1–" + MAX_COUNT + " 的整数：" + code);
                }
                o.put("count", count.asInt());
            }
            String note = item.path("note").isString() ? item.path("note").asString().trim() : "";
            if (note.length() > MAX_NOTE)
            {
                throw new ServiceException("申请材料说明最多 " + MAX_NOTE + " 字：" + code);
            }
            if (AbrAssessmentAnswers.containsTag(MAPPER.getNodeFactory().stringNode(note)))
            {
                throw new ServiceException("申请材料说明不能包含 < 加字母的写法");
            }
            if (!note.isEmpty())
            {
                o.put("note", note);
            }
        }
        return out.toString();
    }

    /**
     * 材料类型
     *
     * @param code 编码
     * @param label 名称
     * @param scope 准备方式（shared / program）
     */
    record Type(String code, String label, String scope)
    {
    }
}
