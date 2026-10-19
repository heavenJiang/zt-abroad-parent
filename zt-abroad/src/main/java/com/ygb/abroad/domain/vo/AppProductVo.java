package com.ygb.abroad.domain.vo;

/**
 * C 端：产品（只含对外展示的字段）
 *
 * @param productCode 产品编码
 * @param productName 名称
 * @param category 类别
 * @param description 介绍
 * @param priceText 价格展示（未定价为“价格面议”）
 * @param bookable 是否可在线预约
 * @param durationMinutes 预约时长（分钟）
 * @author ygb
 */
public record AppProductVo(String productCode, String productName, String category, String description, String priceText, boolean bookable,
        Integer durationMinutes)
{
}
