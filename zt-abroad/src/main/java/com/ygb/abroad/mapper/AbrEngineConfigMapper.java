package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrEngineConfig;

/**
 * 选校引擎配置版本 数据层
 * 
 * @author ygb
 */
public interface AbrEngineConfigMapper
{
    /**
     * 查询配置版本列表（不含配置 JSON）
     * 
     * @param config 查询条件
     * @return 配置版本集合
     */
    public List<AbrEngineConfig> selectAbrEngineConfigList(AbrEngineConfig config);

    /**
     * 按ID查询配置版本（含配置 JSON）
     * 
     * @param configId 配置ID
     * @return 配置版本
     */
    public AbrEngineConfig selectAbrEngineConfigById(Long configId);

    /**
     * 查询某学位全部启用的配置（含配置 JSON）
     * 
     * @param degree 学位
     * @return 启用的配置（每个国家 / 地区一条）
     */
    public List<AbrEngineConfig> selectActiveAbrEngineConfigs(String degree);

    /**
     * 查询某国家 × 学位当前启用的配置（不含配置 JSON）
     * 
     * @param country 国家 / 地区
     * @param degree 学位
     * @return 启用的配置，没有返回 null
     */
    public AbrEngineConfig selectActiveAbrEngineConfig(@Param("country") String country, @Param("degree") String degree);

    /**
     * 按编码和版本查询（不含配置 JSON）
     * 
     * @param configCode 配置编码
     * @param version 版本
     * @return 配置版本
     */
    public AbrEngineConfig selectAbrEngineConfigByCodeVersion(@Param("configCode") String configCode, @Param("version") String version);

    /**
     * 新增配置版本（草稿）
     * 
     * @param config 配置版本
     * @return 结果
     */
    public int insertAbrEngineConfig(AbrEngineConfig config);

    /**
     * 停用某国家 × 学位当前启用的版本
     * 
     * @param country 国家 / 地区
     * @param degree 学位
     * @param updateBy 操作人
     * @return 结果
     */
    public int retireActiveAbrEngineConfig(@Param("country") String country, @Param("degree") String degree, @Param("updateBy") String updateBy);

    /**
     * 启用配置版本
     * 
     * @param configId 配置ID
     * @param updateBy 操作人
     * @return 结果
     */
    public int activateAbrEngineConfig(@Param("configId") Long configId, @Param("updateBy") String updateBy);

    /**
     * 删除草稿（只删除 draft 状态的版本）
     * 
     * @param ids 配置ID
     * @param updateBy 操作人
     * @return 结果
     */
    public int deleteDraftAbrEngineConfigByIds(@Param("ids") Long[] ids, @Param("updateBy") String updateBy);
}
