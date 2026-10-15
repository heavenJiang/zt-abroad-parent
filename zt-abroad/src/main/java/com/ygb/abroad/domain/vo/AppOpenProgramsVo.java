package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端：现在还能申（按截止日排序的仍开放项目；数据只取院校库）
 * 
 * @param toolRunId 运行ID
 * @param today 计算日期（北京时间，yyyy-MM-dd）
 * @param programs 仍开放的项目（有截止日的按日期从近到远 → 滚动录取 → 截止日待核实）
 * @param closedCount 本申请季已截止的项目数
 * @param missingCount 院校库未录入截止日的项目数
 * @author ygb
 */
public record AppOpenProgramsVo(Long toolRunId, String today, List<Program> programs, int closedCount, int missingCount)
{
    /**
     * 带上运行ID
     * 
     * @param toolRunId 运行ID
     * @return 新结果
     */
    public AppOpenProgramsVo withToolRunId(Long toolRunId)
    {
        return new AppOpenProgramsVo(toolRunId, today, programs, closedCount, missingCount);
    }

    /**
     * 一个项目
     * 
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param country 国家 / 地区代码
     * @param countryLabel 国家 / 地区名称
     * @param season 申请季
     * @param nextRound 最近一轮名称（如 R2；滚动录取或待核实时为 null）
     * @param nextDeadline 最近一轮截止日（yyyy-MM-dd；超过 90 天未核实时为 null）
     * @param daysLeft 距截止天数（当天截止为 0）
     * @param roundsLeft 剩余轮次数
     * @param rolling 是否滚动录取（额满即止）
     * @param unverified 项目信息超过 90 天未核实（不展示日期，以官网为准）
     * @param website 项目官网
     */
    public record Program(Long programId, String schoolName, String programName, String country, String countryLabel, String season,
            String nextRound, String nextDeadline, Integer daysLeft, int roundsLeft, boolean rolling, boolean unverified, String website)
    {
    }
}
