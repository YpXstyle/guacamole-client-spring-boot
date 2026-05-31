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
