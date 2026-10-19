package com.ygb.abroad.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 线索分配与 SLA 规则（纯计算，不访问数据库）
 * <ul>
 * <li>候选：接单、未达负责上限的顾问；按国家 / 地区与学段匹配时只留擅长的（擅长为空表示全部），匹配不到时退回全部候选；</li>
 * <li>挑选：轮询（最久没被分配的优先）或按负载（进行中线索最少的优先），并列时按员工ID；</li>
 * <li>SLA：分配时间 + 该意向度的时限；没有时限的意向度不设到期。</li>
 * </ul>
 *
 * @author ygb
 */
final class AbrLeadAssigner
{
    /** 挑选方式：轮询 */
    static final String ROUND_ROBIN = "round_robin";

    /** 挑选方式：按负载 */
    static final String LOAD = "load";

    /**
     * 候选顾问
     *
     * @param userId 员工ID
     * @param countries 擅长国家 / 地区（空为全部）
     * @param degrees 擅长学段（空为全部）
     * @param maxActive 负责上限
     * @param active 进行中线索数
     * @param lastAssigned 最近一次被自动分配的时间（从未分配为 null）
     */
    record Candidate(Long userId, Set<String> countries, Set<String> degrees, int maxActive, int active, LocalDateTime lastAssigned)
    {
    }

    private AbrLeadAssigner()
    {
    }

    /**
     * 挑选负责人
     *
     * @param candidates 接单的顾问
     * @param leadCountries 线索的目标国家 / 地区
     * @param leadDegree 线索的目标学段（不知道为 null）
     * @param matchCountry 是否按国家与学段匹配
     * @param strategy 挑选方式
     * @return 员工ID；没有可分配的顾问为 null
     */
    static Long pick(List<Candidate> candidates, Set<String> leadCountries, String leadDegree, boolean matchCountry, String strategy)
    {
        List<Candidate> free = candidates.stream().filter(c -> c.active() < c.maxActive()).toList();
        if (free.isEmpty())
        {
            return null;
        }
        List<Candidate> pool = free;
        if (matchCountry)
        {
            List<Candidate> matched = free.stream().filter(c -> fits(c, leadCountries, leadDegree)).toList();
            if (!matched.isEmpty())
            {
                pool = matched;
            }
        }
        Comparator<Candidate> order = LOAD.equals(strategy)
                ? Comparator.comparingInt(Candidate::active).thenComparing(Candidate::lastAssigned, Comparator.nullsFirst(Comparator.naturalOrder()))
                : Comparator.comparing(Candidate::lastAssigned, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(Candidate::active);
        return pool.stream().min(order.thenComparing(Candidate::userId)).map(Candidate::userId).orElse(null);
    }

    /**
     * 是否擅长：国家 / 地区有交集（任一方为空视为符合），学段包含（任一方为空视为符合）
     */
    static boolean fits(Candidate c, Set<String> leadCountries, String leadDegree)
    {
        boolean country = c.countries().isEmpty() || leadCountries.isEmpty() || leadCountries.stream().anyMatch(c.countries()::contains);
        boolean degree = c.degrees().isEmpty() || leadDegree == null || c.degrees().contains(leadDegree);
        return country && degree;
    }

    /**
     * SLA 到期时间
     *
     * @param assigned 分配时间
     * @param grade 意向度
     * @param slaHours 意向度 → 小时
     * @return 到期时间；没有时限为 null
     */
    static LocalDateTime slaDue(LocalDateTime assigned, String grade, Map<String, Integer> slaHours)
    {
        Integer h = grade == null ? null : slaHours.get(grade);
        return h == null || h <= 0 ? null : assigned.plusHours(h);
    }
}
