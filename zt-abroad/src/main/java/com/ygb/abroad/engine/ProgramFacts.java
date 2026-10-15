package com.ygb.abroad.engine;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 引擎用的项目事实（院校库 abr_program + 当季 abr_requirement 的只读快照，由 Service 组装后传入）
 *
 * @param programId 项目ID
 * @param country 国家 / 地区代码（US/UK/AU/SG/HK/JP/KR/DE）
 * @param difficulty 难度档 D1–D5（未定档为 null，兜底值无法取用）
 * @param teachingLanguage 授课语言（en / ja / ko / de / zh / other，缺省按 en）
 * @param apsRequired 项目是否标明需要 APS
 * @param requirement 当季申请要求（项目库没有记录时为 null）
 * @param listing 偏好排序用的院校库信息（学费、学制、城市、排名；没有时为 null）
 * @author ygb
 */
public record ProgramFacts(Long programId, String country, String difficulty, String teachingLanguage, boolean apsRequired,
        Requirement requirement, Listing listing)
{
    /**
     * 不带院校库信息的项目事实（只做硬门槛、综合分与分档时使用）
     *
     * @param programId 项目ID
     * @param country 国家 / 地区代码
     * @param difficulty 难度档
     * @param teachingLanguage 授课语言
     * @param apsRequired 是否需要 APS
     * @param requirement 当季申请要求
     */
    public ProgramFacts(Long programId, String country, String difficulty, String teachingLanguage, boolean apsRequired,
            Requirement requirement)
    {
        this(programId, country, difficulty, teachingLanguage, apsRequired, requirement, null);
    }

    /**
     * 偏好排序用的院校库信息（字段为空表示院校库没有该信息）
     *
     * @param tuition 学费（每年）
     * @param currency 学费币种（ISO 4217）
     * @param durationMonths 学制（月）
     * @param city 院校所在城市
     * @param ranks 榜单 → 最新一年名次（如 {"QS": 40}）
     */
    public record Listing(BigDecimal tuition, String currency, Integer durationMonths, String city, Map<String, Integer> ranks)
    {
    }

    /**
     * 申请要求（字段为空表示项目库没有该项要求）
     *
     * @param minGpa 本科层次 → 最低百分制均分
     * @param tiersNotAccepted 不接受的本科层次
     * @param ieltsOverall 雅思总分要求
     * @param ieltsMinSub 雅思最低小分要求
     * @param testRequirement 标化要求（none 不要求 / optional 可选 / required 必需；为空按可选）
     * @param otherLanguage 其他语言要求（{"toefl":{"overall":90,"minSub":20},"jlpt":{"level":"N2"}}，可为 null）
     * @param prerequisites 先修课要求（文字）
     * @param prerequisitesRequired 先修课是否硬性要求
     * @param workYears 工作年限要求（年）
     * @param acceptCrossMajor 是否接受跨专业（0 不接受 / 1 接受 / 2 未知）
     * @param acceptPreSessional 是否接受语言班（0 不接受 / 1 接受 / 2 未知）
     * @param gpaNoCutoff 官网明确不设均分线（为 true 时不套用兜底均分）
     */
    public record Requirement(Map<String, BigDecimal> minGpa, Set<String> tiersNotAccepted, BigDecimal ieltsOverall,
            BigDecimal ieltsMinSub, String testRequirement, JsonNode otherLanguage, String prerequisites, boolean prerequisitesRequired, Integer workYears,
            String acceptCrossMajor, String acceptPreSessional, boolean gpaNoCutoff)
    {
        /**
         * 未注明是否设均分线的申请要求
         *
         * @param minGpa 本科层次 → 最低百分制均分
         * @param tiersNotAccepted 不接受的本科层次
         * @param ieltsOverall 雅思总分要求
         * @param ieltsMinSub 雅思最低小分要求
         * @param testRequirement 标化要求
         * @param otherLanguage 其他语言要求
         * @param prerequisites 先修课要求
         * @param prerequisitesRequired 先修课是否硬性要求
         * @param workYears 工作年限要求
         * @param acceptCrossMajor 是否接受跨专业
         * @param acceptPreSessional 是否接受语言班
         */
        public Requirement(Map<String, BigDecimal> minGpa, Set<String> tiersNotAccepted, BigDecimal ieltsOverall, BigDecimal ieltsMinSub,
                String testRequirement, JsonNode otherLanguage, String prerequisites, boolean prerequisitesRequired, Integer workYears,
                String acceptCrossMajor, String acceptPreSessional)
        {
            this(minGpa, tiersNotAccepted, ieltsOverall, ieltsMinSub, testRequirement, otherLanguage, prerequisites, prerequisitesRequired,
                    workYears, acceptCrossMajor, acceptPreSessional, false);
        }
    }
}
