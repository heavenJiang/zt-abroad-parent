package com.ygb.abroad.service;

import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppQuickFormVo;
import com.ygb.abroad.domain.vo.AppToolBody;

/**
 * 查询类入口工具 服务层：本校认可度（recognition）、雅思够不够（ielts）、现在还能申（open）
 * 
 * @author ygb
 */
public interface IAbrLookupToolService
{
    /**
     * 工具输入页：题目与国家 / 地区开放情况
     * 
     * @param tool 工具编码
     * @param degree 学段（为空按硕士）
     * @return 输入页定义
     */
    public AppQuickFormVo selectForm(String tool, String degree);

    /**
     * 运行工具：校验 → 查询院校库（与引擎硬门槛口径一致）→ 留痕；采集个人信息的工具须同意当前隐私政策，答案写入访客渐进档案
     * 
     * @param tool 工具编码
     * @param visitor 访客
     * @param body 请求
     * @return 学生可见结果（AppRecognitionVo / AppIeltsVo / AppOpenProgramsVo）
     */
    public Object run(String tool, AbrVisitor visitor, AppToolBody body);
}
