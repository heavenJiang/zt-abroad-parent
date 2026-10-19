package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrProduct;
import com.ygb.common.exception.ServiceException;

/**
 * 产品与预约规则测试：价格展示、产品校验、批量生成时段（星期几、早于现在跳过、切分、上限、写法）
 *
 * @author ygb
 */
class AbrBookingRulesTest
{
    @Test
    void priceText()
    {
        assertEquals("价格面议", AbrBookingRules.priceText(null, null, "CNY", null));
        assertEquals("免费", AbrBookingRules.priceText(BigDecimal.ZERO, BigDecimal.ZERO, "CNY", ""));
        assertEquals("¥3,000", AbrBookingRules.priceText(new BigDecimal("3000"), new BigDecimal("3000"), "CNY", null));
        assertEquals("¥20,000–38,000（按国家数计价）", AbrBookingRules.priceText(new BigDecimal("20000"), new BigDecimal("38000"), "CNY", "按国家数计价"));
        assertEquals("¥999.5 起", AbrBookingRules.priceText(new BigDecimal("999.50"), null, null, null));
        assertEquals("HKD 500 以内", AbrBookingRules.priceText(null, new BigDecimal("500"), "hkd", null));
    }

    private static AbrProduct product(String category, String min, String max, String bookable, Integer minutes)
    {
        AbrProduct p = new AbrProduct();
        p.setCategory(category);
        p.setPriceMin(min == null ? null : new BigDecimal(min));
        p.setPriceMax(max == null ? null : new BigDecimal(max));
        p.setBookable(bookable);
        p.setDurationMinutes(minutes);
        return p;
    }

    @Test
    void validateProduct()
    {
        AbrProduct ok = product("consult", null, null, "Y", 20);
        AbrBookingRules.validate(ok);
        assertEquals("CNY", ok.getCurrency());
        assertEquals("0", ok.getStatus());
        assertThrows(ServiceException.class, () -> AbrBookingRules.validate(product("vip", null, null, "N", null)));
        assertThrows(ServiceException.class, () -> AbrBookingRules.validate(product("service", "-1", null, "N", null)));
        assertThrows(ServiceException.class, () -> AbrBookingRules.validate(product("service", "300", "200", "N", null)));
        assertThrows(ServiceException.class, () -> AbrBookingRules.validate(product("consult", null, null, "Y", null)));
        AbrProduct notBookable = product("service", null, null, "x", null);
        AbrBookingRules.validate(notBookable);
        assertEquals("N", notBookable.getBookable());
    }

    @Test
    void generateSlots()
    {
        LocalDate mon = LocalDate.of(2026, 10, 19);
        LocalDateTime now = mon.atTime(9, 40);
        List<LocalDateTime[]> s = AbrBookingRules.slots(mon, mon.plusDays(6), Set.of(1, 3), List.of("09:00-11:00", "14:00 - 15:10"), 30, now);
        // 周一 9:00、9:30 早于现在跳过 → 10:00、10:30、14:00、14:30（14:30-15:00；15:00-15:30 超出）；周三 6 个
        assertEquals(4 + 6, s.size());
        assertEquals(mon.atTime(10, 0), s.get(0)[0]);
        assertEquals(mon.atTime(10, 30), s.get(0)[1]);
        assertTrue(s.stream().allMatch(x -> Set.of(1, 3).contains(x[0].getDayOfWeek().getValue())));
        assertEquals(47, AbrBookingRules.slots(mon.plusDays(1), mon.plusDays(1), null, List.of("0:00-23:59"), 30, now).size(), "23:30-24:00 超出 23:59");
        assertThrows(ServiceException.class, () -> AbrBookingRules.slots(mon, mon.minusDays(1), null, List.of("09:00-10:00"), 30, now));
        assertThrows(ServiceException.class, () -> AbrBookingRules.slots(mon, mon.plusDays(100), null, List.of("09:00-10:00"), 30, now));
        assertThrows(ServiceException.class, () -> AbrBookingRules.slots(mon, mon, null, List.of("9点到10点"), 30, now));
        assertThrows(ServiceException.class, () -> AbrBookingRules.slots(mon, mon, null, List.of("11:00-10:00"), 30, now));
        assertThrows(ServiceException.class, () -> AbrBookingRules.slots(mon, mon, null, List.of("09:00-10:00"), 5, now));
        assertThrows(ServiceException.class, () -> AbrBookingRules.slots(mon, mon.plusDays(60), null, List.of("00:00-23:00"), 10, now));
    }
}
