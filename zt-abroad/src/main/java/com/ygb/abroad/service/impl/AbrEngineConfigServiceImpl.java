package com.ygb.abroad.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ygb.abroad.domain.AbrEngineConfig;
import com.ygb.abroad.domain.AbrQuestionnaire;
import com.ygb.abroad.engine.CaseBench;
import com.ygb.abroad.engine.NormalizationRules;
import com.ygb.abroad.domain.vo.AbrEngineConfigCheck;
import com.ygb.abroad.mapper.AbrEngineConfigMapper;
import com.ygb.abroad.mapper.AbrQuestionnaireMapper;
import com.ygb.abroad.service.IAbrAssessmentService;
import com.ygb.abroad.service.IAbrEngineConfigService;
import com.ygb.common.exception.ServiceException;
import com.ygb.common.utils.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 选校引擎配置版本 服务层实现
 * 
 * @author ygb
 */
@Service
public class AbrEngineConfigServiceImpl implements IAbrEngineConfigService
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private AbrEngineConfigMapper engineConfigMapper;

    @Autowired
    private AbrQuestionnaireMapper questionnaireMapper;

    @Override
    public List<AbrEngineConfig> selectAbrEngineConfigList(AbrEngineConfig config)
    {
        return engineConfigMapper.selectAbrEngineConfigList(config);
    }

    @Override
    public AbrEngineConfig selectAbrEngineConfigById(Long configId)
    {
        return engineConfigMapper.selectAbrEngineConfigById(configId);
    }

    @Override
    public AbrEngineConfigCheck checkAbrEngineConfig(String json)
    {
        AbrEngineConfigCheck check = AbrEngineConfigValidator.check(json);
        if (check.getConfigCode() != null && check.getVersion() != null)
        {
            AbrEngineConfig existing = engineConfigMapper.selectAbrEngineConfigByCodeVersion(check.getConfigCode(), check.getVersion());
            check.setExistingStatus(existing == null ? null : existing.getPublishStatus());
        }
        if (check.getCountry() != null && check.getDegree() != null)
        {
            AbrEngineConfig active = engineConfigMapper.selectActiveAbrEngineConfig(check.getCountry(), check.getDegree());
            check.setActiveVersion(active == null ? null : active.getVersion());
        }
        return check;
    }

    @Override
    public AbrEngineConfig importAbrEngineConfig(String json, String remark, String operator)
    {
        AbrEngineConfigCheck check = checkAbrEngineConfig(json);
        if (!check.getErrors().isEmpty())
        {
            throw new ServiceException("引擎配置校验未通过：" + StringUtils.join(check.getErrors(), "；"));
        }
        if (check.getExistingStatus() != null)
        {
            throw new ServiceException("配置 " + check.getConfigCode() + " 的版本 " + check.getVersion() + " 已存在，请在 JSON 中提高 version 后再导入");
        }
        AbrEngineConfig config = new AbrEngineConfig();
        config.setConfigCode(check.getConfigCode());
        config.setCountry(check.getCountry());
        config.setDegree(check.getDegree());
        config.setVersion(check.getVersion());
        config.setFileStatus(check.getFileStatus());
        config.setConfig(AbrEngineConfigValidator.normalize(json));
        config.setWarnings(MAPPER.writeValueAsString(check.getWarnings()));
        config.setCreateBy(operator);
        config.setRemark(remark);
        try
        {
            engineConfigMapper.insertAbrEngineConfig(config);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("配置 " + check.getConfigCode() + " 的版本 " + check.getVersion() + " 已存在");
        }
        config.setPublishStatus(AbrEngineConfig.STATUS_DRAFT);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int activateAbrEngineConfig(Long configId, String operator)
    {
        AbrEngineConfig config = engineConfigMapper.selectAbrEngineConfigById(configId);
        if (config == null)
        {
            throw new ServiceException("配置版本不存在");
        }
        if (AbrEngineConfig.STATUS_ACTIVE.equals(config.getPublishStatus()))
        {
            throw new ServiceException("该版本已是启用状态");
        }
        if (!AbrEngineConfig.FILE_CONFIRMED.equals(config.getFileStatus()))
        {
            throw new ServiceException("该版本是草案（draft），顾问确认并把 status 改为 confirmed、提高版本号后重新导入才能启用");
        }
        // 入库后再校验一次：防止校验规则或引擎升级后启用不合格的旧版本
        AbrEngineConfigCheck check = AbrEngineConfigValidator.check(config.getConfig());
        if (!check.getErrors().isEmpty())
        {
            throw new ServiceException("该版本校验未通过，不能启用：" + StringUtils.join(check.getErrors(), "；"));
        }
        engineConfigMapper.retireActiveAbrEngineConfig(config.getCountry(), config.getDegree(), operator);
        int rows = engineConfigMapper.activateAbrEngineConfig(configId, operator);
        if (rows == 0)
        {
            throw new ServiceException("启用失败，请刷新后重试");
        }
        return rows;
    }

    @Override
    public int deleteAbrEngineConfigByIds(Long[] configIds, String operator)
    {
        for (Long id : configIds)
        {
            AbrEngineConfig config = engineConfigMapper.selectAbrEngineConfigById(id);
            if (config != null && !AbrEngineConfig.STATUS_DRAFT.equals(config.getPublishStatus()))
            {
                throw new ServiceException("版本 " + config.getVersion() + " 已启用过，不能删除");
            }
        }
        return engineConfigMapper.deleteDraftAbrEngineConfigByIds(configIds, operator);
    }

    @Override
    public Map<String, JsonNode> selectActiveConfigs(String degree)
    {
        Map<String, JsonNode> configs = new LinkedHashMap<>();
        for (AbrEngineConfig c : engineConfigMapper.selectActiveAbrEngineConfigs(degree))
        {
            configs.put(c.getCountry(), MAPPER.readTree(c.getConfig()));
        }
        return configs;
    }

    @Override
    public Map<String, Object> runBench(String csv, Map<String, Long> configIds)
    {
        AbrQuestionnaire questionnaire = questionnaireMapper.selectActiveAbrQuestionnaire(IAbrAssessmentService.DEFAULT_QUESTIONNAIRE_CODE);
        NormalizationRules rules = questionnaire == null ? null : NormalizationRules.parse(MAPPER.readTree(questionnaire.getDefinition()));
        AbrEngineCaseCsv.Parsed parsed = AbrEngineCaseCsv.parse(csv, rules);

        Map<String, JsonNode> configs = new LinkedHashMap<>();
        Map<String, String> versions = new LinkedHashMap<>();
        for (CaseBench.BenchCase c : parsed.cases())
        {
            String country = c.program().country();
            if (versions.containsKey(country))
            {
                continue;
            }
            AbrEngineConfig config = benchConfig(country, c.profile().getTargetDegree(), configIds == null ? null : configIds.get(country));
            versions.put(country, config == null ? null : config.getVersion() + "（" + config.getPublishStatus() + "）");
            if (config != null)
            {
                configs.put(country, MAPPER.readTree(config.getConfig()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", CaseBench.run(parsed.cases(), configs));
        out.put("configs", versions);
        out.put("errors", parsed.errors());
        out.put("skipped", parsed.skipped());
        return out;
    }

    /**
     * 测试台用的配置：指定的版本 → 启用版本 → 最新导入的草稿
     */
    private AbrEngineConfig benchConfig(String country, String degree, Long configId)
    {
        if (configId != null)
        {
            AbrEngineConfig config = engineConfigMapper.selectAbrEngineConfigById(configId);
            if (config == null || !country.equals(config.getCountry()))
            {
                throw new ServiceException("所选配置不存在或不是 " + country + " 的配置");
            }
            return config;
        }
        AbrEngineConfig query = new AbrEngineConfig();
        query.setCountry(country);
        query.setDegree(degree);
        AbrEngineConfig pick = null;
        for (AbrEngineConfig c : engineConfigMapper.selectAbrEngineConfigList(query))
        {
            if (AbrEngineConfig.STATUS_ACTIVE.equals(c.getPublishStatus()))
            {
                pick = c;
                break;
            }
            if (pick == null && AbrEngineConfig.STATUS_DRAFT.equals(c.getPublishStatus()))
            {
                pick = c;
            }
        }
        return pick == null ? null : engineConfigMapper.selectAbrEngineConfigById(pick.getConfigId());
    }
}
