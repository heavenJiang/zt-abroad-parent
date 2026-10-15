package com.ygb.abroad.service;

import com.ygb.abroad.domain.vo.AppReportVo;

/**
 * 测评解读（report_explainer）服务层：异步生成定位、学校理由与短板说明，未返回或失败时用模板文字
 * 
 * @author ygb
 */
public interface IAbrReportExplanationService
{
    /**
     * 给报告配上解读：已生成的直接合并；没有、选校结果已重新计算或生成中断时认领并在后台生成，本次先返回模板文字（状态 pending）
     * 
     * @param report 报告结构化部分（mode 不是 full 时原样返回）
     * @param degreeLabel 学段名称
     * @param fieldLabel 专业方向名称
     * @return 带解读的报告
     */
    public AppReportVo explain(AppReportVo report, String degreeLabel, String fieldLabel);
}
