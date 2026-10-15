package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrMaterialTemplate;

/**
 * 默认材料模板 服务层
 * 
 * @author ygb
 */
public interface IAbrMaterialTemplateService
{
    /**
     * 查询模板列表
     * 
     * @param template 查询条件
     * @return 模板集合
     */
    public List<AbrMaterialTemplate> selectAbrMaterialTemplateList(AbrMaterialTemplate template);

    /**
     * 按ID查询模板
     * 
     * @param templateId 模板ID
     * @return 模板
     */
    public AbrMaterialTemplate selectAbrMaterialTemplateById(Long templateId);

    /**
     * 新增模板（校验材料列表与「国家 / 地区 × 学位」唯一）
     * 
     * @param template 模板
     * @return 结果
     */
    public int insertAbrMaterialTemplate(AbrMaterialTemplate template);

    /**
     * 修改模板（校验材料列表与「国家 / 地区 × 学位」唯一）
     * 
     * @param template 模板
     * @return 结果
     */
    public int updateAbrMaterialTemplate(AbrMaterialTemplate template);

    /**
     * 批量删除模板
     * 
     * @param templateIds 模板ID
     * @return 结果
     */
    public int deleteAbrMaterialTemplateByIds(Long[] templateIds);
}
