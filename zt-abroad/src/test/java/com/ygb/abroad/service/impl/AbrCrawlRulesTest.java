package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 项目采集网址规则与学科归类（网址取自真实站点地图）
 *
 * @author ygb
 */
class AbrCrawlRulesTest
{
    @Test
    void siteDomain()
    {
        assertEquals("ucl.ac.uk", AbrCrawlRules.siteDomain("https://www.ucl.ac.uk/x"));
        assertEquals("hku.hk", AbrCrawlRules.siteDomain("https://www.hku.hk/en"));
        assertEquals("nus.edu.sg", AbrCrawlRules.siteDomain("https://nus.edu.sg/"));
        assertEquals("unsw.edu.au", AbrCrawlRules.siteDomain("https://www.unsw.edu.au/study"));
        assertTrue(AbrCrawlRules.sameSite("https://www.ed.ac.uk/studying/x", "https://www.ed.ac.uk/"));
        assertTrue(AbrCrawlRules.sameSite("https://tpg.hku.hk/x", "https://www.hku.hk/"));
        assertFalse(AbrCrawlRules.sameSite("https://www.ed.ac.uk.evil.com/x", "https://www.ed.ac.uk/"));
    }

    @Test
    void classifyUrls()
    {
        assertEquals("program", AbrCrawlRules.classify("https://www.ucl.ac.uk/prospective-students/graduate/taught-degrees/computer-science-msc"));
        assertEquals("program", AbrCrawlRules.classify("https://www.unsw.edu.au/study/postgraduate/master-of-data-science"));
        assertEquals("program", AbrCrawlRules.classify("https://www.kcl.ac.uk/study/postgraduate-taught/courses/data-science-msc"));
        assertEquals("directory", AbrCrawlRules.classify("https://www.ed.ac.uk/studying/postgraduate/applying/taught/masters"));
        assertEquals("directory", AbrCrawlRules.classify("https://www.hku.hk/en/academics/programme-overview/taught-postgraduates"));
        assertNull(AbrCrawlRules.classify("https://www.ucl.ac.uk/engage/events/2026/mar/masterclass-understanding-un-through-sdgs"));
        assertNull(AbrCrawlRules.classify("https://www.unsw.edu.au/news/2026/07/ada-launches-new-master-of-counselling"));
        assertNull(AbrCrawlRules.classify("https://www.ed.ac.uk/studying/postgraduate/fees-and-funding/tuition"));
        assertNull(AbrCrawlRules.classify("https://www.ucl.ac.uk/study/phd/computer-science-mphil"));
        assertNull(AbrCrawlRules.classify("https://www.ucl.ac.uk/about"));
        assertNull(AbrCrawlRules.classify("https://www.ed.ac.uk/user/login?destination=/studying/online-masters"));
        assertNull(AbrCrawlRules.classify("https://www.kcl.ac.uk/study/postgraduate-research/areas/chemistry-mphil-phd"));
        assertFalse(AbrCrawlRules.programLink(new AbrCrawlRules.Link("https://www.kcl.ac.uk/study/x/chemistry-mphil", "Chemistry Research MPhil/PhD")));
        assertNull(AbrCrawlRules.classify("https://www.ucl.ac.uk/engage/policy-lab/our-programmes/nation-neighbours"));
        assertNull(AbrCrawlRules.classify("https://www.ucl.ac.uk/engage/ucl-east/schools-and-courses"));
        assertEquals("directory", AbrCrawlRules.classify("https://www.ucl.ac.uk/prospective-students/graduate/taught-degrees"));
        assertEquals("https://www.ucl.ac.uk/a/b", AbrCrawlRules.normalize("http://www.ucl.ac.uk/a/b/"));
        assertEquals("https://www.ucl.ac.uk/", AbrCrawlRules.normalize("https://www.ucl.ac.uk/"));
    }

    @Test
    void links()
    {
        String html = "<a href=\"/studying/postgraduate/degrees/index.php?r=site/view&amp;id=902\">Data Science <b>MSc</b></a>"
                + "<a href='#top'>Top</a><a href=\"https://x.com/news/1\">News</a><a href=\"/studying/postgraduate/degrees/index.php?r=site/view&amp;id=1\">PhD Informatics</a>";
        List<AbrCrawlRules.Link> l = AbrCrawlRules.links(html, "https://www.ed.ac.uk/studying/postgraduate/degrees/index.php?r=site/taught");
        assertEquals(3, l.size());
        assertEquals("https://www.ed.ac.uk/studying/postgraduate/degrees/index.php?r=site/view&id=902", l.get(0).url());
        assertEquals("Data Science MSc", l.get(0).text());
        assertTrue(AbrCrawlRules.programLink(l.get(0)));
        assertFalse(AbrCrawlRules.programLink(l.get(2)));
    }

    @Test
    void classifyPrograms()
    {
        assertEquals("data_ai", AbrProgramClassifier.classify("Data Science MSc").majorDirection());
        assertEquals("cs", AbrProgramClassifier.classify("Computer Science MSc").majorDirection());
        assertEquals("finance", AbrProgramClassifier.classify("MSc Finance").majorDirection());
        assertEquals("business_analytics", AbrProgramClassifier.classify("MSc Business Analytics").majorDirection());
        assertEquals("data_ai", AbrProgramClassifier.classify("MSc Artificial Intelligence").majorDirection());
        assertEquals("law", AbrProgramClassifier.classify("LLM International Commercial Law").majorDirection());
        assertEquals("ee", AbrProgramClassifier.classify("MSc Electronic Engineering").majorDirection());
        assertEquals("arts_design", AbrProgramClassifier.classify("MA Fine Art").majorDirection());
        assertEquals("humanities", AbrProgramClassifier.classify("MA Translation Studies").majorDirection());
        AbrProgramClassifier.Result r = AbrProgramClassifier.classify("MSc Something Unusual");
        assertFalse(r.matched());
        assertEquals("humanities_social", r.fieldGroup());
    }
}
