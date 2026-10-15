package com.ygb.abroad.domain.vo;

import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 引擎测试台运行请求
 * 
 * @author ygb
 */
public class AbrEngineBenchBody
{
    /** 案例 CSV 文本（格式见 seed/engine-cases/README.md） */
    private String csv;

    /** 国家 / 地区 → 配置ID（不填的国家用启用版本，没有启用版本时用最新导入的草稿） */
    private Map<String, Long> configIds;

    @NotBlank(message = "请上传案例 CSV")
    @Size(max = 2097152, message = "案例 CSV 不能超过 2 MB")
    public String getCsv()
    {
        return csv;
    }

    public void setCsv(String csv)
    {
        this.csv = csv;
    }

    public Map<String, Long> getConfigIds()
    {
        return configIds;
    }

    public void setConfigIds(Map<String, Long> configIds)
    {
        this.configIds = configIds;
    }
}
