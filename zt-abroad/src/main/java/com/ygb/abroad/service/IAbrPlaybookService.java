package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrPlaybook;

/**
 * 营销剧本 服务层
 * 
 * @author ygb
 */
public interface IAbrPlaybookService
{
    /**
     * 查询剧本列表
     * 
     * @param playbook 查询条件
     * @return 剧本集合
     */
    public List<AbrPlaybook> selectAbrPlaybookList(AbrPlaybook playbook);

    /**
     * 按ID查询剧本
     * 
     * @param playbookId 剧本ID
     * @return 剧本
     */
    public AbrPlaybook selectAbrPlaybookById(Long playbookId);

    /**
     * 校验剧本编码是否唯一
     * 
     * @param playbook 剧本
     * @return true 唯一
     */
    public boolean checkPlaybookCodeUnique(AbrPlaybook playbook);

    /**
     * 新增剧本（校验学段、国家 / 地区、钩子、内容配比）
     * 
     * @param playbook 剧本
     * @return 结果
     */
    public int insertAbrPlaybook(AbrPlaybook playbook);

    /**
     * 修改剧本
     * 
     * @param playbook 剧本
     * @return 结果
     */
    public int updateAbrPlaybook(AbrPlaybook playbook);

    /**
     * 批量删除剧本（被日历或触发规则引用时不能删除）
     * 
     * @param playbookIds 剧本ID
     * @return 结果
     */
    public int deleteAbrPlaybookByIds(Long[] playbookIds);
}
