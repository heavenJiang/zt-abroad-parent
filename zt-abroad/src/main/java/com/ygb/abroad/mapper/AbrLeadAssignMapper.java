package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrConsultant;
import com.ygb.abroad.domain.AbrLeadAssignSetting;
import com.ygb.abroad.domain.AbrStaffNotice;

/**
 * 线索分配、SLA 与员工提醒 数据层
 *
 * @author ygb
 */
public interface AbrLeadAssignMapper
{
    /**
     * 参与分配的顾问（带出姓名与进行中线索数）
     *
     * @return 顾问
     */
    public List<AbrConsultant> selectConsultants();

    /**
     * 按ID查询顾问
     *
     * @param consultantId ID
     * @return 顾问
     */
    public AbrConsultant selectConsultantById(Long consultantId);

    /**
     * 按员工查询顾问
     *
     * @param userId 员工ID
     * @return 顾问
     */
    public AbrConsultant selectConsultantByUser(Long userId);

    /**
     * 新增顾问
     *
     * @param c 顾问
     * @return 结果
     */
    public int insertConsultant(AbrConsultant c);

    /**
     * 修改顾问（擅长、上限、是否接单、备注）
     *
     * @param c 顾问
     * @return 结果
     */
    public int updateConsultant(AbrConsultant c);

    /**
     * 删除顾问（不再参与分配；已负责的线索不变）
     *
     * @param consultantIds ID
     * @return 结果
     */
    public int deleteConsultants(Long[] consultantIds);

    /**
     * 记录最近一次被自动分配的时间
     *
     * @param userId 员工ID
     * @return 结果
     */
    public int touchAssigned(Long userId);

    /**
     * 分配设置
     *
     * @return 设置
     */
    public AbrLeadAssignSetting selectSetting();

    /**
     * 修改分配设置
     *
     * @param s 设置
     * @return 结果
     */
    public int updateSetting(AbrLeadAssignSetting s);

    /**
     * 待自动分配的线索：未分配、未合并、未签约 / 流失、意向度在范围内
     *
     * @param grades 意向度
     * @param limit 最多条数
     * @return [leadId, grade, countries（JSON 文本）, degree]
     */
    public List<Map<String, Object>> selectUnassigned(@Param("grades") List<String> grades, @Param("limit") int limit);

    /**
     * 首次联系已到期、还没联系的线索（状态待联系）
     *
     * @param now 现在
     * @return [leadId, ownerUserId, displayName, grade, slaDueTime]
     */
    public List<Map<String, Object>> selectSlaOverdue(@Param("now") Date now);

    /**
     * 超时后仍没联系、到了升级时间的线索（状态已超时）
     *
     * @param before 到期时间早于它（现在 - 升级小时数）
     * @return [leadId, ownerUserId, displayName, grade, slaDueTime]
     */
    public List<Map<String, Object>> selectSlaToEscalate(@Param("before") Date before);

    /**
     * 改 SLA 状态（只从原状态改）
     *
     * @param leadId 线索ID
     * @param from 原状态
     * @param to 新状态
     * @return 影响行数
     */
    public int updateSlaStatus(@Param("leadId") Long leadId, @Param("from") String from, @Param("to") String to);

    /**
     * 新增提醒（同一员工同一去重键只写一次）
     *
     * @param n 提醒
     * @return 写入条数
     */
    public int insertNotice(AbrStaffNotice n);

    /**
     * 员工的提醒（最近 200 条）
     *
     * @param userId 员工ID
     * @param unreadOnly 只看未读
     * @return 提醒
     */
    public List<AbrStaffNotice> selectNotices(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly);

    /**
     * 未读数
     *
     * @param userId 员工ID
     * @return 数量
     */
    public int countUnread(Long userId);

    /**
     * 标为已读（ids 为空时全部已读）
     *
     * @param userId 员工ID
     * @param ids 提醒ID
     * @return 影响行数
     */
    public int markRead(@Param("userId") Long userId, @Param("ids") Long[] ids);
}
