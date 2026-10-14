package com.ygb.web.core.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import com.ygb.abroad.ai.AiGateway;
import com.ygb.abroad.ai.AiPromptLoader;
import com.ygb.abroad.ai.AiStore;
import com.ygb.abroad.ai.eval.AiEvalRunner;

/**
 * AI 评测命令：java -jar zt-admin.jar --abroad.ai.eval=&lt;agentCode&gt; [--abroad.ai.eval-provider=&lt;provider:model&gt;]
 * <p>
 * 跑完该 Agent 生效提示词版本下 eval/ 的全部样例，报告写到 ./ai-eval/&lt;agentCode&gt;-&lt;yyyyMMddHHmmss&gt;.md，
 * 全部通过退出码 0，否则 1。评测模式下 Web 服务只监听本机随机端口（见 ZtApplication）。
 * </p>
 *
 * @author ygb
 */
@Component
@ConditionalOnProperty(prefix = "abroad.ai", name = "eval")
public class AiEvalCommandRunner implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(AiEvalCommandRunner.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AiGateway gateway;

    @Autowired
    private AiPromptLoader promptLoader;

    @Autowired
    private AiStore store;

    @Value("${abroad.ai.eval}")
    private String agentCode;

    @Value("${abroad.ai.eval-provider:}")
    private String evalProvider;

    @Value("${abroad.ai.eval-dir:ai-eval}")
    private String evalDir;

    @Override
    public void run(ApplicationArguments args)
    {
        int code = 1;
        try
        {
            AiEvalRunner.Report report = new AiEvalRunner(gateway, promptLoader, store).run(agentCode.trim(), evalProvider, "eval");
            Path dir = Paths.get(evalDir).toAbsolutePath();
            Files.createDirectories(dir);
            Path file = dir.resolve(agentCode.trim() + "-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".md");
            Files.writeString(file, report.toMarkdown(), StandardCharsets.UTF_8);
            for (AiEvalRunner.CaseResult c : report.getCases())
            {
                log.info("[AI 评测] {} {}（{}）", c.isPassed() ? "PASS" : "FAIL", c.getName(), c.getResult().getStatus());
            }
            log.info("[AI 评测] {}", report.summary());
            log.info("[AI 评测] 报告：{}", file);
            code = report.isPassed() ? 0 : 1;
        }
        catch (Exception e)
        {
            log.error("[AI 评测] 执行失败：{}", e.getMessage(), e);
        }
        final int exitCode = code;
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }
}
