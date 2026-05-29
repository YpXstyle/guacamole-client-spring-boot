package org.apache.guacamole.config;

import java.io.IOException;
import java.io.InputStream;
import org.apache.guacamole.extension.LanguageResourceService;
import org.apache.guacamole.resource.ByteArrayResource;
import org.apache.guacamole.resource.Resource;
import org.apache.guacamole.resource.ResourceServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import jakarta.annotation.PostConstruct;

@Configuration
public class LanguageConfig {

    private static final Logger logger = LoggerFactory.getLogger(LanguageConfig.class);

    @Autowired
    private LanguageResourceService languageResourceService;

    @PostConstruct
    public void loadLanguageResources() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] resources = resolver.getResources("classpath*:translations/*.json");
        for (org.springframework.core.io.Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename != null) {
                String languageKey = filename.replace(".json", "");
                try (InputStream in = resource.getInputStream()) {
                    byte[] data = in.readAllBytes();
                    languageResourceService.addLanguageResource(languageKey,
                            new ByteArrayResource("application/json", data));
                    // Extract source JAR/module from resource URL for better logging
                    String source = extractSource(resource);
                    logger.info("Registered translation: /translations/{}.json [{}]", languageKey, source);
                }
            }
        }
    }

    /**
     * Extracts the source module/JAR name from a classpath resource URL.
     * Handles both JAR and file-based (exploded) classpath resources.
     */
    private String extractSource(org.springframework.core.io.Resource resource) {
        try {
            String url = resource.getURL().toString();
            // JAR format: jar:file:/path/guacamole-auth-totp-starter.jar!/translations/en.json
            if (url.startsWith("jar:")) {
                int start = url.lastIndexOf("/");
                int end = url.lastIndexOf(".jar!");
                if (start >= 0 && end > start) {
                    return url.substring(start + 1, end + 4);
                }
            }
            // File format: file:/path/starters/guacamole-auth-totp-starter/target/classes/translations/en.json
            // Extract the starter module name from the path
            if (url.contains("starters/")) {
                int start = url.indexOf("starters/") + 9;
                int end = url.indexOf("/", start);
                if (end > start) {
                    return url.substring(start, end);
                }
            }
            // Fallback for core module resources
            return "guacamole-core";
        } catch (IOException e) {
            return "unknown";
        }
    }
}
