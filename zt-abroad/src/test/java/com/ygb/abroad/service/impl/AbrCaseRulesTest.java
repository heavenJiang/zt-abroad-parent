package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrCase;
import com.ygb.abroad.growth.card.Card;
import com.ygb.common.exception.ServiceException;

/**
 * 录取案例规则测试：校验与规范、均分区间、申请季写法、能否公开、对外背景与 offer 卡（不含身份信息）
 *
 * @author ygb
 */
class AbrCaseRulesTest
{
    private static final BiFunction<String, String, String> LABEL = (type, value) -> switch (value)
    {
        case "UK" -> "英国";
        case "master" -> "硕士";
        case "985" -> "985";
        case "business_analytics" -> "商业分析";
        default -> value;
    };

    private static AbrCase sample()
    {
        AbrCase c = new AbrCase();
        c.setLeadId(7L);
        c.setLeadName("王同学家长");
        c.setSeason(" 2027-fall ");
        c.setCountry("UK");
        c.setDegree("master");
        c.setSchoolName(" 测试英国大学 ");
        c.setProgramName("商业分析硕士");
        c.setResult("admit");
        c.setUndergradTier("985");
        c.setMajorDirection("business_analytics");
        c.setGpa(new BigDecimal("85.60"));
        c.setIelts(new BigDecimal("7.0"));
        c.setVerifyLevel("A");
        c.setPublicityConsent("Y");
        c.setAlgoConsent("x");
        c.setStatus("0");
        return c;
    }

    @Test
    void validateNormalizes()
    {
        AbrCase c = sample();
        c.setVerifyLevel(null);
        c.setSource(null);
        AbrCaseRules.validate(c);
        assertEquals("2027-fall", c.getSeason());
        assertEquals("测试英国大学", c.getSchoolName());
        assertEquals("C", c.getVerifyLevel());
        assertEquals("manual", c.getSource());
        assertEquals("N", c.getAlgoConsent());
        assertEquals("Y", c.getPublicityConsent());
        assertEquals("0", c.getStatus());
    }

    @Test
    void validateRejects()
    {
        AbrCase season = sample();
        season.setSeason("2027");
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(season));
        AbrCase result = sample();
        result.setResult("offer");
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(result));
        AbrCase gpa = sample();
        gpa.setGpa(new BigDecimal("101"));
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(gpa));
        AbrCase ielts = sample();
        ielts.setIelts(new BigDecimal("9.5"));
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(ielts));
        AbrCase level = sample();
        level.setVerifyLevel("E");
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(level));
        AbrCase source = sample();
        source.setSource("forum");
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(source));
        AbrCase school = sample();
        school.setSchoolName(" ");
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(school));
        AbrCase country = sample();
        country.setCountry(null);
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(country));
        AbrCase longText = sample();
        longText.setExperience("x".repeat(501));
        assertThrows(ServiceException.class, () -> AbrCaseRules.validate(longText));
    }

    @Test
    void gpaBandsAndSeason()
    {
        assertEquals("84–87", AbrCaseRules.gpaBand(new BigDecimal("85.6")));
        assertEquals("75–78", AbrCaseRules.gpaBand(new BigDecimal("75")));
        assertEquals("75 以下", AbrCaseRules.gpaBand(new BigDecimal("74.9")));
        assertEquals("99–100", AbrCaseRules.gpaBand(new BigDecimal("100")));
        assertNull(AbrCaseRules.gpaBand(null));
        assertEquals(List.of(new BigDecimal("84"), new BigDecimal("87")), AbrCaseRules.gpaBounds(new BigDecimal("85.6")));
        assertEquals(List.of(new BigDecimal("75")), AbrCaseRules.gpaBounds(new BigDecimal("70")));
        assertTrue(AbrCaseRules.gpaBounds(null).isEmpty());
        assertEquals("2027 秋", AbrCaseRules.seasonLabel("2027-fall"));
        assertEquals("2028 春", AbrCaseRules.seasonLabel("2028-spring"));
        assertEquals("2027", AbrCaseRules.seasonLabel("2027"));
    }

    @Test
    void publicEligibility()
    {
        assertTrue(AbrCaseRules.publicEligible(sample()));
        AbrCase reject = sample();
        reject.setResult("reject");
        assertFalse(AbrCaseRules.publicEligible(reject));
        AbrCase selfReport = sample();
        selfReport.setVerifyLevel("C");
        assertFalse(AbrCaseRules.publicEligible(selfReport));
        AbrCase noConsent = sample();
        noConsent.setPublicityConsent("N");
        assertFalse(AbrCaseRules.publicEligible(noConsent));
        AbrCase revoked = sample();
        revoked.setRevokeTime(new Date());
        assertFalse(AbrCaseRules.publicEligible(revoked));
        AbrCase disabled = sample();
        disabled.setStatus("1");
        assertFalse(AbrCaseRules.publicEligible(disabled));
        assertFalse(AbrCaseRules.publicEligible(null));
    }

    @Test
    void cardHasNoIdentity()
    {
        AbrCase c = sample();
        AbrCaseRules.validate(c);
        c.setScholarship("2000 英镑");
        Card card = AbrCaseRules.card(c, LABEL, "留学工作室", "https://app.example.com/?ch=offer&ct=o1");
        assertEquals("2027 秋 · 英国 · 硕士", card.kicker());
        assertEquals("录取 · 测试英国大学", card.title());
        assertEquals(List.of("商业分析硕士", "奖学金：2000 英镑"), card.sections().get(0).lines());
        assertEquals(List.of("985 · 商业分析", "均分 84–87", "雅思 7.0"), card.sections().get(1).lines());
        String all = card.toString();
        assertFalse(all.contains("王同学"));
        assertFalse(all.contains("85.6"));
        AbrCase bare = sample();
        bare.setUndergradTier(null);
        bare.setMajorDirection(null);
        bare.setGpa(null);
        bare.setIelts(null);
        assertTrue(AbrCaseRules.background(bare, LABEL).isEmpty());
        assertEquals(1, AbrCaseRules.card(bare, LABEL, "b", null).sections().size());
    }
}
