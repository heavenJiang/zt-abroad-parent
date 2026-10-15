package com.ygb.abroad.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 引擎配置导入 / 校验请求
 * 
 * @author ygb
 */
public class AbrEngineConfigBody
{
    /** 引擎配置 JSON（完整文件内容） */
    private String config;

    /** 备注 */
    private String remark;

    @NotBlank(message = "引擎配置 JSON 不能为空")
    @Size(max = 1048576, message = "引擎配置 JSON 不能超过 1 MB")
    public String getConfig()
    {
        return config;
    }

    public void setConfig(String config)
    {
        this.config = config;
    }

    @Size(max = 500, message = "备注不能超过 500 个字符")
    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
