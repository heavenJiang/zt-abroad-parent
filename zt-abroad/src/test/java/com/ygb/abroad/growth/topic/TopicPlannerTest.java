package com.ygb.abroad.growth.topic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 选题排期测试：空位扣除已有、剧本轮流、按配比补缺口、暂不排的类型；大模型输出逐条校验；备用选题轮换与去重
 *
 * @author ygb
 */
class TopicPlannerTest
{
    private static final LocalDate D1 = LocalDate.of(2026, 10, 20);

    private static final BiPredicate<String, List<Long>> OK = (t, ids) -> true;

    private static Map<String, Integer> mix(Object... kv)
    {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
        {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    private static List<LocalDate> days(int n)
    {
        List<LocalDate> l = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            l.add(D1.plusDays(i));
        }
        return l;
    }

    private static TopicPlanner.Playbook pb(String code, Map<String, Integer> mix, List<String> backup)
    {
        return new TopicPlanner.Playbook(code, mix, List.of("open", "quick"), backup);
    }

    @Test
    void slotsSkipExistingAndFollowMix()
    {
        Map<LocalDate, Integer> existing = Map.of(D1, 1);
        List<TopicPlanner.Slot> slots = TopicPlanner.slots(days(7), 1, existing, Map.of(), List.of(),
                mix("case", 35, "flash", 30, "longtail", 20, "invite", 15), Set.of("case"));
        assertEquals(6, slots.size(), "第一天已有一条");
        assertEquals(D1.plusDays(1), slots.get(0).date());
        Map<String, Integer> count = new HashMap<>();
        slots.forEach(s -> count.merge(s.kind(), 1, Integer::sum));
        assertTrue(!count.containsKey("case"), "案例暂不排");
        assertEquals(3, count.get("flash"), count.toString());
        assertEquals(2, count.get("longtail"), count.toString());
        assertEquals(1, count.get("invite"), count.toString());
        assertEquals("s1", slots.get(0).id());
    }

    @Test
    void playbooksRotateAndCountExistingKinds()
    {
        List<TopicPlanner.Playbook> pbs = List.of(pb("a", mix("flash", 100), List.of()), pb("b", mix(), List.of()));
        List<TopicPlanner.Slot> slots = TopicPlanner.slots(days(2), 2, Map.of(), Map.of(), pbs, mix("longtail", 100), Set.of());
        assertEquals(4, slots.size());
        assertEquals(List.of("a", "b", "a", "b"), slots.stream().map(TopicPlanner.Slot::playbookCode).toList());
        assertEquals(List.of("flash", "longtail", "flash", "longtail"), slots.stream().map(TopicPlanner.Slot::kind).toList(), "空配比用默认");
        assertEquals(2, slots.get(1).slotNo());
        assertEquals("longtail", TopicPlanner.nextKind(mix("flash", 50, "longtail", 50), Map.of("flash", 3), Set.of()), "已有快讯多时补长尾");
        assertEquals("longtail", TopicPlanner.nextKind(mix("case", 100), Map.of(), Set.of("case")), "全部暂不排时用长尾");
    }

    @Test
    void acceptValidatesEachTopic() throws Exception
    {
        List<TopicPlanner.Slot> slots = List.of(new TopicPlanner.Slot("s1", D1, 1, "flash", "a"), new TopicPlanner.Slot("s2", D1.plusDays(1), 1, "longtail", "a"),
                new TopicPlanner.Slot("s3", D1.plusDays(2), 1, "longtail", "a"));
        JsonNode out = JsonMapper.builder().build().readTree("""
                {"topics":[
                  {"slot":"s1","title":"某校管理学硕士第一轮截止临近","angle":"提醒递交","hookTool":"open","programIds":[5,5]},
                  {"slot":"s1","title":"重复空位的标题","hookTool":"open"},
                  {"slot":"s2","title":"短","hookTool":"open"},
                  {"slot":"s2","title":"保研失败后还能申哪些英国硕士","hookTool":"open"},
                  {"slot":"s2","title":"分轮次截止的项目怎么排递交顺序","hookTool":"booking"},
                  {"slot":"s3","title":"英港新澳硕士怎么选","hookTool":"quick","programIds":[99]},
                  {"slot":"s9","title":"不存在的空位标题","hookTool":"open"},
                  {"slot":"s3","title":"签约保录的说法要警惕吗","hookTool":"quick"},
                  {"slot":"s3","title":"英港新澳硕士怎么选","hookTool":"quick"}
                ]}""");
        BiPredicate<String, List<Long>> noPromise = (t, ids) -> !t.contains("保录");
        TopicPlanner.Checked c = TopicPlanner.accept(slots, out, Map.of("a", List.of("open", "quick")), Set.of(5L), Set.of("保研失败后还能申哪些英国硕士"),
                noPromise);
        assertEquals(List.of("s1", "s3"), c.accepted().stream().map(t -> t.slot().id()).toList(), c.rejected().toString());
        assertEquals(List.of(5L), c.accepted().get(0).programIds(), "去重");
        assertEquals(TopicPlanner.SOURCE_AI, c.accepted().get(0).source());
        assertEquals(7, c.rejected().size(), c.rejected().toString());
        assertTrue(c.rejected().stream().anyMatch(r -> r.contains("快讯") || r.contains("重复")));
        JsonNode noProgram = JsonMapper.builder().build().readTree("{\"topics\":[{\"slot\":\"s1\",\"title\":\"截止提醒一下大家\",\"hookTool\":\"open\"}]}");
        assertTrue(TopicPlanner.accept(slots, noProgram, Map.of("a", List.of("open")), Set.of(5L), Set.of(), OK).rejected().get(0).contains("快讯须引用"));
        assertTrue(TopicPlanner.accept(slots, null, Map.of(), Set.of(), Set.of(), OK).accepted().isEmpty());
    }

    @Test
    void backupRotatesAndSkipsRecent()
    {
        List<TopicPlanner.Slot> slots = List.of(new TopicPlanner.Slot("s1", D1, 1, "flash", "a"), new TopicPlanner.Slot("s2", D1.plusDays(1), 1, "case", "a"),
                new TopicPlanner.Slot("s3", D1.plusDays(2), 1, "invite", "a"), new TopicPlanner.Slot("s4", D1.plusDays(3), 1, "invite", null));
        Map<String, TopicPlanner.Playbook> pbs = Map.of("a", pb("a", mix(), List.of("用过的备用选题", "备用选题一号", "备用选题二号")));
        List<TopicPlanner.Topic> b = TopicPlanner.backup(slots, pbs, Set.of("用过的 备用选题"), OK);
        assertEquals(List.of("备用选题一号", "备用选题二号"), b.stream().map(TopicPlanner.Topic::title).toList());
        assertTrue(b.stream().allMatch(t -> "longtail".equals(t.kind()) && "open".equals(t.hookTool()) && TopicPlanner.SOURCE_BACKUP.equals(t.source())));
        assertEquals("备用选题一号", TopicPlanner.backup(slots.subList(0, 1), pbs, Set.of(), (t, ids) -> !t.contains("用过")).get(0).title());
    }
}
