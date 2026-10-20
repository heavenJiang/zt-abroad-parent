package com.ygb.abroad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ygb.abroad.domain.AbrCase;

/**
 * 录取案例与 offer 数据层
 *
 * @author ygb
 */
public interface AbrCaseMapper
{
    /**
     * 查询案例列表（params[consent]=publicity 只看有宣传授权的）
     *
     * @param c 条件
     * @return 列表
     */
    public List<AbrCase> selectCaseList(AbrCase c);

    /**
     * 查询案例
     *
     * @param caseId 案例ID
     * @return 案例
     */
    public AbrCase selectCaseById(Long caseId);

    /**
     * 可公开展示的录取（录取、核验 A / B、宣传授权未撤回、状态正常），按申请季新到旧
     *
     * @param country 国家 / 地区（可空）
     * @param degree 学段（可空）
     * @return 列表
     */
    public List<AbrCase> selectPublicOffers(@Param("country") String country, @Param("degree") String degree);

    /**
     * 同一线索同一项目同一申请季的案例数（排除自己）
     *
     * @param leadId 线索
     * @param programId 项目
     * @param season 申请季
     * @param caseId 排除的案例ID
     * @return 数量
     */
    public int countDuplicate(@Param("leadId") Long leadId, @Param("programId") Long programId, @Param("season") String season,
            @Param("caseId") Long caseId);

    /**
     * 新增案例
     *
     * @param c 案例
     * @return 行数
     */
    public int insertCase(AbrCase c);

    /**
     * 修改案例（不含 offer 截图）
     *
     * @param c 案例
     * @return 行数
     */
    public int updateCase(AbrCase c);

    /**
     * 更新 offer 截图路径
     *
     * @param caseId 案例ID
     * @param offerFile 相对路径
     * @param operator 操作人
     * @return 行数
     */
    public int updateOfferFile(@Param("caseId") Long caseId, @Param("offerFile") String offerFile, @Param("operator") String operator);

    /**
     * 删除案例
     *
     * @param caseIds 案例ID
     * @return 行数
     */
    public int deleteCaseByIds(Long[] caseIds);
}
