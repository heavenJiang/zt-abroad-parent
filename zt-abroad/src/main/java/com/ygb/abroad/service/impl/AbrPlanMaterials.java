package com.ygb.abroad.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import com.ygb.abroad.domain.AbrPlanItem;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.vo.AppMaterialsVo;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;

/**
 * 材料清单的纯函数部分（不访问数据库）
 *
 * 每个项目的材料：申请要求写了材料就按项目（份数、说明），否则按「国家 × 学位」默认模板（标为一般要求）；
 * 再按申请要求补上推得出的材料：写了语言要求 → 语言成绩，GRE / GMAT 必须或可选 → 标化成绩，需要 APS → APS 证书。
 * 共用材料（css_class = shared）在清单中只列一份，份数取各项目要求的最大值；单独准备的材料按项目分别列出。
 * 项目信息超过 90 天未核实时，按项目写明的份数与说明不展示。
 *
 * @author ygb
 */
final class AbrPlanMaterials
{
    /** 说明 */
    static final String NOTE = "材料按院校库中的项目要求汇总；标“一般要求”的为该国家常见材料，以院校官网为准";

    private AbrPlanMaterials()
    {
    }

    /**
     * 汇总材料清单
     *
     * @param items 选校单条目（只取正常项目，保持顺序）
     * @param programs 项目ID → 院校库记录
     * @param requirements 项目ID → 最新申请季的要求
     * @param templates 「国家:学位」→ 默认模板材料列表 JSON
     * @param types 材料类型（按显示顺序）
     * @param done 已完成的材料键
     * @param countryLabel 国家 / 地区代码 → 名称
     * @param today 今天（北京时间）
     * @return 材料清单
     */
    static AppMaterialsVo build(List<AbrPlanItem> items, Map<Long, AbrProgram> programs, Map<Long, AbrRequirement> requirements,
            Map<String, String> templates, Map<String, AbrMaterials.Type> types, Set<String> done, Function<String, String> countryLabel,
            LocalDate today)
    {
        Map<String, List<AppMaterialsVo.Need>> sharedNeeds = new LinkedHashMap<>();
        List<AppMaterialsVo.ProgramMaterials> list = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        for (AbrPlanItem item : items)
        {
            if (!Boolean.TRUE.equals(item.getActive()))
            {
                continue;
            }
            AbrProgram program = programs.get(item.getProgramId());
            AbrRequirement req = requirements.get(item.getProgramId());
            boolean stale = AbrLookupTools.stale(program, today);
            Required required = required(program, req, templates, types, stale);
            List<AppMaterialsVo.Item> mats = new ArrayList<>();
            int doneCount = 0;
            for (AbrMaterials.Type type : types.values())
            {
                Need need = required.needs().get(type.code());
                if (need == null)
                {
                    continue;
                }
                boolean shared = AbrMaterials.SCOPE_SHARED.equals(type.scope());
                String key = shared ? type.code() : type.code() + ":" + item.getProgramId();
                boolean isDone = done.contains(key);
                doneCount += isDone ? 1 : 0;
                allKeys.add(key);
                mats.add(new AppMaterialsVo.Item(key, type.code(), type.label(), type.scope(), need.count(), need.note(), need.general(), isDone));
                if (shared)
                {
                    sharedNeeds.computeIfAbsent(type.code(), k -> new ArrayList<>()).add(new AppMaterialsVo.Need(item.getProgramId(),
                            item.getSchoolName(), item.getProgramName(), need.count(), need.note(), need.general()));
                }
            }
            String next = null;
            if (!stale && req != null)
            {
                AbrLookupTools.Deadline d = AbrLookupTools.deadline(req.getDeadlines(), today);
                next = d == null || d.nextDate() == null ? null : d.nextDate().toString();
            }
            list.add(new AppMaterialsVo.ProgramMaterials(item.getProgramId(), item.getSchoolName(), item.getProgramName(),
                    countryLabel.apply(item.getCountry()), next, doneCount, mats.size(), stale, required.noTemplate(), mats));
        }
        List<AppMaterialsVo.Shared> shared = new ArrayList<>();
        for (AbrMaterials.Type type : types.values())
        {
            List<AppMaterialsVo.Need> needs = sharedNeeds.get(type.code());
            if (needs == null)
            {
                continue;
            }
            Integer count = needs.stream().map(AppMaterialsVo.Need::count).filter(c -> c != null).max(Integer::compare).orElse(null);
            shared.add(new AppMaterialsVo.Shared(type.code(), type.code(), type.label(), count, done.contains(type.code()), needs));
        }
        int doneTotal = (int) allKeys.stream().filter(done::contains).count();
        return new AppMaterialsVo(doneTotal, allKeys.size(), shared, list, NOTE);
    }

    /**
     * 清单中的全部材料键（校验勾选提交的键）
     *
     * @param vo 材料清单
     * @return 材料键
     */
    static Set<String> keys(AppMaterialsVo vo)
    {
        Set<String> keys = new LinkedHashSet<>();
        vo.programs().forEach(p -> p.items().forEach(i -> keys.add(i.key())));
        return keys;
    }

    /**
     * 一个项目需要的材料
     */
    static Required required(AbrProgram program, AbrRequirement req, Map<String, String> templates, Map<String, AbrMaterials.Type> types,
            boolean stale)
    {
        Map<String, Need> needs = new LinkedHashMap<>();
        boolean noTemplate = false;
        JsonNode explicit = req == null || StringUtils.isBlank(req.getMaterials()) ? null : AbrJsonFields.parse(req.getMaterials(), "申请材料");
        if (explicit != null && explicit.isArray() && !explicit.isEmpty())
        {
            add(needs, explicit, types, false, stale);
        }
        else
        {
            String template = program == null ? null : templates.get(program.getCountry() + ":" + program.getDegree());
            if (template == null)
            {
                noTemplate = true;
            }
            else
            {
                add(needs, AbrJsonFields.parse(template, "默认材料模板"), types, true, false);
            }
        }
        if (req != null)
        {
            boolean language = req.getIeltsOverall() != null || !AbrAssessmentAnswers.object(req.getOtherLanguage()).isEmpty();
            if (language && types.containsKey("language"))
            {
                needs.putIfAbsent("language", new Need(null, null, false));
            }
            if ("required".equals(req.getTestRequirement()) && types.containsKey("gre_gmat"))
            {
                needs.put("gre_gmat", new Need(null, "必须提交", false));
            }
            else if ("optional".equals(req.getTestRequirement()) && types.containsKey("gre_gmat"))
            {
                needs.putIfAbsent("gre_gmat", new Need(null, "可选提交", false));
            }
        }
        if (program != null && "Y".equals(program.getApsRequired()) && types.containsKey("aps"))
        {
            needs.putIfAbsent("aps", new Need(null, null, false));
        }
        return new Required(needs, noTemplate);
    }

    private static void add(Map<String, Need> needs, JsonNode list, Map<String, AbrMaterials.Type> types, boolean general, boolean stale)
    {
        if (list == null || !list.isArray())
        {
            return;
        }
        for (JsonNode item : list)
        {
            String code = item.path("code").asString("");
            if (!types.containsKey(code))
            {
                continue;
            }
            Integer count = item.path("count").isIntegralNumber() ? item.path("count").asInt() : null;
            String note = item.path("note").isString() ? item.path("note").asString() : null;
            needs.put(code, stale ? new Need(null, null, false) : new Need(count, note, general));
        }
    }

    /**
     * 一项材料要求
     *
     * @param count 份数
     * @param note 说明
     * @param general 是否按国家一般要求
     */
    record Need(Integer count, String note, boolean general)
    {
    }

    /**
     * 一个项目需要的材料
     *
     * @param needs 材料类型 → 要求
     * @param noTemplate 项目没写材料要求且没有默认模板
     */
    record Required(Map<String, Need> needs, boolean noTemplate)
    {
    }
}
