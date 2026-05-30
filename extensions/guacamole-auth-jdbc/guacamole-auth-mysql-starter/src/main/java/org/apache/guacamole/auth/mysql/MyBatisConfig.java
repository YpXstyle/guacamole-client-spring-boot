package org.apache.guacamole.auth.mysql;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@MapperScan(basePackages = "org.apache.guacamole.auth.jdbc", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MyBatisConfig {

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource,
            ApplicationContext ctx) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(ctx.getResources("classpath*:mappers/mysql/**/*.xml"));
        return factory;
    }
}
