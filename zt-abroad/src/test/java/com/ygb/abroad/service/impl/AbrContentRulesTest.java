package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrContent;
import com.ygb.common.exception.ServiceException;

/**
 * 内容规则测试：内容编号与口令格式、工具链接、字段校验、改稿重新过闸门、闸门后的状态、AI 生成标识
 *
 * @author ygb
 */
class AbrContentRulesTest
{
    private static final Set<String> KINDS = Set.of("case", "flash", "longtail", "invite");

    private static final Set<String> DEGREES = Set.of("master");

    private static final Set<String> COUNTRIES = Set.of("UK", "HK");

    private static final Set<String> HOOKS = Set.of("ielts", "quick", "follow");

    private static AbrContent content()
    {
        AbrContent c = new AbrContent();
        c.setTitle(" 某校 2027 雅思要求变化 ");
        c.setContentKind("flash");
        c.setAccountId(1L);
        c.setDegree("master");
        c.setCountries("[\"UK\",\"UK\"]");
        c.setHashtags("[\"#英国留学\",\"雅思\"]");
        c.setCitations("[{\"type\":\"requirement\",\"id\":12},{\"type\":\"url\",\"url\":\"https://www.example.ac.uk/req\"}]");
        c.setBody("均分 > 85 的同学可以看看");
        c.setHookTool("ielts");
        return c;
    }

    private static void normalize(AbrContent c)
    {
        AbrContentRules.normalize(c, KINDS, DEGREES, COUNTRIES, HOOKS);
    }

    @Test
    void codesAndCodeword()
    {
        String code = AbrContentRules.newContentCode();
        assertTrue(code.matches("^c[2-9a-z]{9}$"), code);
        assertTrue(AbrContentRules.newCodeword().matches("^[1-9]\\d{3}$"));
        assertNull(AbrContentRules.normalizeCodeword("  "));
        assertEquals("选校", AbrContentRules.normalizeCodeword(" 选校 "));
        assertEquals("ielts7", AbrContentRules.normalizeCodeword("ielts7"));
        assertThrows(ServiceException.class, () -> AbrContentRules.normalizeCodeword("a"));
        assertThrows(ServiceException.class, () -> AbrContentRules.normalizeCodeword("选 校"));
        assertThrows(ServiceException.class, () -> AbrContentRules.normalizeCodeword("12345678901234567"));
    }

    @Test
    void toolLink()
    {
        assertEquals("https://h5.example.com/pages/tool/ielts?ch=xhs_main&ct=c123",
                AbrContentRules.toolLink("https://h5.example.com", "ielts", "xhs_main", "c123"));
        assertEquals("https://h5.example.com/?ch=xhs_main&ct=c123", AbrContentRules.toolLink("https://h5.example.com/", "follow", "xhs_main", "c123"));
        assertEquals("https://h5.example.com/?ch=a&ct=c", AbrContentRules.toolLink("https://h5.example.com/", null, "a", "c"));
    }

    @Test
    void normalizeFields()
    {
        AbrContent c = content();
        normalize(c);
        assertEquals("某校 2027 雅思要求变化", c.getTitle());
        assertEquals("[\"UK\"]", c.getCountries());
        assertEquals("[\"英国留学\",\"雅思\"]", c.getHashtags());
        assertEquals("[]", c.getCards());
    }

    @Test
    void normalizeRejects()
    {
        AbrContent kind = content();
        kind.setContentKind("ad");
        assertThrows(ServiceException.class, () -> normalize(kind));
        AbrContent country = content();
        country.setCountries("[\"US\"]");
        assertThrows(ServiceException.class, () -> normalize(country));
        AbrContent hook = content();
        hook.setHookTool("lottery");
        assertThrows(ServiceException.class, () -> normalize(hook));
        AbrContent cite = content();
        cite.setCitations("[{\"type\":\"program\"}]");
        assertThrows(ServiceException.class, () -> normalize(cite));
        AbrContent url = content();
        url.setCitations("[{\"type\":\"url\",\"url\":\"ftp://x\"}]");
        assertThrows(ServiceException.class, () -> normalize(url));
        AbrContent tag = content();
        tag.setBody("<a href=x>点这里</a>");
        assertThrows(ServiceException.class, () -> normalize(tag));
        AbrContent cards = content();
        cards.setCards("[1]");
        assertThrows(ServiceException.class, () -> normalize(cards));
        AbrContent hashtags = content();
        hashtags.setHashtags("[\"\"]");
        assertThrows(ServiceException.class, () -> normalize(hashtags));
    }

    @Test
    void regateOnContentChange()
    {
        AbrContent before = content();
        normalize(before);
        before.setCodeword("8372");
        AbrContent same = content();
        normalize(same);
        same.setCodeword("8372");
        same.setRemark("只改备注");
        same.setPlaybookCode("season_open");
        assertFalse(AbrContentRules.needsRegate(before, same));
        AbrContent body = content();
        normalize(body);
        body.setCodeword("8372");
        body.setBody("改了正文");
        assertTrue(AbrContentRules.needsRegate(before, body));
        AbrContent word = content();
        normalize(word);
        word.setCodeword("9999");
        assertTrue(AbrContentRules.needsRegate(before, word));
    }

    @Test
    void statusAfterGateAndAiLabel()
    {
        assertEquals(AbrContent.STATUS_READY, AbrContentRules.statusAfterGate(AbrContent.GATE_PASSED, false));
        assertEquals(AbrContent.STATUS_PENDING_CONFIRM, AbrContentRules.statusAfterGate(AbrContent.GATE_MANUAL_PASSED, true));
        assertEquals(AbrContent.STATUS_DRAFT, AbrContentRules.statusAfterGate(AbrContent.GATE_BLOCKED, true));

        AbrContent c = content();
        assertNull(AbrContentRules.missingAiLabel(c, "本文由 AI 辅助生成"));
        c.setAiGenerated("Y");
        assertNotNull(AbrContentRules.missingAiLabel(c, "本文由 AI 辅助生成"));
        c.setBody(c.getBody() + "\n本文由 AI 辅助生成");
        assertNull(AbrContentRules.missingAiLabel(c, "本文由 AI 辅助生成"));
    }
}
