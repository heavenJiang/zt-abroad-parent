package com.ygb.abroad.mapper;

import com.ygb.abroad.domain.AbrProfile;

/**
 * 背景档案 数据层
 * 
 * @author ygb
 */
public interface AbrProfileMapper
{
    /**
     * 按测评查询背景档案
     * 
     * @param assessmentId 测评ID
     * @return 背景档案，没有返回 null
     */
    public AbrProfile selectAbrProfileByAssessmentId(Long assessmentId);

    /**
     * 写入或覆盖背景档案（按测评ID）
     * 
     * @param profile 背景档案
     * @return 结果
     */
    public int upsertAbrProfile(AbrProfile profile);
}
