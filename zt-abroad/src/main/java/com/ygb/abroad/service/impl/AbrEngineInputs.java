package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 院校库实体 → 选校引擎输入的转换（引擎不依赖实体与数据库）
 *
 * @author ygb
 */
public final class AbrEngineInputs
{
    private AbrEngineInputs()
    {
    }

    /**
     * 组装项目事实
     *
     * @param program 项目
     * @param requirement 当季申请要求（没有记录为 null）
     * @return 引擎用的项目事实
     */
    public static ProgramFacts programFacts(AbrProgram program, AbrRequirement requirement)
    {
        return programFacts(program, null, requirement);
    }

    /**
     * 组装项目事实（含偏好排序用的学费、学制、城市、排名）
     *
     * @param program 项目
     * @param school 所属院校（没有时城市、排名为空）
     * @param requirement 当季申请要求（没有记录为 null）
     * @return 引擎用的项目事实
     */
    public static ProgramFacts programFacts(AbrProgram program, AbrSchool school, AbrRequirement requirement)
    {
        ProgramFacts.Listing listing = new ProgramFacts.Listing(program.getTuition(), program.getCurrency(), program.getDurationMonths(),
                school == null || StringUtils.isBlank(school.getCity()) ? null : school.getCity().trim(),
                school == null ? Map.of() : latestRanks(school.getRankings()));
        return new ProgramFacts(program.getProgramId(), program.getCountry(), StringUtils.isBlank(program.getDifficulty()) ? null
                : program.getDifficulty().trim(), StringUtils.isBlank(program.getTeachingLanguage()) ? "en" : program.getTeachingLanguage(),
                "Y".equals(program.getApsRequired()), requirement == null ? null : requirement(requirement), listing);
    }

    /**
     * 榜单名次（[{"list":"QS","year":2026,"rank":40}]）→ 每个榜单最新一年的名次
     */
    static Map<String, Integer> latestRanks(String rankings)
    {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        Map<String, Integer> years = new LinkedHashMap<>();
        JsonNode list = AbrJsonFields.parse(rankings, "榜单名次");
        if (list == null || !list.isArray())
        {
            return ranks;
        }
        for (JsonNode r : list)
        {
            String name = r.path("list").asString("").trim();
            if (name.isEmpty() || !r.path("rank").isNumber())
            {
                continue;
            }
            int year = r.path("year").asInt(0);
            if (!years.containsKey(name) || year > years.get(name))
            {
                years.put(name, year);
                ranks.put(name, r.path("rank").asInt());
            }
        }
        return ranks;
    }

    private static ProgramFacts.Requirement requirement(AbrRequirement r)
    {
        Map<String, BigDecimal> minGpa = new LinkedHashMap<>();
        JsonNode gpa = AbrJsonFields.parse(r.getMinGpa(), "最低均分");
        if (gpa != null && gpa.isObject())
        {
            for (Map.Entry<String, JsonNode> e : gpa.properties())
            {
                if (e.getValue().isNumber())
                {
                    minGpa.put(e.getKey(), e.getValue().decimalValue());
                }
            }
        }
        Set<String> tiers = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(r.getTiersNotAccepted()))
        {
            for (String t : r.getTiersNotAccepted().split(","))
            {
                if (StringUtils.isNotBlank(t))
                {
                    tiers.add(t.trim());
                }
            }
        }
        JsonNode other = AbrJsonFields.parse(r.getOtherLanguage(), "其他语言要求");
        return new ProgramFacts.Requirement(minGpa, tiers, r.getIeltsOverall(), r.getIeltsMinSub(), r.getTestRequirement(),
                other != null && other.isObject() ? other : null, r.getPrerequisites(), "Y".equals(r.getPrerequisitesRequired()),
                r.getWorkYears(), r.getAcceptCrossMajor(), r.getAcceptPreSessional());
    }
}
