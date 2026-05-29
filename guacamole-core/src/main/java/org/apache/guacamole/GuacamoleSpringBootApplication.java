package org.apache.guacamole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "org.apache.guacamole",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.apache\\.guacamole\\.auth\\.jdbc\\..*"
    )
)
public class GuacamoleSpringBootApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuacamoleSpringBootApplication.class, args);
    }
}
