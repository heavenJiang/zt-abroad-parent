package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 问卷导入 / 校验的请求体
 * 
 * @author ygb
 */
public class AbrQuestionnaireBody
{
    /** 问卷定义（完整 JSON 文本） */
    private String definition;

    /** 备注 */
    private String remark;

    @NotBlank(message = "请上传问卷 JSON")
    @Size(max = 1048576, message = "问卷 JSON 不能超过 1MB")
    public String getDefinition()
    {
        return definition;
    }

    public void setDefinition(String definition)
    {
        this.definition = definition;
    }

    @Size(max = 500, message = "备注不能超过500个字符")
    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
