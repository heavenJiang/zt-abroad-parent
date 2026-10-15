package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端：申请日历（选校单中正常项目的申请轮次与截止日，取院校库最新申请季）
 *
 * @param events 截止事件（按日期从早到晚；含已过的轮次，标 passed）
 * @param rolling 滚动录取、没有固定截止日的项目
 * @param noDeadline 院校库暂无截止信息的项目
 * @param unverified 信息超过 90 天未核实、不展示截止日的项目（以官网为准）
 * @param nextDeadline 最近一个未过的截止事件（没有为 null）
 * @param note 说明
 * @author ygb
 */
public record AppCalendarVo(List<Event> events, List<ProgramRef> rolling, List<ProgramRef> noDeadline, List<ProgramRef> unverified,
        Event nextDeadline, String note)
{
    /**
     * 截止事件
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param countryLabel 国家 / 地区名称
     * @param round 轮次名称（院校原文，如 Round 1；只有一个截止日时为 Deadline）
     * @param date 截止日（yyyy-MM-dd，院校当地时间）
     * @param daysLeft 距今天数（已过为负数）
     * @param passed 是否已过
     * @param website 项目官网
     * @param verifiedDate 项目信息核对日期
     */
    public record Event(Long programId, String schoolName, String programName, String countryLabel, String round, String date, long daysLeft,
            boolean passed, String website, String verifiedDate)
    {
    }

    /**
     * 项目
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param countryLabel 国家 / 地区名称
     * @param website 项目官网
     */
    public record ProgramRef(Long programId, String schoolName, String programName, String countryLabel, String website)
    {
    }
}
