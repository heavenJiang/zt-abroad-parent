package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端 offer 墙的一张 offer（只含泛化后的信息，不含任何可识别个人的字段）
 *
 * @param caseId 案例ID（取 offer 卡用）
 * @param season 申请季（如 2027 秋）
 * @param country 国家 / 地区名称
 * @param degree 学段名称
 * @param school 院校
 * @param program 项目
 * @param scholarship 奖学金（可空）
 * @param background 背景（院校层次 · 专业方向、均分区间、语言）
 * @author ygb
 */
public record AppOfferVo(Long caseId, String season, String country, String degree, String school, String program, String scholarship,
        List<String> background)
{
}
