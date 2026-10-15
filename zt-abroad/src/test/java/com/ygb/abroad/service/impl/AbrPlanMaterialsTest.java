package com.ygb.abroad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.common.exception.ServiceException;

/**
 * 材料清单测试：项目要求优先、国家默认模板兜底、从申请要求推出的材料、共用材料合并、完成度、未核实不展示份数；材料列表校验
 *
 * @author ygb
 */
class AbrPlanMaterialsTest
{
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    private static final Map<String, AbrMaterials.Type> TYPES = AbrMaterials.BUILTIN;

    private static final Map<String, String> TEMPLATES = Map.of("UK:master",
            "[{\"code\":\"transcript\"},{\"code\":\"language\"},{\"code\":\"cv\"},{\"code\":\"ps\"},{\"code\":\"reference\",\"count\":2}]");

    private static AbrPlanItem item(long id, String country, boolean active)
    {
        AbrPlanItem i = new AbrPlanItem();
        i.setProgramId(id);
        i.setSchoolName("学校" + id);
        i.setProgramName("项目" + id);
        i.setCountry(country);
        i.setActive(active);
        return i;
    }

    private static AbrProgram program(long id, String country, int verifiedDaysAgo, String aps)
    {
        AbrProgram p = new AbrProgram();
        p.setProgramId(id);
        p.setCountry(country);
        p.setDegree("master");
        p.setApsRequired(aps);
        p.setVerifiedTime(Date.from(TODAY.minusDays(verifiedDaysAgo).atStartOfDay(AbrLookupTools.BEIJING).toInstant()));
        return p;
    }

    /**
     * 1 英国按模板；2 英国项目写明 3 封推荐信 + 作品集，GRE 必须，截止 2026-12-01；3 德国需 APS、无模板；4 英国未核实且写了 4 封推荐信；5 已下线
     */
    private static AppMaterialsVo build(Set<String> done)
    {
        List<AbrPlanItem> items = List.of(item(1, "UK", true), item(2, "UK", true), item(3, "DE", true), item(4, "UK", true), item(5, "UK", false));
        Map<Long, AbrProgram> programs = new HashMap<>();
        programs.put(1L, program(1, "UK", 5, "N"));
        programs.put(2L, program(2, "UK", 5, "N"));
        programs.put(3L, program(3, "DE", 5, "Y"));
        programs.put(4L, program(4, "UK", 120, "N"));
        Map<Long, AbrRequirement> reqs = new HashMap<>();
        AbrRequirement r2 = new AbrRequirement();
        r2.setProgramId(2L);
        r2.setMaterials("[{\"code\":\"transcript\"},{\"code\":\"ps\",\"note\":\"1000 词以内\"},{\"code\":\"reference\",\"count\":3},{\"code\":\"portfolio\"}]");
        r2.setTestRequirement("required");
        r2.setIeltsOverall(new BigDecimal("7.0"));
        r2.setDeadlines("{\"rounds\": [{\"name\": \"Round 1\", \"date\": \"2026-12-01\"}]}");
        reqs.put(2L, r2);
        AbrRequirement r4 = new AbrRequirement();
        r4.setProgramId(4L);
        r4.setMaterials("[{\"code\":\"reference\",\"count\":4,\"note\":\"两封学术\"}]");
        r4.setDeadlines("{\"rounds\": [{\"name\": \"Round 1\", \"date\": \"2026-12-01\"}]}");
        reqs.put(4L, r4);
        return AbrPlanMaterials.build(items, programs, reqs, TEMPLATES, TYPES, done, c -> c, TODAY);
    }

    @Test
    void programRequirementsOverrideTemplate()
    {
        AppMaterialsVo vo = build(Set.of());
        assertEquals(4, vo.programs().size());
        AppMaterialsVo.ProgramMaterials p1 = vo.programs().get(0);
        assertEquals(List.of("transcript", "language", "cv", "ps", "reference"), p1.items().stream().map(AppMaterialsVo.Item::code).toList());
        assertTrue(p1.items().stream().allMatch(AppMaterialsVo.Item::general));
        assertEquals("ps:1", p1.items().get(3).key());
        assertEquals("program", p1.items().get(3).scope());

        AppMaterialsVo.ProgramMaterials p2 = vo.programs().get(1);
        // 项目写明的材料 + 雅思要求推出语言成绩 + GRE 必须；没有简历（项目材料列表未写）
        assertEquals(List.of("transcript", "language", "gre_gmat", "ps", "reference", "portfolio"),
                p2.items().stream().map(AppMaterialsVo.Item::code).toList());
        assertTrue(p2.items().stream().noneMatch(AppMaterialsVo.Item::general));
        assertEquals("必须提交", p2.items().get(2).note());
        assertEquals("1000 词以内", p2.items().get(3).note());
        assertEquals("2026-12-01", p2.nextDeadline());

        AppMaterialsVo.ProgramMaterials p3 = vo.programs().get(2);
        assertTrue(p3.noTemplate());
        assertEquals(List.of("aps"), p3.items().stream().map(AppMaterialsVo.Item::code).toList());

        // 4：超过 90 天未核实，份数与说明不展示，截止日不展示
        AppMaterialsVo.ProgramMaterials p4 = vo.programs().get(3);
        assertTrue(p4.unverified());
        assertNull(p4.items().get(0).count());
        assertNull(p4.items().get(0).note());
        assertNull(p4.nextDeadline());
    }

    @Test
    void sharedMaterialsMergeAndProgressCounts()
    {
        AppMaterialsVo vo = build(Set.of("transcript", "ps:2", "unknown"));
        AppMaterialsVo.Shared reference = vo.shared().stream().filter(s -> "reference".equals(s.code())).findFirst().orElseThrow();
        assertEquals(3, reference.count());
        assertEquals(List.of(1L, 2L, 4L), reference.programs().stream().map(AppMaterialsVo.Need::programId).toList());
        assertTrue(vo.shared().stream().noneMatch(s -> "ps".equals(s.code())));
        AppMaterialsVo.Shared transcript = vo.shared().get(0);
        assertEquals("transcript", transcript.code());
        assertTrue(transcript.done());
        // 共用材料：transcript language cv reference gre_gmat portfolio aps；单独准备：ps:1 ps:2
        assertEquals(9, vo.totalCount());
        assertEquals(2, vo.doneCount());
        AppMaterialsVo.ProgramMaterials p2 = vo.programs().get(1);
        assertEquals(2, p2.doneCount());
        assertEquals(6, p2.totalCount());
        assertTrue(AbrPlanMaterials.keys(vo).contains("ps:2"));
        assertFalse(AbrPlanMaterials.keys(vo).contains("ps:5"));
    }

    @Test
    void normalizeValidatesItems()
    {
        Set<String> codes = TYPES.keySet();
        assertNull(AbrMaterials.normalize(null, codes));
        assertEquals("[]", AbrMaterials.normalize("[]", codes));
        assertEquals("[{\"code\":\"reference\",\"count\":2,\"note\":\"学术\"},{\"code\":\"cv\"}]",
                AbrMaterials.normalize("[{\"code\":\" reference \",\"count\":2,\"note\":\" 学术 \"},{\"code\":\"cv\",\"note\":\"\"}]", codes));
        assertRejects("{}", "应为 JSON 数组");
        assertRejects("[{\"code\":\"x\"}]", "类型不正确");
        assertRejects("[{\"code\":\"cv\"},{\"code\":\"cv\"}]", "重复");
        assertRejects("[{\"code\":\"reference\",\"count\":0}]", "1–10");
        assertRejects("[{\"code\":\"reference\",\"count\":1.5}]", "1–10");
        assertRejects("[{\"code\":\"cv\",\"note\":\"<b>x</b>\"}]", "不能包含");
        assertRejects("[{\"code\":\"cv\",\"note\":\"" + "长".repeat(101) + "\"}]", "最多 100 字");
        assertRejects("not json", "不是合法的 JSON");
    }

    private static void assertRejects(String json, String message)
    {
        ServiceException e = assertThrows(ServiceException.class, () -> AbrMaterials.normalize(json, TYPES.keySet()));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }
}
