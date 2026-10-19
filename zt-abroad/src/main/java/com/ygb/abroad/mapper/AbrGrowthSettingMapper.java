package com.ygb.abroad.mapper;

import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrGrowthSetting;

/**
 * 获客自动化全局设置 数据层（单行 setting_id = 1）
 * 
 * @author ygb
 */
public interface AbrGrowthSettingMapper
{
    /**
     * 查询全局设置
     * 
     * @return 设置
     */
    public AbrGrowthSetting selectAbrGrowthSetting();

    /**
     * 修改全局设置（触达上限、免打扰、预算；不改暂停状态）
     * 
     * @param setting 设置
     * @return 结果
     */
    public int updateAbrGrowthSetting(AbrGrowthSetting setting);

    /**
     * 全局暂停（已暂停时不变）
     * 
     * @param reason 原因
     * @param pausedBy 暂停人
     * @return 影响行数（已暂停为 0）
     */
    public int pauseGlobal(@Param("reason") String reason, @Param("pausedBy") String pausedBy);

    /**
     * 全局恢复（未暂停时不变）
     * 
     * @param updateBy 恢复人
     * @return 影响行数（未暂停为 0）
     */
    public int resumeGlobal(String updateBy);
}
