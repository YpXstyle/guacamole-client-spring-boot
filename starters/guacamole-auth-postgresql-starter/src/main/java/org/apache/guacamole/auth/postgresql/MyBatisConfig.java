package org.apache.guacamole.auth.postgresql;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisConfig {

    @Bean
    public static MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("org.apache.guacamole.auth.jdbc");
        configurer.setAnnotationClass(org.apache.ibatis.annotations.Mapper.class);
        
        return configurer;
    }

}
