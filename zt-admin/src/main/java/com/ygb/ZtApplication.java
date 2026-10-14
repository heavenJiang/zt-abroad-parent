package com.ygb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class ZtApplication {
    public static void main(String[] args) {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication application = new SpringApplication(ZtApplication.class);
        // AI 评测命令（--abroad.ai.eval=<agentCode>）跑完即退出：Web 服务改用本机随机端口，避免与正在运行的服务冲突
        // （安全配置依赖 MVC，不能关闭 Web 服务；命令行显式指定 --server.port 时以命令行为准）
        if (isAiEval(args)) {
            System.setProperty("server.port", "0");
            System.setProperty("server.address", "127.0.0.1");
        }
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  留学智能体后台服务启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }

    /**
     * 是否为 AI 评测命令
     */
    private static boolean isAiEval(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--abroad.ai.eval=")) {
                return true;
            }
        }
        return System.getProperty("abroad.ai.eval") != null;
    }
}
