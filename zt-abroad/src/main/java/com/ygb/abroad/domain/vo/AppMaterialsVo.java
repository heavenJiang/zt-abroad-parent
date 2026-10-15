package com.ygb.abroad.domain.vo;

import java.util.List;

/**
 * C 端：材料清单（按选校单项目的申请要求汇总；项目没写材料要求时按国家默认模板，标为一般要求）
 *
 * @param doneCount 已完成的材料数（共用材料算一份，每个项目单独准备的按项目分别算）
 * @param totalCount 材料总数
 * @param shared 各项目共用的材料（成绩单、语言成绩、推荐信等，准备一份即可）
 * @param programs 每个项目的材料（含共用材料与该项目单独准备的材料）
 * @param note 说明
 * @author ygb
 */
public record AppMaterialsVo(int doneCount, int totalCount, List<Shared> shared, List<ProgramMaterials> programs, String note)
{
    /**
     * 共用材料
     *
     * @param key 材料键（勾选完成时提交）
     * @param code 材料类型
     * @param label 名称
     * @param count 份数（各项目要求的最大值；没有要求为 null）
     * @param done 是否已完成
     * @param programs 需要该材料的项目
     */
    public record Shared(String key, String code, String label, Integer count, boolean done, List<Need> programs)
    {
    }

    /**
     * 某项目对共用材料的要求
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param count 份数
     * @param note 说明
     * @param general 是否按国家一般要求（项目未写明）
     */
    public record Need(Long programId, String schoolName, String programName, Integer count, String note, boolean general)
    {
    }

    /**
     * 一个项目的材料
     *
     * @param programId 项目ID
     * @param schoolName 院校
     * @param programName 项目
     * @param countryLabel 国家 / 地区名称
     * @param nextDeadline 最近的未过截止日（yyyy-MM-dd；没有或未核实为 null）
     * @param doneCount 已完成数
     * @param totalCount 总数
     * @param unverified 项目信息超过 90 天未核实（份数与说明不展示，以官网为准）
     * @param noTemplate 项目没写材料要求且该国家没有默认模板（只列出能从申请要求推出的材料）
     * @param items 材料
     */
    public record ProgramMaterials(Long programId, String schoolName, String programName, String countryLabel, String nextDeadline, int doneCount,
            int totalCount, boolean unverified, boolean noTemplate, List<Item> items)
    {
    }

    /**
     * 项目的一项材料
     *
     * @param key 材料键（共用材料与共用列表中的键相同）
     * @param code 材料类型
     * @param label 名称
     * @param scope shared 共用 / program 该项目单独准备
     * @param count 份数
     * @param note 说明
     * @param general 是否按国家一般要求（项目未写明）
     * @param done 是否已完成
     */
    public record Item(String key, String code, String label, String scope, Integer count, String note, boolean general, boolean done)
    {
    }
}
