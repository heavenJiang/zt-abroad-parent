package com.ygb.abroad.service.impl;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrFieldSource;
import com.ygb.abroad.domain.AbrProgram;
import com.ygb.abroad.domain.AbrRequirement;
import com.ygb.abroad.domain.AbrSchool;
import com.ygb.abroad.domain.vo.AbrProgramImportRow;
import com.ygb.abroad.mapper.AbrProgramMapper;
import com.ygb.abroad.mapper.AbrRequirementMapper;
import com.ygb.abroad.mapper.AbrSchoolMapper;
import com.ygb.abroad.service.IAbrProgramImportService;
import com.ygb.abroad.service.IAbrProgramService;
import com.ygb.common.annotation.Excel;
import com.ygb.common.core.domain.entity.SysDictData;
import com.ygb.common.core.redis.RedisCache;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.DictUtils;
import com.ygb.common.utils.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 项目批量导入 服务层处理
 *
 * @author ygb
 */
@Service
public class AbrProgramImportServiceImpl implements IAbrProgramImportService
{
    /** 预览结果缓存 key 前缀 */
    private static final String CACHE_KEY = "abroad:import:program:";

    /** 预览结果保留时间（分钟） */
    private static final int CACHE_MINUTES = 30;

    /** 单次导入最多行数 */
    private static final int MAX_ROWS = 2000;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** 均分列 → 本科层次编码 */
    private static final Map<String, String> GPA_COLUMNS = new LinkedHashMap<>();

    static
    {
        GPA_COLUMNS.put("gpaC9", "c9");
        GPA_COLUMNS.put("gpa985", "985");
        GPA_COLUMNS.put("gpa211", "211");
        GPA_COLUMNS.put("gpaDoubleFirstClass", "double_first_class");
        GPA_COLUMNS.put("gpaNonKey", "non_key");
        GPA_COLUMNS.put("gpaOverseas", "overseas");
    }

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private AbrSchoolMapper schoolMapper;

    @Autowired
    private AbrProgramMapper programMapper;

    @Autowired
    private AbrRequirementMapper requirementMapper;

    @Autowired
    private IAbrProgramService programService;

    /**
     * 解析 Excel 并逐行校验，结果暂存 30 分钟供确认
     *
     * @param is Excel 文件流
     * @return token、rows、summary
     */
    @Override
    public Map<String, Object> preview(InputStream is)
    {
        List<AbrProgramImportRow> rows = parse(is);
        if (rows.isEmpty())
        {
            throw new ServiceException("文件中没有数据行，请使用导入模板填写");
        }
        validate(rows);
        String token = UUID.randomUUID().toString().replace("-", "");
        try
        {
            redisCache.setCacheObject(CACHE_KEY + token, MAPPER.writeValueAsString(rows), CACHE_MINUTES, TimeUnit.MINUTES);
        }
        catch (JacksonException e)
        {
            throw new ServiceException("预览结果保存失败");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("rows", rows);
        result.put("summary", summarize(rows));
        return result;
    }

    /**
     * 按预览结果导入：重新校验后新建院校、项目与申请要求
     *
     * @param token 预览返回的 token
     * @param operator 操作人
     * @return 各类数量
     */
    @Override
    @Transactional
    public Map<String, Object> confirm(String token, String operator)
    {
        String json = redisCache.getCacheObject(CACHE_KEY + token);
        if (StringUtils.isEmpty(json))
        {
            throw new ServiceException("预览已过期，请重新上传文件");
        }
        List<AbrProgramImportRow> rows;
        try
        {
            rows = MAPPER.readValue(json, new TypeReference<List<AbrProgramImportRow>>() {});
        }
        catch (JacksonException e)
        {
            throw new ServiceException("预览结果读取失败，请重新上传文件");
        }
        // 预览后数据可能被其他人修改，按当前库重新校验一遍
        for (AbrProgramImportRow row : rows)
        {
            row.setMessages(new ArrayList<>());
            row.setAction(null);
            row.setSchoolId(null);
            row.setProgramId(null);
            row.setNewSchool(null);
        }
        validate(rows);

        int schools = 0;
        int programs = 0;
        int requirements = 0;
        int skipped = 0;
        Map<String, Long> createdSchools = new HashMap<>();
        Map<String, Long> createdPrograms = new HashMap<>();
        for (AbrProgramImportRow row : rows)
        {
            String action = row.getAction();
            if (AbrProgramImportRow.ACTION_SKIP.equals(action) || AbrProgramImportRow.ACTION_ERROR.equals(action))
            {
                skipped++;
                continue;
            }
            Long programId = row.getProgramId();
            if (AbrProgramImportRow.ACTION_CREATE.equals(action))
            {
                Long schoolId = row.getSchoolId();
                if (schoolId == null)
                {
                    String key = schoolKey(row);
                    schoolId = createdSchools.get(key);
                    if (schoolId == null)
                    {
                        AbrSchool school = new AbrSchool();
                        school.setCountry(row.getCountry());
                        school.setSchoolNameEn(row.getSchoolNameEn());
                        school.setSchoolName(row.getSchoolName());
                        school.setCreateBy(operator);
                        schoolMapper.insertAbrSchool(school);
                        schoolId = school.getSchoolId();
                        createdSchools.put(key, schoolId);
                        schools++;
                    }
                }
                AbrProgram program = toProgram(row, schoolId);
                program.setCreateBy(operator);
                programMapper.insertAbrProgram(program);
                programId = program.getProgramId();
                createdPrograms.put(programKey(row), programId);
                programs++;
            }
            else if (programId == null)
            {
                // 同一文件中更早的行新建的项目，补充另一个申请季
                programId = createdPrograms.get(programKey(row));
            }
            if (hasRequirement(row))
            {
                AbrRequirement requirement = toRequirement(row, programId);
                requirement.setCreateBy(operator);
                programService.insertAbrRequirement(requirement);
                requirements++;
            }
        }
        redisCache.deleteObject(CACHE_KEY + token);
        Map<String, Object> result = new HashMap<>();
        result.put("schoolsCreated", schools);
        result.put("programsCreated", programs);
        result.put("requirementsCreated", requirements);
        result.put("skipped", skipped);
        return result;
    }

    /**
     * 读取第一个工作表：第 1 行为表头（按列名匹配，列顺序可变），之后每行一条
     */
    private List<AbrProgramImportRow> parse(InputStream is)
    {
        Map<String, Field> fieldsByLabel = new HashMap<>();
        for (Field field : AbrProgramImportRow.class.getDeclaredFields())
        {
            Excel excel = field.getAnnotation(Excel.class);
            if (excel != null)
            {
                field.setAccessible(true);
                fieldsByLabel.put(excel.name(), field);
            }
        }
        List<AbrProgramImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(is))
        {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null)
            {
                return rows;
            }
            Map<Integer, Field> columns = new HashMap<>();
            for (Cell cell : header)
            {
                Field field = fieldsByLabel.get(StringUtils.trim(formatter.formatCellValue(cell)));
                if (field != null)
                {
                    columns.put(cell.getColumnIndex(), field);
                }
            }
            if (!columns.containsValue(fieldsByLabel.get("项目英文名")))
            {
                throw new ServiceException("表头不正确，请使用导入模板（缺少“项目英文名”列）");
            }
            for (int r = header.getRowNum() + 1; r <= sheet.getLastRowNum(); r++)
            {
                Row row = sheet.getRow(r);
                if (row == null)
                {
                    continue;
                }
                AbrProgramImportRow item = new AbrProgramImportRow();
                boolean empty = true;
                for (Map.Entry<Integer, Field> e : columns.entrySet())
                {
                    String value = StringUtils.trim(formatter.formatCellValue(row.getCell(e.getKey())));
                    if (StringUtils.isNotEmpty(value))
                    {
                        e.getValue().set(item, value);
                        empty = false;
                    }
                }
                if (empty)
                {
                    continue;
                }
                item.setRowNo(r + 1);
                rows.add(item);
                if (rows.size() > MAX_ROWS)
                {
                    throw new ServiceException("单次最多导入 " + MAX_ROWS + " 行，请拆分文件");
                }
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("无法读取文件，请上传 .xlsx 或 .xls 格式的导入模板");
        }
        return rows;
    }

    /**
     * 逐行校验并给出处理结果：字段格式 → 院校匹配 → 项目去重（库内 + 文件内） → 申请季去重
     */
    private void validate(List<AbrProgramImportRow> rows)
    {
        Map<String, Integer> programKeys = new HashMap<>();
        Map<String, Integer> requirementKeys = new HashMap<>();
        for (AbrProgramImportRow row : rows)
        {
            checkFields(row);
            if (!row.getMessages().isEmpty())
            {
                row.setAction(AbrProgramImportRow.ACTION_ERROR);
                continue;
            }
            AbrSchool query = new AbrSchool();
            query.setCountry(row.getCountry());
            query.setSchoolNameEn(row.getSchoolNameEn());
            AbrSchool school = schoolMapper.checkAbrSchoolUnique(query);
            row.setNewSchool(school == null);
            if (school == null)
            {
                if (StringUtils.isEmpty(row.getSchoolName()))
                {
                    row.addMessage("院校不存在，需要填写院校中文名以新建院校");
                    row.setAction(AbrProgramImportRow.ACTION_ERROR);
                    continue;
                }
            }
            else
            {
                row.setSchoolId(school.getSchoolId());
            }

            String programKey = programKey(row);
            String requirementKey = programKey + "|" + StringUtils.nvl(row.getSeason(), "");
            AbrProgram existing = null;
            if (school != null)
            {
                AbrProgram q = new AbrProgram();
                q.setSchoolId(school.getSchoolId());
                q.setProgramNameEn(row.getProgramNameEn());
                q.setDegree(row.getDegree());
                existing = programMapper.checkAbrProgramUnique(q);
            }
            Integer earlier = programKeys.get(programKey);
            if (existing == null && earlier == null)
            {
                row.setAction(AbrProgramImportRow.ACTION_CREATE);
                programKeys.put(programKey, row.getRowNo());
                if (hasRequirement(row))
                {
                    requirementKeys.put(requirementKey, row.getRowNo());
                }
                continue;
            }
            // 项目已存在（库内或文件中更早的行）：不重复创建项目，只看申请季要求是否需要补
            if (existing != null)
            {
                row.setProgramId(existing.getProgramId());
                row.addMessage("项目已存在，不重复创建");
            }
            else
            {
                row.addMessage("与第 " + earlier + " 行是同一项目，不重复创建");
            }
            if (!hasRequirement(row))
            {
                row.setAction(AbrProgramImportRow.ACTION_SKIP);
                continue;
            }
            Integer earlierReq = requirementKeys.get(requirementKey);
            boolean reqExists = false;
            if (existing != null)
            {
                AbrRequirement rq = new AbrRequirement();
                rq.setProgramId(existing.getProgramId());
                rq.setSeason(row.getSeason());
                reqExists = requirementMapper.checkAbrRequirementUnique(rq) != null;
            }
            if (reqExists || earlierReq != null)
            {
                row.addMessage("申请季 " + row.getSeason() + " 的要求已存在，跳过");
                row.setAction(AbrProgramImportRow.ACTION_SKIP);
            }
            else
            {
                row.setAction(AbrProgramImportRow.ACTION_ADD_REQUIREMENT);
                requirementKeys.put(requirementKey, row.getRowNo());
            }
        }
    }

    /**
     * 字段格式校验，并把字典标签换成字典值
     */
    private void checkFields(AbrProgramImportRow row)
    {
        required(row, row.getCountry(), "国家");
        required(row, row.getSchoolNameEn(), "院校英文名");
        required(row, row.getProgramNameEn(), "项目英文名");
        required(row, row.getDegree(), "学位");
        required(row, row.getFieldGroup(), "学科大类");
        row.setCountry(dict(row, "abr_country", row.getCountry(), "国家"));
        row.setDegree(dict(row, "abr_degree", row.getDegree(), "学位"));
        row.setFieldGroup(dict(row, "abr_field_group", row.getFieldGroup(), "学科大类"));
        row.setMajorDirection(dict(row, "abr_major_direction", row.getMajorDirection(), "专业方向"));
        row.setTeachingLanguage(dict(row, "abr_teaching_language", row.getTeachingLanguage(), "授课语言"));
        row.setApsRequired(dict(row, "sys_yes_no", row.getApsRequired(), "需要APS"));
        row.setDifficulty(dict(row, "abr_program_difficulty", row.getDifficulty(), "难度档"));
        if (StringUtils.isNotEmpty(row.getIntakes()))
        {
            List<String> values = new ArrayList<>();
            for (String part : row.getIntakes().split("[,，、;；\\s]+"))
            {
                String v = dict(row, "abr_intake", part, "入学季");
                if (StringUtils.isNotEmpty(v) && !values.contains(v))
                {
                    values.add(v);
                }
            }
            row.setIntakes(String.join(",", values));
        }
        lengthAtMost(row, row.getSchoolNameEn(), 200, "院校英文名");
        lengthAtMost(row, row.getSchoolName(), 200, "院校中文名");
        lengthAtMost(row, row.getProgramNameEn(), 300, "项目英文名");
        lengthAtMost(row, row.getProgramName(), 200, "项目中文名");
        lengthAtMost(row, row.getWebsite(), 500, "项目官网");
        Integer months = integer(row, row.getDurationMonths(), "学制（月）");
        if (months != null && (months < 1 || months > 120))
        {
            row.addMessage("学制（月）应在 1–120 之间");
        }
        BigDecimal tuition = decimal(row, row.getTuition(), "学费");
        if (tuition != null && tuition.signum() < 0)
        {
            row.addMessage("学费不能为负数");
        }
        if (StringUtils.isNotEmpty(row.getCurrency()))
        {
            row.setCurrency(row.getCurrency().toUpperCase());
            if (!row.getCurrency().matches("^[A-Z]{3}$"))
            {
                row.addMessage("币种应为 3 位字母代码，如 GBP");
            }
        }

        if (!hasRequirement(row))
        {
            return;
        }
        if (StringUtils.isEmpty(row.getSeason()) || !row.getSeason().matches("^20\\d{2}-(fall|spring|summer|winter)$"))
        {
            row.addMessage("填写了申请要求时，申请季必填，格式如 2027-fall");
        }
        for (Map.Entry<String, String> e : GPA_COLUMNS.entrySet())
        {
            BigDecimal gpa = decimal(row, gpaValue(row, e.getKey()), "均分要求");
            if (gpa != null && (gpa.signum() < 0 || gpa.compareTo(BigDecimal.valueOf(100)) > 0))
            {
                row.addMessage("均分要求应在 0–100 之间");
            }
        }
        BigDecimal overall = ielts(row, row.getIeltsOverall(), "雅思总分");
        BigDecimal minSub = ielts(row, row.getIeltsMinSub(), "雅思小分");
        if (overall != null && minSub != null && minSub.compareTo(overall) > 0)
        {
            row.addMessage("雅思小分不能高于总分");
        }
        deadlinesJson(row);
        if (StringUtils.isEmpty(row.getSourceUrl()))
        {
            row.addMessage("填写了均分、雅思或截止日时，来源链接必填");
        }
        else if (!StringUtils.startsWithAny(row.getSourceUrl().toLowerCase(), "http://", "https://"))
        {
            row.addMessage("来源链接应以 http:// 或 https:// 开头");
        }
    }

    private static boolean hasRequirement(AbrProgramImportRow row)
    {
        if (StringUtils.isNotEmpty(row.getIeltsOverall()) || StringUtils.isNotEmpty(row.getIeltsMinSub())
                || StringUtils.isNotEmpty(row.getDeadlines()))
        {
            return true;
        }
        for (String column : GPA_COLUMNS.keySet())
        {
            if (StringUtils.isNotEmpty(gpaValue(row, column)))
            {
                return true;
            }
        }
        return false;
    }

    private static String gpaValue(AbrProgramImportRow row, String column)
    {
        switch (column)
        {
            case "gpaC9":
                return row.getGpaC9();
            case "gpa985":
                return row.getGpa985();
            case "gpa211":
                return row.getGpa211();
            case "gpaDoubleFirstClass":
                return row.getGpaDoubleFirstClass();
            case "gpaNonKey":
                return row.getGpaNonKey();
            default:
                return row.getGpaOverseas();
        }
    }

    private static String schoolKey(AbrProgramImportRow row)
    {
        return row.getCountry() + "|" + row.getSchoolNameEn().toLowerCase();
    }

    private static String programKey(AbrProgramImportRow row)
    {
        return schoolKey(row) + "|" + row.getProgramNameEn().toLowerCase() + "|" + row.getDegree();
    }

    private AbrProgram toProgram(AbrProgramImportRow row, Long schoolId)
    {
        AbrProgram program = new AbrProgram();
        program.setSchoolId(schoolId);
        program.setProgramNameEn(row.getProgramNameEn());
        program.setProgramName(row.getProgramName());
        program.setDegree(row.getDegree());
        program.setFieldGroup(row.getFieldGroup());
        program.setMajorDirection(row.getMajorDirection());
        program.setDurationMonths(StringUtils.isEmpty(row.getDurationMonths()) ? null : Integer.valueOf(row.getDurationMonths()));
        program.setIntakes(row.getIntakes());
        program.setTuition(StringUtils.isEmpty(row.getTuition()) ? null : new BigDecimal(row.getTuition()));
        program.setCurrency(row.getCurrency());
        program.setTeachingLanguage(row.getTeachingLanguage());
        program.setWebsite(row.getWebsite());
        program.setApsRequired(row.getApsRequired());
        program.setDifficulty(row.getDifficulty());
        return program;
    }

    private AbrRequirement toRequirement(AbrProgramImportRow row, Long programId)
    {
        AbrRequirement requirement = new AbrRequirement();
        requirement.setProgramId(programId);
        requirement.setSeason(row.getSeason());
        ObjectNode gpa = MAPPER.createObjectNode();
        for (Map.Entry<String, String> e : GPA_COLUMNS.entrySet())
        {
            String v = gpaValue(row, e.getKey());
            if (StringUtils.isNotEmpty(v))
            {
                gpa.put(e.getValue(), new BigDecimal(v));
            }
        }
        requirement.setMinGpa(gpa.isEmpty() ? null : gpa.toString());
        requirement.setIeltsOverall(StringUtils.isEmpty(row.getIeltsOverall()) ? null : new BigDecimal(row.getIeltsOverall()));
        requirement.setIeltsMinSub(StringUtils.isEmpty(row.getIeltsMinSub()) ? null : new BigDecimal(row.getIeltsMinSub()));
        requirement.setDeadlines(deadlinesJson(row));
        List<AbrFieldSource> sources = new ArrayList<>();
        for (String field : new String[] { "minGpa", "ieltsOverall", "ieltsMinSub", "deadlines" })
        {
            AbrFieldSource source = new AbrFieldSource();
            source.setFieldName(field);
            source.setSourceUrl(row.getSourceUrl());
            source.setQuote(row.getSourceQuote());
            source.setOrigin("0");
            sources.add(source);
        }
        requirement.setSources(sources);
        return requirement;
    }

    /**
     * 截止日：“滚动”或“R1:2027-01-15;R2:2027-03-01”，也接受单个日期
     */
    private static String deadlinesJson(AbrProgramImportRow row)
    {
        String text = row.getDeadlines();
        if (StringUtils.isEmpty(text))
        {
            return null;
        }
        ObjectNode node = MAPPER.createObjectNode();
        if ("滚动".equals(text) || "rolling".equalsIgnoreCase(text))
        {
            node.put("rolling", true);
            node.putArray("rounds");
            return node.toString();
        }
        node.put("rolling", false);
        var rounds = node.putArray("rounds");
        int index = 1;
        for (String part : text.split("[;；\\n]+"))
        {
            String p = part.trim();
            if (p.isEmpty())
            {
                continue;
            }
            String name = "R" + index;
            String date = p;
            int colon = Math.max(p.lastIndexOf(':'), p.lastIndexOf('：'));
            if (colon > 0)
            {
                name = p.substring(0, colon).trim();
                date = p.substring(colon + 1).trim();
            }
            try
            {
                LocalDate.parse(date);
            }
            catch (DateTimeParseException e)
            {
                row.addMessage("截止日“" + p + "”格式不正确，应为 R1:2027-01-15 或“滚动”");
                return null;
            }
            rounds.addObject().put("name", name).put("date", date);
            index++;
        }
        return node.toString();
    }

    private static void required(AbrProgramImportRow row, String value, String label)
    {
        if (StringUtils.isEmpty(value))
        {
            row.addMessage(label + "必填");
        }
    }

    private static void lengthAtMost(AbrProgramImportRow row, String value, int max, String label)
    {
        if (value != null && value.length() > max)
        {
            row.addMessage(label + "不能超过 " + max + " 个字符");
        }
    }

    /**
     * 字典标签或字典值都接受，返回字典值；无法识别时记错误并返回原值
     */
    private static String dict(AbrProgramImportRow row, String dictType, String raw, String label)
    {
        if (StringUtils.isEmpty(raw))
        {
            return raw;
        }
        List<SysDictData> datas = DictUtils.getDictCache(dictType);
        if (datas != null)
        {
            for (SysDictData d : datas)
            {
                if (raw.equalsIgnoreCase(d.getDictValue()) || raw.equals(d.getDictLabel()))
                {
                    return d.getDictValue();
                }
            }
        }
        if ("sys_yes_no".equals(dictType) && ("否".equals(raw) || "是".equals(raw)))
        {
            return "是".equals(raw) ? "Y" : "N";
        }
        row.addMessage(label + "“" + raw + "”不在可选范围内");
        return raw;
    }

    private static Integer integer(AbrProgramImportRow row, String raw, String label)
    {
        if (StringUtils.isEmpty(raw))
        {
            return null;
        }
        try
        {
            return Integer.valueOf(raw);
        }
        catch (NumberFormatException e)
        {
            row.addMessage(label + "应为整数");
            return null;
        }
    }

    private static BigDecimal decimal(AbrProgramImportRow row, String raw, String label)
    {
        if (StringUtils.isEmpty(raw))
        {
            return null;
        }
        try
        {
            return new BigDecimal(raw);
        }
        catch (NumberFormatException e)
        {
            row.addMessage(label + "应为数字");
            return null;
        }
    }

    private static BigDecimal ielts(AbrProgramImportRow row, String raw, String label)
    {
        BigDecimal v = decimal(row, raw, label);
        if (v != null && (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(9)) > 0))
        {
            row.addMessage(label + "应在 0–9 之间");
            return null;
        }
        return v;
    }

    private static Map<String, Integer> summarize(List<AbrProgramImportRow> rows)
    {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put(AbrProgramImportRow.ACTION_CREATE, 0);
        summary.put(AbrProgramImportRow.ACTION_ADD_REQUIREMENT, 0);
        summary.put(AbrProgramImportRow.ACTION_SKIP, 0);
        summary.put(AbrProgramImportRow.ACTION_ERROR, 0);
        for (AbrProgramImportRow row : rows)
        {
            summary.merge(row.getAction(), 1, Integer::sum);
        }
        return summary;
    }
}
