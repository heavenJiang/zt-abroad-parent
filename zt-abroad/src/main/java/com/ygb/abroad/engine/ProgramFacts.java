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
 * @author ygb
 */
public record ProgramFacts(Long programId, String country, String difficulty, String teachingLanguage, boolean apsRequired,
        Requirement requirement)
{
    /**
     * 申请要求（字段为空表示项目库没有该项要求）
     *
     * @param minGpa 本科层次 → 最低百分制均分
     * @param tiersNotAccepted 不接受的本科层次
     * @param ieltsOverall 雅思总分要求
     * @param ieltsMinSub 雅思最低小分要求
     * @param otherLanguage 其他语言要求（{"toefl":{"overall":90,"minSub":20},"jlpt":{"level":"N2"}}，可为 null）
     * @param prerequisites 先修课要求（文字）
     * @param prerequisitesRequired 先修课是否硬性要求
     * @param workYears 工作年限要求（年）
     * @param acceptCrossMajor 是否接受跨专业（0 不接受 / 1 接受 / 2 未知）
     * @param acceptPreSessional 是否接受语言班（0 不接受 / 1 接受 / 2 未知）
     */
    public record Requirement(Map<String, BigDecimal> minGpa, Set<String> tiersNotAccepted, BigDecimal ieltsOverall,
            BigDecimal ieltsMinSub, JsonNode otherLanguage, String prerequisites, boolean prerequisitesRequired, Integer workYears,
            String acceptCrossMajor, String acceptPreSessional)
    {
    }
}
