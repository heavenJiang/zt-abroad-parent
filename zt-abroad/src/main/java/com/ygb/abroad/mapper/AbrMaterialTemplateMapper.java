package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrMaterialTemplate;

/**
 * 默认材料模板 数据层
 * 
 * @author ygb
 */
public interface AbrMaterialTemplateMapper
{
    /**
     * 查询模板列表
     * 
     * @param template 查询条件（国家 / 地区、学位、状态）
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
     * 查询正常状态的全部模板（材料清单用）
     * 
     * @return 模板集合
     */
    public List<AbrMaterialTemplate> selectActiveAbrMaterialTemplates();

    /**
     * 校验「国家 / 地区 × 学位」是否唯一
     * 
     * @param template 模板
     * @return 已存在的模板，没有返回 null
     */
    public AbrMaterialTemplate checkAbrMaterialTemplateUnique(AbrMaterialTemplate template);

    /**
     * 新增模板
     * 
     * @param template 模板
     * @return 结果
     */
    public int insertAbrMaterialTemplate(AbrMaterialTemplate template);

    /**
     * 修改模板
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
