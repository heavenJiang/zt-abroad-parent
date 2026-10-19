package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrContent;

/**
 * 内容 数据层
 * 
 * @author ygb
 */
public interface AbrContentMapper
{
    /**
     * 查询内容列表（带出账号编码、名称、平台）
     * 
     * @param content 查询条件（标题模糊、内容编号、口令、类型、账号、剧本、状态、闸门结果、创建时间区间）
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
     * 按内容编号查询
     * 
     * @param contentCode 内容编号
     * @return 内容
     */
    public AbrContent selectAbrContentByCode(String contentCode);

    /**
     * 按私信口令查询未归档的内容
     * 
     * @param codeword 口令
     * @return 内容
     */
    public AbrContent selectAbrContentByCodeword(String codeword);

    /**
     * 口令是否已被其他未归档内容使用
     * 
     * @param codeword 口令
     * @param excludeId 排除的内容ID（可为 null）
     * @return 是否已使用
     */
    public boolean existsCodeword(@Param("codeword") String codeword, @Param("excludeId") Long excludeId);

    /**
     * 新增内容
     * 
     * @param content 内容
     * @return 结果
     */
    public int insertAbrContent(AbrContent content);

    /**
     * 修改内容（稿件字段；闸门与状态由 service 计算后一起写入）
     * 
     * @param content 内容
     * @return 结果
     */
    public int updateAbrContent(AbrContent content);

    /**
     * 写入闸门结果与状态
     * 
     * @param content 内容（contentId、gateStatus、gateResult、gateBy、status）
     * @return 结果
     */
    public int updateContentGate(AbrContent content);

    /**
     * 人工确认（待人工确认 → 可发布）
     * 
     * @param contentId 内容ID
     * @param confirmBy 确认人
     * @return 影响行数（不是待人工确认为 0）
     */
    public int confirmContent(@Param("contentId") Long contentId, @Param("confirmBy") String confirmBy);

    /**
     * 修改状态（发布、归档）
     * 
     * @param contentId 内容ID
     * @param status 状态
     * @param updateBy 操作人
     * @return 结果
     */
    public int updateContentStatus(@Param("contentId") Long contentId, @Param("status") String status, @Param("updateBy") String updateBy);

    /**
     * 删除内容（只删没有发布记录的）
     * 
     * @param contentIds 内容ID
     * @return 结果
     */
    public int deleteUnpublishedContentByIds(Long[] contentIds);
}
