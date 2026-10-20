package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.domain.AbrCase;
import com.ygb.abroad.domain.vo.AppOfferVo;

/**
 * 录取案例与 offer 库（M9-01）
 *
 * @author ygb
 */
public interface IAbrCaseService
{
    /** 取得宣传授权时写的事件 */
    public static final String EVENT_OFFER_AUTHORIZED = "offer_authorized";

    /**
     * 查询案例
     *
     * @param c 条件
     * @return 列表
     */
    public List<AbrCase> selectCaseList(AbrCase c);

    /**
     * 案例详情
     *
     * @param caseId 案例ID
     * @return 案例
     */
    public AbrCase selectCaseById(Long caseId);

    /**
     * 新增或修改（同一线索同一项目同一申请季只能一条；授权从无到有记授权时间、全部撤回记撤回时间；新取得宣传授权且可公开时写 offer_authorized 事件）
     *
     * @param c 案例
     * @param keepIdentity 是否保留原关联线索（操作人没有 abroad:case:identity 权限时为 true）
     * @return 案例ID
     */
    public Long saveCase(AbrCase c, boolean keepIdentity);

    /**
     * 撤回全部授权（算法与宣传）
     *
     * @param caseId 案例ID
     * @param operator 操作人
     * @return 行数
     */
    public int revoke(Long caseId, String operator);

    /**
     * 删除案例（连同 offer 截图）
     *
     * @param caseIds 案例ID
     * @return 行数
     */
    public int deleteCases(Long[] caseIds);

    /**
     * 上传 offer 截图（替换原截图）
     *
     * @param caseId 案例ID
     * @param bytes 内容
     * @param ext 原文件扩展名（不采用，格式按文件头判断）
     * @param operator 操作人
     */
    public void saveOfferFile(Long caseId, byte[] bytes, String ext, String operator);

    /**
     * 读取 offer 截图
     *
     * @param caseId 案例ID
     * @return 内容与扩展名
     */
    public Object[] readOfferFile(Long caseId);

    /**
     * 员工预览 offer 卡（不可公开时报原因）
     *
     * @param caseId 案例ID
     * @return PNG
     */
    public byte[] cardPng(Long caseId);

    /**
     * C 端 offer 墙（只含可公开的录取）
     *
     * @param country 国家 / 地区（可空）
     * @param degree 学段（可空）
     * @return 列表
     */
    public List<AppOfferVo> publicOffers(String country, String degree);

    /**
     * C 端 offer 卡（不可公开时当作不存在）
     *
     * @param caseId 案例ID
     * @return PNG
     */
    public byte[] publicCardPng(Long caseId);

    /**
     * 能否公开引用（合规闸门核对内容引用的 case / offer）
     *
     * @param caseId 案例ID
     * @return 可公开的案例（不可公开为 null）
     */
    public AbrCase publicCase(Long caseId);
}
