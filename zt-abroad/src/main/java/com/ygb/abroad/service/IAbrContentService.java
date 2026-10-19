package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrContent;

/**
 * 内容 服务层：稿件、内容编号与私信口令、合规闸门结果、人工确认
 *
 * 状态：草稿 →（闸门通过）→ 可发布，或执行模式为人工确认时 → 待人工确认 →（确认）→ 可发布 →（发布记录回填）→ 已发布；
 * 改动标题、正文、话题、图卡、引用、文末工具、账号或口令后回到草稿并须重新过闸门。
 * 
 * @author ygb
 */
public interface IAbrContentService
{
    /**
     * 查询内容列表
     * 
     * @param content 查询条件
     * @return 内容集合
     */
    public List<AbrContent> selectAbrContentList(AbrContent content);

    /**
     * 按ID查询内容
     * 
     * @param contentId 内容ID
     * @return 内容
     */
    public AbrContent selectAbrContentById(Long contentId);

    /**
     * 按私信口令找到内容（engagement_agent 识别口令用；忽略首尾空白）
     * 
     * @param codeword 口令
     * @return 内容，没有返回 null
     */
    public AbrContent selectAbrContentByCodeword(String codeword);

    /**
     * 按内容编号找到内容（归因用）
     * 
     * @param contentCode 内容编号
     * @return 内容，没有返回 null
     */
    public AbrContent selectAbrContentByCode(String contentCode);

    /**
     * 员工新增内容（草稿；生成内容编号、口令与工具链接）
     * 
     * @param content 内容
     * @return 新增的内容
     */
    public AbrContent insertAbrContent(AbrContent content);

    /**
     * Agent 生成内容（content_writer 等；AI 生成标识为 Y，执行模式为人工确认时须确认后才能发布；Agent 不能运行时拒绝）
     * 
     * @param content 内容
     * @param agentCode Agent 编码
     * @param aiCallId AI 调用ID（可为 null）
     * @return 新增的内容
     */
    public AbrContent insertAgentContent(AbrContent content, String agentCode, Long aiCallId);

    /**
     * 修改内容（已发布、已归档的不能改；改动稿件后须重新过闸门）
     * 
     * @param content 内容
     * @return 结果
     */
    public int updateAbrContent(AbrContent content);

    /**
     * 写入合规闸门结果（compliance_checker，M6-03）；Agent 生成的内容同时上报合规通过 / 拦截信号（熔断统计拦截率）
     * 
     * @param contentId 内容ID
     * @param passed 是否通过
     * @param resultJson 明细（JSON 对象）
     * @param checker 检查人
     * @return 写入后的内容
     */
    public AbrContent recordGateResult(Long contentId, boolean passed, String resultJson, String checker);

    /**
     * 员工审核闸门（M6-03 上线前使用；结果记为人工审核通过或拦截）
     * 
     * @param contentId 内容ID
     * @param passed 是否通过
     * @param note 审核意见
     * @param operator 审核人
     * @return 写入后的内容
     */
    public AbrContent reviewGate(Long contentId, boolean passed, String note, String operator);

    /**
     * 人工确认（待人工确认 → 可发布）
     * 
     * @param contentId 内容ID
     * @param operator 确认人
     * @return 是否有变化
     */
    public boolean confirmContent(Long contentId, String operator);

    /**
     * 删除内容：没有发布记录的直接删除，有的改为归档（口令释放）
     * 
     * @param contentIds 内容ID
     * @param operator 操作人
     * @return 删除与归档的条数
     */
    public int removeContents(Long[] contentIds, String operator);
}
