package com.ygb.abroad.service;

import com.ygb.abroad.domain.vo.AppOutcomeBody;
import com.ygb.abroad.domain.vo.AppOutcomeVo;

/**
 * 录取结果回填（M9-06）：学生回填选校单项目的结果（单独授权）进入案例库，首次回填按申请季发一份服务抵扣作为感谢
 *
 * @author ygb
 */
public interface IAbrOutcomeService
{
    /**
     * 我回填的结果与收到的感谢（须登录）
     *
     * @return 结果
     */
    public AppOutcomeVo mine();

    /**
     * 回填一条结果（须登录；同一项目同一申请季只能回填一次；未成年人不能在本人账号中同意展示）
     *
     * @param body 回填内容
     * @return 我的回填
     */
    public AppOutcomeVo submit(AppOutcomeBody body);

    /**
     * 为自己的一条回填上传 offer 截图（顾问核验后才可能展示）
     *
     * @param caseId 记录ID
     * @param bytes 内容
     * @param filename 原文件名
     * @return 我的回填
     */
    public AppOutcomeVo uploadOffer(Long caseId, byte[] bytes, String filename);

    /**
     * 撤回自己一条回填的全部授权
     *
     * @param caseId 记录ID
     * @return 我的回填
     */
    public AppOutcomeVo revoke(Long caseId);

    /**
     * 删除自己的一条回填（连同截图；已发的感谢不收回）
     *
     * @param caseId 记录ID
     * @return 我的回填
     */
    public AppOutcomeVo delete(Long caseId);
}
