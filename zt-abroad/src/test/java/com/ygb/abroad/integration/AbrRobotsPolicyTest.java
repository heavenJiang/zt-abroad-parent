package com.ygb.abroad.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * robots.txt 规则解析与判断
 *
 * @author ygb
 */
class AbrRobotsPolicyTest
{
    @Test
    void starGroup()
    {
        String txt = """
                # 示例
                User-agent: Googlebot
                Disallow: /

                User-agent: *
                Disallow: /admin/
                Disallow: /*.pdf$
                Allow: /admin/public/
                Crawl-delay: 10
                """;
        List<AbrRobotsPolicy.Rule> r = AbrRobotsPolicy.parse(txt, AbrRobotsPolicy.AGENT);
        assertEquals(3, r.size());
        assertTrue(AbrRobotsPolicy.decide(r, "/"));
        assertFalse(AbrRobotsPolicy.decide(r, "/admin/x"));
        assertTrue(AbrRobotsPolicy.decide(r, "/admin/public/a"));
        assertFalse(AbrRobotsPolicy.decide(r, "/files/a.pdf"));
        assertTrue(AbrRobotsPolicy.decide(r, "/files/a.pdf?x=1"));
    }

    @Test
    void ownGroupAndEmpty()
    {
        List<AbrRobotsPolicy.Rule> own = AbrRobotsPolicy.parse("User-agent: AbroadCatalogBot\nDisallow: /\n\nUser-agent: *\nAllow: /\n", AbrRobotsPolicy.AGENT);
        assertFalse(AbrRobotsPolicy.decide(own, "/"));
        List<AbrRobotsPolicy.Rule> multi = AbrRobotsPolicy.parse("User-agent: a\nUser-agent: *\nDisallow: /x\n", AbrRobotsPolicy.AGENT);
        assertFalse(AbrRobotsPolicy.decide(multi, "/x/1"));
        assertTrue(AbrRobotsPolicy.decide(AbrRobotsPolicy.parse("User-agent: *\nDisallow:\n", AbrRobotsPolicy.AGENT), "/anything"));
        assertTrue(AbrRobotsPolicy.decide(AbrRobotsPolicy.parse("", AbrRobotsPolicy.AGENT), "/"));
    }
}
