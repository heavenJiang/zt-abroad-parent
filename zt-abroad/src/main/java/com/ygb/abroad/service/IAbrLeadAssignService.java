package com.ygb.abroad.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrConsultant;
import com.ygb.abroad.domain.AbrLeadAssignSetting;
import com.ygb.abroad.domain.AbrStaffNotice;

/**
 * 线索分配与 SLA 服务层：自动分配（按国家 / 学段匹配，轮询或按负载）、首次联系时限、超时提醒与升级、员工站内提醒
 *
 * @author ygb
 */
public interface IAbrLeadAssignService
{
    /** 事件：线索已分配 */
    public static final String EVENT_ASSIGNED = "lead_assigned";

    /**
     * 一轮：分配未分配的线索 → 超时提醒 → 升级（定时任务调用）
     *
     * @return assigned 分配数、overdue 超时数、escalated 升级数
     */
    public Map<String, Object> runRound();

    /**
     * 按当前设置计算首次联系到期时间
     *
     * @param grade 意向度
     * @param assigned 分配时间
     * @return 到期时间；该意向度没有时限为 null
     */
    public Date slaDue(String grade, Date assigned);

    /**
     * 写一条员工提醒（同时尝试企业微信；同一员工同一去重键只提醒一次）
     *
     * @param userId 员工
     * @param kind 类型
     * @param title 标题
     * @param content 内容
     * @param leadId 线索
     * @param dedupKey 去重键
     */
    public void notify(Long userId, String kind, String title, String content, Long leadId, String dedupKey);

    /**
     * 分配设置
     *
     * @return 设置
     */
    public AbrLeadAssignSetting selectSetting();

    /**
     * 修改分配设置
     *
     * @param setting 设置
     * @return 结果
     */
    public int updateSetting(AbrLeadAssignSetting setting);

    /**
     * 参与分配的顾问
     *
     * @return 顾问
     */
    public List<AbrConsultant> selectConsultants();

    /**
     * 新增或修改顾问（员工须在用；同一员工只有一条）
     *
     * @param consultant 顾问
     * @return 结果
     */
    public int saveConsultant(AbrConsultant consultant);

    /**
     * 删除顾问
     *
     * @param consultantIds ID
     * @return 结果
     */
    public int deleteConsultants(Long[] consultantIds);

    /**
     * 我的提醒
     *
     * @param userId 员工
     * @param unreadOnly 只看未读
     * @return 提醒
     */
    public List<AbrStaffNotice> myNotices(Long userId, boolean unreadOnly);

    /**
     * 未读数
     *
     * @param userId 员工
     * @return 数量
     */
    public int unreadCount(Long userId);

    /**
     * 标为已读（ids 为空时全部）
     *
     * @param userId 员工
     * @param ids 提醒ID
     * @return 影响行数
     */
    public int markRead(Long userId, Long[] ids);
}
