package com.ygb.abroad.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrLead;
import com.ygb.abroad.domain.AbrLeadFollow;
import com.ygb.abroad.domain.AbrLeadTouch;

/**
 * 线索 数据层
 *
 * @author ygb
 */
public interface AbrLeadMapper
{
    /**
     * 查询线索列表（不含已合并的；阶段、首次来源、首次渠道、是否未成年精确；params.keyword 称呼模糊，params.phoneHash 手机号；
     * params.beginTime / endTime 首次触达日期）
     *
     * @param lead 查询条件
     * @return 线索集合
     */
    public List<AbrLead> selectAbrLeadList(AbrLead lead);

    /**
     * 按ID查询线索（含已合并的）
     *
     * @param leadId 线索ID
     * @return 线索
     */
    public AbrLead selectAbrLeadById(Long leadId);

    /**
     * 按身份查询线索（含已合并的，调用方沿 merged_into 找到保留的一条）
     *
     * @param appUserId 学生账号ID（可为 null）
     * @param unionid unionid（可为 null）
     * @param phoneHash 手机号哈希（可为 null）
     * @return 线索
     */
    public List<AbrLead> selectByIdentity(@Param("appUserId") Long appUserId, @Param("unionid") String unionid, @Param("phoneHash") String phoneHash);

    /**
     * 新增线索
     *
     * @param lead 线索
     * @return 结果
     */
    public int insertAbrLead(AbrLead lead);

    /**
     * 补上身份信息（只填原来为空的）；未成年标记只会从 N 变为 Y
     *
     * @param lead 线索（leadId、displayName、phone、phoneHash、unionid、appUserId、visitorId、minor）
     * @return 结果
     */
    public int fillIdentity(AbrLead lead);

    /**
     * 记一次触达：更早时改首次来源，更晚时改最近来源，次数加 count
     *
     * @param leadId 线索ID
     * @param source 来源
     * @param channel 渠道码
     * @param content 内容编号
     * @param time 发生时间
     * @param count 次数增量
     * @return 结果
     */
    public int recordTouch(@Param("leadId") Long leadId, @Param("source") String source, @Param("channel") String channel,
            @Param("content") String content, @Param("time") Date time, @Param("count") int count);

    /**
     * 标记已合并（唯一索引不含已合并的线索，身份字段保留原值，按身份查到时沿 merged_into 找到保留的一条）
     *
     * @param leadId 被合并的线索
     * @param intoId 保留的线索
     * @return 结果
     */
    public int markMerged(@Param("leadId") Long leadId, @Param("intoId") Long intoId);

    /**
     * 新增触达记录（同一业务事件只记一次）
     *
     * @param touch 记录
     * @return 写入条数（重复事件为 0）
     */
    public int insertTouch(AbrLeadTouch touch);

    /**
     * 把触达记录移到保留的线索
     *
     * @param fromId 被合并的线索
     * @param toId 保留的线索
     * @return 结果
     */
    public int moveTouches(@Param("fromId") Long fromId, @Param("toId") Long toId);

    /**
     * 时间线：触达记录 + 主访客的业务事件（已作为触达记录的除外）、工具使用、测评
     *
     * @param leadId 线索ID
     * @param visitorId 主访客ID（可为 null）
     * @param appUserId 学生账号ID（可为 null）
     * @param limit 最多条数
     * @return [{time, kind, type, source, summary, channel, content, refType, refId}]
     */
    public List<Map<String, Object>> selectTimeline(@Param("leadId") Long leadId, @Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId,
            @Param("limit") int limit);

    /**
     * 访客渐进档案中的年龄段（问卷 age_band 的答案）
     *
     * @param visitorId 访客ID
     * @return 年龄段，没有为 null
     */
    public String selectVisitorAgeBand(Long visitorId);

    /**
     * 评分特征：转化时刻、入学年份、测评、近期工具使用、选校单、家长分享、来源
     *
     * @param leadId 线索ID
     * @param visitorId 主访客ID（可为 null）
     * @param appUserId 学生账号ID（可为 null）
     * @param since 工具使用统计起点
     * @return 特征
     */
    public Map<String, Object> selectScoreFeatures(@Param("leadId") Long leadId, @Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId,
            @Param("since") Date since);

    /**
     * 写入评分（顾问修改过等级的只更新分数、规则等级与理由）
     *
     * @param leadId 线索ID
     * @param score 总分
     * @param ruleGrade 规则等级
     * @param reasons 理由（JSON 数组）
     * @return 结果
     */
    public int updateScore(@Param("leadId") Long leadId, @Param("score") int score, @Param("ruleGrade") String ruleGrade, @Param("reasons") String reasons);

    /**
     * 顾问修改等级
     *
     * @param leadId 线索ID
     * @param grade 等级
     * @param reason 原因
     * @param operator 操作人
     * @return 结果
     */
    public int setOverride(@Param("leadId") Long leadId, @Param("grade") String grade, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 撤销顾问修改，恢复规则等级
     *
     * @param leadId 线索ID
     * @param operator 操作人
     * @return 结果
     */
    public int clearOverride(@Param("leadId") Long leadId, @Param("operator") String operator);

    /**
     * 写等级历史
     *
     * @param leadId 线索ID
     * @param fromGrade 原等级
     * @param toGrade 新等级
     * @param score 规则总分
     * @param ruleGrade 规则等级
     * @param changeType 类型（rule / manual / reset）
     * @param reason 原因
     * @param operator 操作人
     * @return 结果
     */
    public int insertScoreLog(@Param("leadId") Long leadId, @Param("fromGrade") String fromGrade, @Param("toGrade") String toGrade, @Param("score") Integer score,
            @Param("ruleGrade") String ruleGrade, @Param("changeType") String changeType, @Param("reason") String reason, @Param("operator") String operator);

    /**
     * 等级历史
     *
     * @param leadId 线索ID
     * @return 记录
     */
    public List<Map<String, Object>> selectScoreLog(Long leadId);

    /**
     * 需要重算的线索（未合并、未签约、未流失）
     *
     * @return 线索ID
     */
    public List<Long> selectScorableLeadIds();

    /**
     * 访客或学生账号对应的线索
     *
     * @param visitorId 访客ID（可为 null）
     * @param appUserId 学生账号ID（可为 null）
     * @return 线索ID，没有为 null
     */
    public Long selectLeadIdByVisitor(@Param("visitorId") Long visitorId, @Param("appUserId") Long appUserId);

    /**
     * 修改称呼与备注
     *
     * @param lead 线索（leadId、displayName、remark、updateBy）
     * @return 结果
     */
    public int updateBasic(AbrLead lead);

    /**
     * 记跟进：最近跟进时间、下次联系时间；新线索第一次跟进后进入“已联系”
     *
     * @param leadId 线索ID
     * @param nextFollowTime 下次联系时间（可为 null）
     * @param operator 操作人
     * @return 结果
     */
    public int recordFollow(@Param("leadId") Long leadId, @Param("nextFollowTime") Date nextFollowTime, @Param("operator") String operator);

    /**
     * 改阶段（签约、流失时清空下次联系时间）
     *
     * @param leadId 线索ID
     * @param stage 阶段
     * @param lostReason 流失原因（其他阶段为空串）
     * @param operator 操作人
     * @return 结果
     */
    public int updateStage(@Param("leadId") Long leadId, @Param("stage") String stage, @Param("lostReason") String lostReason,
            @Param("operator") String operator);

    /**
     * 改负责人
     *
     * @param leadId 线索ID
     * @param ownerUserId 负责人（sys_user）
     * @param operator 操作人
     * @return 结果
     */
    public int updateOwner(@Param("leadId") Long leadId, @Param("ownerUserId") Long ownerUserId, @Param("operator") String operator);

    /**
     * 新增跟进记录
     *
     * @param follow 记录
     * @return 结果
     */
    public int insertFollow(AbrLeadFollow follow);

    /**
     * 线索的跟进记录（最近 500 条）
     *
     * @param leadId 线索ID
     * @return 记录
     */
    public List<AbrLeadFollow> selectFollows(Long leadId);

    /**
     * 员工是否在用
     *
     * @param userId 员工ID
     * @return 1 在用，0 不存在或已停用
     */
    public int countActiveUser(Long userId);
}
