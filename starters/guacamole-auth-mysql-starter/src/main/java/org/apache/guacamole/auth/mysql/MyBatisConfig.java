package org.apache.guacamole.auth.mysql;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "org.apache.guacamole.auth.jdbc", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {

}
