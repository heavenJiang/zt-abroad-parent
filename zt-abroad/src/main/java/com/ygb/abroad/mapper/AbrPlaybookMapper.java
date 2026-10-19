package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrPlaybook;

/**
 * 营销剧本 数据层
 * 
 * @author ygb
 */
public interface AbrPlaybookMapper
{
    /**
     * 查询剧本列表
     * 
     * @param playbook 查询条件（编码、名称模糊，状态）
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
     * 按编码查询剧本（校验唯一）
     * 
     * @param playbookCode 剧本编码
     * @return 剧本
     */
    public AbrPlaybook checkPlaybookCodeUnique(String playbookCode);

    /**
     * 新增剧本
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
     * 批量删除剧本
     * 
     * @param playbookIds 剧本ID
     * @return 结果
     */
    public int deleteAbrPlaybookByIds(Long[] playbookIds);

    /**
     * 引用这些剧本的日历条目与触发规则数
     * 
     * @param playbookIds 剧本ID
     * @return 引用数
     */
    public int countPlaybookReferences(Long[] playbookIds);
}
