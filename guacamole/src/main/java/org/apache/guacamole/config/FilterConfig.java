package org.apache.guacamole.config;

import org.apache.guacamole.CacheRevalidationFilter;
import org.apache.guacamole.tunnel.http.RestrictedGuacamoleHTTPTunnelServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public FilterRegistrationBean<CacheRevalidationFilter> cacheRevalidationFilter() {
        FilterRegistrationBean<CacheRevalidationFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new CacheRevalidationFilter());
        reg.addUrlPatterns("/index.html");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public ServletRegistrationBean<RestrictedGuacamoleHTTPTunnelServlet> httpTunnelServlet() {
        RestrictedGuacamoleHTTPTunnelServlet servlet = new RestrictedGuacamoleHTTPTunnelServlet();
        // Manually autowire since servlet is created via new()
        applicationContext.getAutowireCapableBeanFactory().autowireBean(servlet);
        ServletRegistrationBean<RestrictedGuacamoleHTTPTunnelServlet> reg =
                new ServletRegistrationBean<>(servlet, "/tunnel");
        reg.setLoadOnStartup(1);
        return reg;
    }
}
