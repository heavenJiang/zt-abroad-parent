package com.ygb.abroad.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.vo.AppIeltsVo;
import com.ygb.abroad.domain.vo.AppOpenProgramsVo;
import com.ygb.abroad.domain.vo.AppQuickResultVo;
import com.ygb.abroad.domain.vo.AppRecognitionVo;
import com.ygb.abroad.engine.BackgroundProfile;
import com.ygb.abroad.engine.HardThresholdChecker;
import com.ygb.abroad.engine.ProgramFacts;
import com.ygb.abroad.service.IAbrSelectionService;
import com.ygb.common.exception.ServiceException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 查询类入口工具的纯函数部分（不访问数据库）：本校认可度、雅思够不够、现在还能申
 *
 * 结论只取硬门槛检查结果与院校库事实；项目信息超过 90 天未核实时不展示数值（《获客系统设计》数据可信：改为“以官网为准”）。
 *
 * @author ygb
 */
final class AbrLookupTools
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 工具：本校认可度 */
    static final String RECOGNITION = "recognition";

    /** 工具：雅思够不够 */
    static final String IELTS = "ielts";

    /** 工具：现在还能申 */
    static final String OPEN = "open";

    /** 各工具采集的题目 */
    static final Map<String, List<String>> QUESTIONS = Map.of(
            RECOGNITION, List.of("undergrad_school"),
            IELTS, List.of("english_status", "english_score", "target_countries", "target_field"),
            OPEN, List.of("target_countries", "target_field"));

    /** 项目信息超过多少天未核实不展示数值 */
    static final int STALE_DAYS = 90;

    /** 每次最多返回的项目数（现在还能申） */
    static final int MAX_OPEN = 200;

    /** 北京时间 */
    static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private static final Pattern IELTS_TEXT = Pattern.compile("^雅思 ([0-9.]+)(?:（小分 ([0-9.]+)）)?$");

    private static final BigDecimal NINE = new BigDecimal("9");

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private AbrLookupTools()
    {
    }

    // ---------------------------------------------------------------- 校验

    /**
     * 本校认可度：只要本科学校
     *
     * @param answers 答案
     * @return 校验后的答案
     */
    static ObjectNode validateRecognition(JsonNode answers)
    {
        JsonNode school = object(answers).get("undergrad_school");
        if (school == null || school.isNull())
        {
            throw new ServiceException("请选择本科学校");
        }
        check(school);
        ObjectNode out = MAPPER.createObjectNode();
        out.set("undergrad_school", AbrQuickTool.school(school));
        return out;
    }

    /**
     * 雅思够不够：雅思总分（必填）与最低小分（选填），目标国家 / 地区，专业方向（选填）
     *
     * @param q 问卷
     * @param answers 答案
     * @return 校验后的答案（english_score 与 english_status = taken）
     */
    static ObjectNode validateIelts(AbrAssessmentAnswers.Questionnaire q, JsonNode answers)
    {
        JsonNode a = object(answers);
        JsonNode score = a.get("english_score");
        if (score == null || !score.isObject() || !"ielts".equals(score.path("type").asString("")))
        {
            throw new ServiceException("请填写雅思成绩");
        }
        BigDecimal total = band(score.get("total"), "雅思总分");
        if (total == null)
        {
            throw new ServiceException("请填写雅思总分");
        }
        BigDecimal sub = band(score.get("min_sub"), "最低小分");
        if (sub != null && sub.compareTo(total) > 0)
        {
            throw new ServiceException("最低小分不能高于总分");
        }
        ObjectNode out = MAPPER.createObjectNode();
        out.put("english_status", "taken");
        ObjectNode s = out.putObject("english_score");
        s.put("type", "ielts");
        s.put("total", total);
        if (sub == null)
        {
            s.putNull("min_sub");
        }
        else
        {
            s.put("min_sub", sub);
        }
        targets(q, a, out);
        return out;
    }

    /**
     * 现在还能申：目标国家 / 地区，专业方向（选填）
     *
     * @param q 问卷
     * @param answers 答案
     * @return 校验后的答案
     */
    static ObjectNode validateOpen(AbrAssessmentAnswers.Questionnaire q, JsonNode answers)
    {
        ObjectNode out = MAPPER.createObjectNode();
        targets(q, object(answers), out);
        return out;
    }

    private static void targets(AbrAssessmentAnswers.Questionnaire q, JsonNode a, ObjectNode out)
    {
        JsonNode countries = a.get("target_countries");
        if (countries == null || countries.isNull())
        {
            throw new ServiceException("请选择目标国家 / 地区");
        }
        out.set("target_countries", AbrQuickTool.countries(q, countries));
        JsonNode field = a.get("target_field");
        if (field != null && !field.isNull() && !"".equals(field.asString("")))
        {
            out.set("target_field", AbrQuickTool.field(q.questions().get("target_field"), field));
        }
    }

    private static JsonNode object(JsonNode answers)
    {
        if (answers == null || !answers.isObject())
        {
            throw new ServiceException("请填写查询信息");
        }
        check(answers);
        return answers;
    }

    private static void check(JsonNode v)
    {
        if (AbrAssessmentAnswers.containsTag(v))
        {
            throw new ServiceException("填写内容不能包含 < 加字母的写法");
        }
    }

    /**
     * 雅思分：0–9，0.5 一档；未填为 null
     */
    private static BigDecimal band(JsonNode v, String name)
    {
        if (v == null || v.isNull())
        {
            return null;
        }
        if (!v.isNumber())
        {
            throw new ServiceException(name + "应为数字");
        }
        BigDecimal x = v.decimalValue();
        if (x.signum() < 0 || x.compareTo(NINE) > 0 || x.remainder(HALF).signum() != 0)
        {
            throw new ServiceException(name + "应在 0–9 之间、以 0.5 为一档");
        }
        return x.setScale(1);
    }

    // ---------------------------------------------------------------- 本校认可度

    /**
     * 本校认可度结果
     *
     * @param slice 院校库切片（已开放国家的全部硕士项目）
     * @param p 只含本科学校的背景档案
     * @param labels 国家 / 地区代码 → 名称
     * @param today 今天（北京时间）
     * @param toolRunId 运行ID
     * @return 结果
     */
    static AppRecognitionVo recognition(IAbrSelectionService.CatalogSlice slice, BackgroundProfile p, Map<String, String> labels, LocalDate today,
            Long toolRunId)
    {
        List<AppRecognitionVo.Country> countries = new ArrayList<>();
        for (String country : slice.openCountries())
        {
            List<AppRecognitionVo.Program> programs = new ArrayList<>();
            for (ProgramFacts f : inCountry(slice, country))
            {
                HardThresholdChecker.Result r = HardThresholdChecker.check(p, f, slice.configs().get(country), true);
                HardThresholdChecker.Item tier = item(r, AbrQuickTool.ITEM_TIER);
                HardThresholdChecker.Item gpa = item(r, "gpa");
                boolean notAccepted = tier != null && HardThresholdChecker.NOT_MET.equals(tier.outcome());
                boolean estimated = tier != null && HardThresholdChecker.SOURCE_DEFAULT.equals(tier.source())
                        || gpa != null && HardThresholdChecker.SOURCE_DEFAULT.equals(gpa.source());
                AbrProgram program = slice.programs().get(f.programId());
                boolean stale = stale(program, today);
                String required = notAccepted || stale || gpa == null ? null : AbrQuickTool.requiredText(gpa);
                programs.add(new AppRecognitionVo.Program(f.programId(), program.getSchoolName(), program.getProgramName(),
                        notAccepted ? "not_accepted" : "accepted", required, estimated, stale));
            }
            programs.sort(Comparator.comparing((AppRecognitionVo.Program x) -> "not_accepted".equals(x.status()))
                    .thenComparing(x -> x.schoolName() == null ? "" : x.schoolName()).thenComparing(AppRecognitionVo.Program::programId));
            int notAccepted = (int) programs.stream().filter(x -> "not_accepted".equals(x.status())).count();
            countries.add(new AppRecognitionVo.Country(country, labels.getOrDefault(country, country), programs.size() - notAccepted, notAccepted,
                    programs));
        }
        List<String> notices = p.getWarnings().contains("tier_unknown") ? List.of(AbrQuickTool.NOTICE_SCHOOL_NOT_FOUND) : List.of();
        return new AppRecognitionVo(toolRunId, notices, countries, closed(slice, labels));
    }

    // ---------------------------------------------------------------- 雅思够不够

    /**
     * 雅思够不够结果（只看英语授课项目）
     *
     * @param slice 院校库切片
     * @param p 只含雅思成绩与目标的背景档案
     * @param labels 国家 / 地区代码 → 名称
     * @param today 今天（北京时间）
     * @param toolRunId 运行ID
     * @return 结果
     */
    static AppIeltsVo ielts(IAbrSelectionService.CatalogSlice slice, BackgroundProfile p, Map<String, String> labels, LocalDate today, Long toolRunId)
    {
        List<AppIeltsVo.Country> countries = new ArrayList<>();
        for (String country : slice.openCountries())
        {
            List<AppIeltsVo.Program> programs = new ArrayList<>();
            for (ProgramFacts f : inCountry(slice, country))
            {
                HardThresholdChecker.Result r = HardThresholdChecker.check(p, f, slice.configs().get(country), true);
                HardThresholdChecker.Item lang = item(r, "language_en");
                if (lang == null)
                {
                    // 非英语授课（日语、德语等）不在本工具范围内
                    continue;
                }
                AbrProgram program = slice.programs().get(f.programId());
                boolean stale = stale(program, today);
                String outcome = stale ? HardThresholdChecker.UNCHECKED : outcome(lang.outcome());
                BigDecimal totalGap = null;
                BigDecimal subGap = null;
                if (HardThresholdChecker.NOT_MET.equals(outcome))
                {
                    BigDecimal[] req = parseIelts(lang.required());
                    if (req != null)
                    {
                        totalGap = gap(req[0], p.getIeltsEquivTotal());
                        subGap = gap(req[1], p.getIeltsEquivMinSub());
                    }
                }
                programs.add(new AppIeltsVo.Program(f.programId(), program.getSchoolName(), program.getProgramName(), outcome,
                        stale ? null : lang.required(), totalGap, subGap, HardThresholdChecker.SOURCE_DEFAULT.equals(lang.source()),
                        !stale && r.flags().contains("pre_sessional_unverified"), stale));
            }
            programs.sort(Comparator.comparingInt((AppIeltsVo.Program x) -> rank(x.outcome()))
                    .thenComparing(x -> maxGap(x), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(x -> x.schoolName() == null ? "" : x.schoolName()).thenComparing(AppIeltsVo.Program::programId));
            countries.add(new AppIeltsVo.Country(country, labels.getOrDefault(country, country), count(programs, HardThresholdChecker.MET),
                    count(programs, HardThresholdChecker.PRE_SESSIONAL), count(programs, HardThresholdChecker.NOT_MET),
                    count(programs, HardThresholdChecker.UNCHECKED), programs));
        }
        String score = "雅思 " + p.getIeltsEquivTotal().toPlainString()
                + (p.getIeltsEquivMinSub() == null ? "" : "（小分 " + p.getIeltsEquivMinSub().toPlainString() + "）");
        return new AppIeltsVo(toolRunId, score, countries, closed(slice, labels));
    }

    private static String outcome(String o)
    {
        return HardThresholdChecker.MET.equals(o) || HardThresholdChecker.PRE_SESSIONAL.equals(o) || HardThresholdChecker.NOT_MET.equals(o) ? o
                : HardThresholdChecker.UNCHECKED;
    }

    private static int rank(String outcome)
    {
        return switch (outcome)
        {
            case HardThresholdChecker.MET -> 0;
            case HardThresholdChecker.PRE_SESSIONAL -> 1;
            case HardThresholdChecker.NOT_MET -> 2;
            default -> 3;
        };
    }

    private static BigDecimal maxGap(AppIeltsVo.Program x)
    {
        if (x.totalGap() == null && x.subGap() == null)
        {
            return null;
        }
        return (x.totalGap() == null ? BigDecimal.ZERO : x.totalGap()).max(x.subGap() == null ? BigDecimal.ZERO : x.subGap());
    }

    private static int count(List<AppIeltsVo.Program> programs, String outcome)
    {
        return (int) programs.stream().filter(x -> outcome.equals(x.outcome())).count();
    }

    /**
     * 解析引擎的雅思要求文字（“雅思 7.0（小分 6.5）”）
     *
     * @param required 要求文字
     * @return [总分, 小分]（小分可为 null）；不是雅思要求时为 null
     */
    static BigDecimal[] parseIelts(String required)
    {
        if (required == null)
        {
            return null;
        }
        Matcher m = IELTS_TEXT.matcher(required);
        if (!m.matches())
        {
            return null;
        }
        return new BigDecimal[] { new BigDecimal(m.group(1)), m.group(2) == null ? null : new BigDecimal(m.group(2)) };
    }

    private static BigDecimal gap(BigDecimal required, BigDecimal actual)
    {
        if (required == null || actual == null || actual.compareTo(required) >= 0)
        {
            return null;
        }
        return required.subtract(actual);
    }

    // ---------------------------------------------------------------- 现在还能申

    /**
     * 现在还能申结果
     *
     * @param slice 院校库切片（不要求引擎配置）
     * @param labels 国家 / 地区代码 → 名称
     * @param today 今天（北京时间）
     * @param toolRunId 运行ID
     * @return 结果
     */
    static AppOpenProgramsVo open(IAbrSelectionService.CatalogSlice slice, Map<String, String> labels, LocalDate today, Long toolRunId)
    {
        List<AppOpenProgramsVo.Program> open = new ArrayList<>();
        int closed = 0;
        int missing = 0;
        for (ProgramFacts f : slice.facts())
        {
            AbrProgram program = slice.programs().get(f.programId());
            AbrRequirement req = slice.requirements().get(f.programId());
            Deadline d = deadline(req == null ? null : req.getDeadlines(), today);
            if (d == null)
            {
                missing++;
                continue;
            }
            if (!d.open())
            {
                closed++;
                continue;
            }
            boolean stale = stale(program, today);
            open.add(new AppOpenProgramsVo.Program(f.programId(), program.getSchoolName(), program.getProgramName(), f.country(),
                    labels.getOrDefault(f.country(), f.country()), req.getSeason(), stale ? null : d.nextRound(),
                    stale || d.nextDate() == null ? null : d.nextDate().toString(),
                    stale || d.nextDate() == null ? null : (int) ChronoUnit.DAYS.between(today, d.nextDate()), d.roundsLeft(), d.rolling(), stale,
                    program.getWebsite()));
        }
        open.sort(Comparator.comparingInt((AppOpenProgramsVo.Program x) -> x.nextDeadline() != null ? 0 : x.unverified() ? 2 : 1)
                .thenComparing(x -> x.nextDeadline() == null ? "" : x.nextDeadline())
                .thenComparing(x -> x.schoolName() == null ? "" : x.schoolName()).thenComparing(AppOpenProgramsVo.Program::programId));
        return new AppOpenProgramsVo(toolRunId, today.toString(), open.size() > MAX_OPEN ? List.copyOf(open.subList(0, MAX_OPEN)) : open, closed,
                missing);
    }

    /**
     * 截止日解析：{"rolling":false,"rounds":[{"name":"R1","date":"2027-01-15"}]}
     *
     * @param json 截止日 JSON
     * @param today 今天
     * @return 截止情况；没有录入（或格式不对）为 null
     */
    static Deadline deadline(String json, LocalDate today)
    {
        JsonNode d = AbrAssessmentAnswers.object(json);
        boolean rolling = d.path("rolling").asBoolean(false);
        List<String[]> ahead = new ArrayList<>();
        int rounds = 0;
        for (JsonNode r : d.path("rounds"))
        {
            LocalDate date;
            try
            {
                date = LocalDate.parse(r.path("date").asString(""));
            }
            catch (DateTimeParseException e)
            {
                continue;
            }
            rounds++;
            if (!date.isBefore(today))
            {
                ahead.add(new String[] { r.path("name").asString(""), date.toString() });
            }
        }
        if (rounds == 0 && !rolling)
        {
            return null;
        }
        ahead.sort(Comparator.comparing(x -> x[1]));
        if (ahead.isEmpty())
        {
            return new Deadline(rolling && rounds == 0, null, null, 0, rolling);
        }
        String name = ahead.get(0)[0];
        return new Deadline(true, name.isBlank() ? null : name, LocalDate.parse(ahead.get(0)[1]), ahead.size(), rolling);
    }

    /**
     * 截止情况
     *
     * @param open 是否仍开放（有未到的轮次，或只写了滚动录取）
     * @param nextRound 最近一轮名称
     * @param nextDate 最近一轮截止日
     * @param roundsLeft 剩余轮次数
     * @param rolling 是否滚动录取
     */
    record Deadline(boolean open, String nextRound, LocalDate nextDate, int roundsLeft, boolean rolling)
    {
    }

    // ---------------------------------------------------------------- 公共

    /**
     * 项目信息是否超过 90 天未核实（从未核实也算）
     *
     * @param program 项目
     * @param today 今天（北京时间）
     * @return 是否过期
     */
    static boolean stale(AbrProgram program, LocalDate today)
    {
        Date verified = program == null ? null : program.getVerifiedTime();
        if (verified == null)
        {
            return true;
        }
        LocalDate day = verified.toInstant().atZone(BEIJING).toLocalDate();
        return ChronoUnit.DAYS.between(day, today) > STALE_DAYS;
    }

    /**
     * 计算留痕（只给管理端）：各项目结论
     *
     * @param outcomes 项目ID → 结论
     * @param slice 院校库切片
     * @return 留痕 JSON
     */
    static ObjectNode trace(Map<Long, String> outcomes, IAbrSelectionService.CatalogSlice slice)
    {
        ObjectNode out = MAPPER.createObjectNode();
        ArrayNode programs = out.putArray("programs");
        outcomes.forEach((id, outcome) -> {
            ObjectNode o = programs.addObject();
            o.put("programId", id);
            o.put("outcome", outcome);
        });
        ArrayNode skipped = out.putArray("skippedCountries");
        slice.skippedCountries().forEach(skipped::add);
        return out;
    }

    private static List<ProgramFacts> inCountry(IAbrSelectionService.CatalogSlice slice, String country)
    {
        return slice.facts().stream().filter(f -> country.equals(f.country())).toList();
    }

    private static HardThresholdChecker.Item item(HardThresholdChecker.Result r, String key)
    {
        return r.items().stream().filter(i -> key.equals(i.key())).findFirst().orElse(null);
    }

    private static List<AppQuickResultVo.CountryRef> closed(IAbrSelectionService.CatalogSlice slice, Map<String, String> labels)
    {
        return slice.skippedCountries().stream().map(c -> new AppQuickResultVo.CountryRef(c, labels.getOrDefault(c, c))).toList();
    }
}
