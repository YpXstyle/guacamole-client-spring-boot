package org.apache.guacamole;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(
    basePackages = "org.apache.guacamole",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.apache\\.guacamole\\.(auth\\..*|vault\\..*|history\\..*)"
    )
)
@Slf4j
public class GuacamoleSpringBootApplication {
    public static void main(String[] args) throws UnknownHostException {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        // 解析命令行参数，第一个参数作为静态文件路径
        if (args != null && args.length > 0 && StringUtils.isNotBlank(args[0])) {
            String staticPath = args[0].trim();
            // 设置系统属性，供配置类使用（不添加末尾斜杠，由配置类统一处理）
            System.setProperty("app.static.file.path", staticPath);
            log.info("静态文件路径已设置为: {}", staticPath);
        }

        ConfigurableApplicationContext run = SpringApplication.run(GuacamoleSpringBootApplication.class, args);
        Environment env = run.getEnvironment();
        final String hostAddress = InetAddress.getLocalHost().getHostAddress();
        final String serverPort = env.getProperty("server.port");
        String contextPath = env.getProperty("server.servlet.context-path");
        if (StringUtils.isNotBlank(contextPath)) {
            if (contextPath.endsWith("/")) {
                contextPath = contextPath.replaceAll("/$", "");
            }
        }
        log.info("\n[----------------------------------------------------------]\n\t" +
                        "启动成功！访问地址:\thttp://{}:{}{}\n\t" +
                        "\n[----------------------------------------------------------]",
                hostAddress, serverPort, StringUtils.isBlank(contextPath) ? "" : contextPath);
    }
}
