package com.ygb.abroad.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.domain.AbrMaterialTemplate;
import com.ygb.abroad.mapper.AbrMaterialTemplateMapper;
import com.ygb.abroad.service.IAbrMaterialTemplateService;
import com.ygb.common.exception.ServiceException;

/**
 * 默认材料模板 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrMaterialTemplateServiceImpl implements IAbrMaterialTemplateService
{
    @Autowired
    private AbrMaterialTemplateMapper templateMapper;

    @Override
    public List<AbrMaterialTemplate> selectAbrMaterialTemplateList(AbrMaterialTemplate template)
    {
        return templateMapper.selectAbrMaterialTemplateList(template);
    }

    @Override
    public AbrMaterialTemplate selectAbrMaterialTemplateById(Long templateId)
    {
        return templateMapper.selectAbrMaterialTemplateById(templateId);
    }

    @Override
    public int insertAbrMaterialTemplate(AbrMaterialTemplate template)
    {
        check(template);
        return templateMapper.insertAbrMaterialTemplate(template);
    }

    @Override
    public int updateAbrMaterialTemplate(AbrMaterialTemplate template)
    {
        if (template.getTemplateId() == null || templateMapper.selectAbrMaterialTemplateById(template.getTemplateId()) == null)
        {
            throw new ServiceException("模板不存在");
        }
        check(template);
        return templateMapper.updateAbrMaterialTemplate(template);
    }

    @Override
    public int deleteAbrMaterialTemplateByIds(Long[] templateIds)
    {
        return templateMapper.deleteAbrMaterialTemplateByIds(templateIds);
    }

    /**
     * 校验材料列表（不能为空）与「国家 / 地区 × 学位」唯一
     */
    private void check(AbrMaterialTemplate template)
    {
        String items = AbrMaterials.normalize(template.getItems(), AbrMaterials.types().keySet());
        if (items == null || "[]".equals(items))
        {
            throw new ServiceException("请至少填写一项材料");
        }
        template.setItems(items);
        AbrMaterialTemplate existing = templateMapper.checkAbrMaterialTemplateUnique(template);
        if (existing != null && !existing.getTemplateId().equals(template.getTemplateId()))
        {
            throw new ServiceException("该国家 / 地区与学位的模板已存在");
        }
    }
}
