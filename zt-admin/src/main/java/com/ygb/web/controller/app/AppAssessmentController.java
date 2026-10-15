package com.ygb.web.controller.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ygb.abroad.domain.AbrVisitor;
import com.ygb.abroad.domain.vo.AppAnswersBody;
import com.ygb.abroad.domain.vo.AppAssessmentBody;
import com.ygb.abroad.service.IAbrAssessmentService;
import com.ygb.abroad.service.IAbrVisitorService;
import com.ygb.common.annotation.Anonymous;
import com.ygb.common.annotation.RateLimiter;
import com.ygb.common.core.controller.BaseController;
import com.ygb.common.core.domain.AjaxResult;
import com.ygb.common.enums.LimitType;

/**
 * C 端-测评（匿名答题、分步保存、断点续答、报告）
 * 
 * 匿名接口：身份来自请求头 X-Visitor-Token，只能读写本人的测评；按 IP 限流。
 * 
 * @author ygb
 */
@Anonymous
@RestController
@RequestMapping("/app/assessment")
public class AppAssessmentController extends BaseController
{
    @Autowired
    private IAbrAssessmentService assessmentService;

    @Autowired
    private IAbrVisitorService visitorService;

    /**
     * 取当前启用的问卷（新测评用）
     */
    @RateLimiter(key = "app_questionnaire:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/questionnaire/current")
    public AjaxResult currentQuestionnaire(@RequestParam(value = "code", required = false) String code)
    {
        return success(assessmentService.selectCurrentQuestionnaire(code == null ? IAbrAssessmentService.DEFAULT_QUESTIONNAIRE_CODE : code));
    }

    /**
     * 首屏：各学段的开放情况（可测评并出结果 / 可测评、结果由顾问评估 / 由顾问评估）
     */
    @RateLimiter(key = "app_questionnaire:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/degrees")
    public AjaxResult degrees()
    {
        return success(assessmentService.selectDegrees());
    }

    /**
     * 落地页：已完成测评的人数（人数少时为空，不显示）
     */
    @RateLimiter(key = "app_questionnaire:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        return success().put("submittedCount", assessmentService.selectSubmittedCount());
    }

    /**
     * 按学段取启用的问卷（首屏选学段后用）
     */
    @RateLimiter(key = "app_questionnaire:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/questionnaire/degree/{degree}")
    public AjaxResult questionnaireByDegree(@PathVariable String degree)
    {
        return success(assessmentService.selectCurrentQuestionnaireByDegree(degree));
    }

    /**
     * 按版本取问卷（续答旧测评用）
     */
    @RateLimiter(key = "app_questionnaire:", time = 60, count = 60, limitType = LimitType.IP)
    @GetMapping("/questionnaire/{questionnaireId}")
    public AjaxResult questionnaire(@PathVariable Long questionnaireId)
    {
        return success(assessmentService.selectQuestionnaire(questionnaireId));
    }

    /**
     * 同意隐私说明后创建测评（同一请求ID重复提交返回同一测评）
     */
    @RateLimiter(key = "app_assessment_create:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping
    public AjaxResult create(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @Validated @RequestBody AppAssessmentBody body)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(assessmentService.createAssessment(visitor, body));
    }

    /**
     * 取本人最近一次答题中的测评（没有时 data 为空）
     */
    @RateLimiter(key = "app_assessment_read:", time = 60, count = 120, limitType = LimitType.IP)
    @GetMapping("/current")
    public AjaxResult current(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @RequestParam(value = "code", required = false) String code)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(assessmentService.selectCurrentAssessment(visitor, code));
    }

    /**
     * 取本人的测评
     */
    @RateLimiter(key = "app_assessment_read:", time = 60, count = 120, limitType = LimitType.IP)
    @GetMapping("/{assessmentId}")
    public AjaxResult getInfo(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @PathVariable Long assessmentId)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(assessmentService.selectAssessment(visitor, assessmentId));
    }

    /**
     * 分步保存答案（只提交本屏改动；null 表示清除）
     */
    @RateLimiter(key = "app_assessment_save:", time = 60, count = 120, limitType = LimitType.IP)
    @PutMapping("/{assessmentId}/answers")
    public AjaxResult saveAnswers(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @PathVariable Long assessmentId, @Validated @RequestBody AppAnswersBody body)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(assessmentService.saveAnswers(visitor, assessmentId, body));
    }

    /**
     * 提交测评（检查必答题后自动计算选校结果）
     */
    @RateLimiter(key = "app_assessment_submit:", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/{assessmentId}/submit")
    public AjaxResult submit(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @PathVariable Long assessmentId)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(assessmentService.submitAssessment(visitor, assessmentId));
    }

    /**
     * 取本人测评的报告（结构化部分：定位、画像、冲 / 稳 / 保代表校、短板、补足后可申、数据说明；不含综合分与录取概率）
     */
    @RateLimiter(key = "app_assessment_read:", time = 60, count = 120, limitType = LimitType.IP)
    @GetMapping("/{assessmentId}/report")
    public AjaxResult report(@RequestHeader(value = IAbrVisitorService.HEADER, required = false) String token,
            @PathVariable Long assessmentId)
    {
        AbrVisitor visitor = visitorService.resolveVisitor(token);
        return success(assessmentService.selectReport(visitor, assessmentId));
    }
}
