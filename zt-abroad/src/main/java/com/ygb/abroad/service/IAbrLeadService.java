package com.ygb.abroad.service;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadFollow;
import com.ygb.abroad.domain.vo.AbrLeadCapture;
import com.ygb.abroad.domain.vo.AbrLeadImportRow;

/**
 * 线索 服务层：多来源收口、去重合并、首次 / 最近触达、时间线
 *
 * @author ygb
 */
public interface IAbrLeadService
{
    /** 事件：新线索（lead_scorer M5-02、nurture_agent M8-03 订阅） */
    public static final String EVENT_CAPTURED = "lead_captured";

    /**
     * 收口一次来源或触达：按 学生账号 → unionid → 手机号哈希 找到线索（几条指向同一人时合并，保留首次触达最早的一条），
     * 没有时新建；同一业务事件只记一次
     *
     * @param capture 来源与身份
     * @return 结果：lead（保留的线索）、created（是否新建）、merged（本次合并的线索ID）、recorded（是否记入，重复事件为 false）
     */
    public Map<String, Object> capture(AbrLeadCapture capture);

    /**
     * 读取业务事件收口（定时任务调用；独立游标 lead_intake）：有学生账号的事件归到对应线索
     *
     * @return 读取事件数、新建线索数、记入数
     */
    public Map<String, Object> ingestEvents();

    /**
     * 查询线索列表（手机号脱敏；params.keyword 为 11 位手机号时按手机号精确查找）
     *
     * @param lead 查询条件
     * @return 线索集合
     */
    public List<AbrLead> selectAbrLeadList(AbrLead lead);

    /**
     * 线索详情（手机号脱敏；已合并的返回保留的一条）
     *
     * @param leadId 线索ID
     * @return 线索
     */
    public AbrLead selectAbrLeadById(Long leadId);

    /**
     * 时间线（触达记录、工具使用、测评、选校单等，按时间倒序）
     *
     * @param leadId 线索ID
     * @param limit 最多条数（1–500）
     * @return 条目
     */
    public List<Map<String, Object>> timeline(Long leadId, int limit);

    /**
     * 在用员工（转给同事的候选，只含姓名与登录名）
     *
     * @return userId、nickName、userName
     */
    public List<Map<String, Object>> selectActiveStaff();

    /**
     * 手机号明文（须单独权限，调用方记操作日志）
     *
     * @param leadId 线索ID
     * @return 手机号，没有为空串
     */
    public String selectPhone(Long leadId);

    /**
     * 员工录入（须有手机号；已有同一人的线索时只记一次来源）
     *
     * @param lead 称呼、手机号、首次来源、首次渠道码与内容编号、备注
     * @param operator 操作人
     * @return 收口结果
     */
    public Map<String, Object> insertManual(AbrLead lead, String operator);

    /**
     * 表格导入（每行一个来源记录；逐行去重）
     *
     * @param rows 行
     * @param operator 操作人
     * @return 新建数、记入已有线索数、失败的行与原因
     */
    public Map<String, Object> importLeads(List<AbrLeadImportRow> rows, String operator);

    /**
     * 修改称呼与备注
     *
     * @param lead 线索（leadId、displayName、remark）
     * @param operator 操作人
     * @return 结果
     */
    public int updateBasic(AbrLead lead, String operator);

    /**
     * 记跟进（方式、内容、下次联系时间；可同时改阶段）；新线索第一次跟进后进入“已联系”
     *
     * @param leadId 线索ID
     * @param follow 跟进
     * @param operator 操作人
     * @return 跟进后的线索
     */
    public AbrLead follow(Long leadId, AbrLeadFollow follow, String operator);

    /**
     * 改阶段（流失须填原因；签约、流失后不再提醒下次联系）
     *
     * @param leadId 线索ID
     * @param stage 阶段（字典 abr_lead_stage）
     * @param lostReason 流失原因
     * @param operator 操作人
     * @return 修改后的线索
     */
    public AbrLead changeStage(Long leadId, String stage, String lostReason, String operator);

    /**
     * 转给同事（须为在用员工；记一条转交记录）
     *
     * @param leadId 线索ID
     * @param ownerUserId 新负责人
     * @param reason 原因（可空）
     * @param operator 操作人
     * @return 修改后的线索
     */
    public AbrLead assign(Long leadId, Long ownerUserId, String reason, String operator);

    /**
     * 跟进记录
     *
     * @param leadId 线索ID
     * @return 记录（按时间倒序）
     */
    public List<AbrLeadFollow> follows(Long leadId);
}
