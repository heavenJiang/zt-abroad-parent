package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.vo.AbrMetricsImportRow;
import com.ygb.common.exception.ServiceException;

/**
 * 效果数据规则测试：规范化（只留约定指标、整数、范围）、导入行转指标
 *
 * @author ygb
 */
class AbrMetricsRulesTest
{
    @Test
    void normalize()
    {
        Map<String, Object> in = new HashMap<>();
        in.put("views", "1,234");
        in.put("likes", 56);
        in.put("dms", 3.0);
        in.put("unknown", 9);
        in.put("shares", "");
        assertEquals("{views=1234, likes=56, dms=3}", AbrMetricsRules.normalize(in).toString());
        assertThrows(ServiceException.class, () -> AbrMetricsRules.normalize(Map.of("views", -1)));
        assertThrows(ServiceException.class, () -> AbrMetricsRules.normalize(Map.of("views", 1.5)));
        assertThrows(ServiceException.class, () -> AbrMetricsRules.normalize(Map.of("views", "多")));
        assertThrows(ServiceException.class, () -> AbrMetricsRules.normalize(Map.of("views", 2_000_000_000L)));
        assertThrows(ServiceException.class, () -> AbrMetricsRules.normalize(Map.of("other", 1)));
        assertThrows(ServiceException.class, () -> AbrMetricsRules.normalize(null));
    }

    @Test
    void fromRow()
    {
        AbrMetricsImportRow row = new AbrMetricsImportRow();
        row.setViews("100");
        row.setFollows("2");
        row.setLikes("");
        assertEquals("{views=100, follows=2}", AbrMetricsRules.normalize(AbrMetricsRules.fromRow(row)).toString());
    }
}
