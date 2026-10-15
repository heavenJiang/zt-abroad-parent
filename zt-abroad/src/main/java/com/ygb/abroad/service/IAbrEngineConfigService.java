package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrEngineConfig;
import com.ygb.abroad.domain.vo.AbrEngineConfigCheck;
import tools.jackson.databind.JsonNode;

/**
 * 选校引擎配置版本 服务层
 * 
 * @author ygb
 */
public interface IAbrEngineConfigService
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
     * 校验配置 JSON（不保存）
     * 
     * @param json 配置 JSON
     * @return 校验结果
     */
    public AbrEngineConfigCheck checkAbrEngineConfig(String json);

    /**
     * 导入配置（保存为草稿）
     * 
     * @param json 配置 JSON
     * @param remark 备注
     * @param operator 操作人
     * @return 新的配置版本
     */
    public AbrEngineConfig importAbrEngineConfig(String json, String remark, String operator);

    /**
     * 启用配置版本（同国家 × 学位原启用版本改为已停用）；只有顾问已确认（confirmed）的版本可启用
     * 
     * @param configId 配置ID
     * @param operator 操作人
     * @return 结果
     */
    public int activateAbrEngineConfig(Long configId, String operator);

    /**
     * 删除草稿
     * 
     * @param configIds 配置ID
     * @param operator 操作人
     * @return 结果
     */
    public int deleteAbrEngineConfigByIds(Long[] configIds, String operator);

    /**
     * 某学位启用的配置
     * 
     * @param degree 学位
     * @return 国家 / 地区代码 → 配置根节点
     */
    public Map<String, JsonNode> selectActiveConfigs(String degree);
}
