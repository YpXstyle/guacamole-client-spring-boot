package org.apache.guacamole.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.guacamole.extension.PatchResourceService;
import org.apache.guacamole.resource.ByteArrayResource;
import org.apache.guacamole.resource.Resource;
import org.apache.guacamole.resource.ResourceServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Loads extension frontend resources from classpath (starter JARs) and
 * serves them the same way as the original Guacamole ExtensionModule:
 * - /app.js  - concatenated extension JavaScript (ResourceServlet, 304 support)
 * - /app.css - concatenated extension CSS (ResourceServlet, 304 support)
 * - /api/patches - HTML patches (via PatchRESTService)
 * - /app/ext/{namespace}/{path} - static resources from extensions
 * - /translations/{lang}.json - language resources (TranslationController, 304 support)
 * - /images/logo-64.png  - small favicon (may be overridden by extension)
 * - /images/logo-144.png - large favicon (may be overridden by extension)
 */
@Configuration
public class ExtensionResourceConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionResourceConfig.class);

    @Autowired
    private Environment environment;

    // Static reference so the ResourceResolver can access the cache
    private static Map<String, CachedResourceData> staticResourceCache;

    /**
     * Holds pre-loaded resource data along with its MIME type.
     */
    private static class CachedResourceData {
        final byte[] data;
        final String mimeType;
        CachedResourceData(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }
    }

    /**
     * Adapts Spring Resource to Guacamole Resource interface.
     */
    static class SpringResource implements Resource {
        private final org.springframework.core.io.Resource delegate;
        private final String mimeType;

        SpringResource(org.springframework.core.io.Resource delegate, String mimeType) {
            this.delegate = delegate;
            this.mimeType = mimeType;
        }

        @Override
        public String getMimeType() { return mimeType; }

        @Override
        public long getLastModified() {
            try { return delegate.lastModified(); }
            catch (IOException e) { return 0; }
        }

        @Override
        public InputStream asStream() {
            try { return delegate.getInputStream(); }
            catch (IOException e) { return null; }
        }
    }

    /**
     * Scans classpath for all guac-manifest.json files and parses them.
     * Extensions with a configProperty field are only included if the
     * corresponding configuration property is set to true (or not set).
     */
    @Bean
    public List<Map<String, Object>> extensionManifests(ResourcePatternResolver resolver) {
        List<Map<String, Object>> manifests = new ArrayList<>();
        try {
            org.springframework.core.io.Resource[] resources =
                resolver.getResources("classpath*:guac-manifest.json");

            for (org.springframework.core.io.Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String json = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> manifest =
                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
                    if (manifest != null) {
                        // Check if extension is enabled via configProperty
                        String configProperty = (String) manifest.get("configProperty");
                        if (configProperty != null) {
                            Boolean enabled = environment.getProperty(configProperty, Boolean.class, true);
                            if (!enabled) {
                                logger.info("Extension '{}' disabled by config: {}",
                                        manifest.get("name"), configProperty);
                                continue;
                            }
                        }
                        manifests.add(manifest);
                        logger.info("Loaded extension manifest: {}", manifest.get("name"));
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse manifest: {}", resource, e);
                }
            }
        } catch (IOException e) {
            logger.warn("Error scanning for extension manifests", e);
        }
        return manifests;
    }

    /**
     * Pre-loads all extension static resources at startup using ClassLoader.getResources().
     * This works correctly with nested JARs in Spring Boot fat JARs.
     * Key: namespace/resourcePath (e.g., "totp/templates/authenticationCodeField.html")
     * Value: CachedResourceData containing resource bytes and MIME type from manifest
     */
    @Bean
    public Map<String, CachedResourceData> extensionStaticResourceCache(List<Map<String, Object>> extensionManifests) {
        Map<String, CachedResourceData> cache = new HashMap<>();
        ClassLoader classLoader = getClass().getClassLoader();
        for (Map<String, Object> manifest : extensionManifests) {
            String namespace = (String) manifest.get("namespace");
            if (namespace == null) continue;
            @SuppressWarnings("unchecked")
            Map<String, String> resources = (Map<String, String>) manifest.get("resources");
            if (resources != null) {
                for (Map.Entry<String, String> entry : resources.entrySet()) {
                    String resourcePath = entry.getKey();
                    String mimeType = entry.getValue();
                    String cacheKey = namespace + "/" + resourcePath;
                    if (cache.containsKey(cacheKey)) continue;
                    try {
                        Enumeration<URL> urls = classLoader.getResources(resourcePath);
                        if (urls.hasMoreElements()) {
                            URL url = urls.nextElement();
                            try (InputStream is = url.openStream()) {
                                byte[] data = is.readAllBytes();
                                cache.put(cacheKey, new CachedResourceData(data, mimeType));
                                logger.info("Pre-loaded static resource: {} ({} bytes, {})", cacheKey, data.length, mimeType);
                            }
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to pre-load resource: {}", cacheKey, e);
                    }
                }
            }
        }
        // Set static reference for ResourceResolver to access
        staticResourceCache = cache;
        return cache;
    }

    /**
     * Provides PatchResourceService with HTML patches from all extensions.
     */
    @Bean
    public PatchResourceService patchResourceService(List<Map<String, Object>> extensionManifests) {
        PatchResourceService service = new PatchResourceService();
        for (Map<String, Object> manifest : extensionManifests) {
            @SuppressWarnings("unchecked")
            List<String> htmlFiles = (List<String>) manifest.get("html");
            if (htmlFiles != null) {
                for (String htmlFile : htmlFiles) {
                    ClassPathResource resource = new ClassPathResource(htmlFile);
                    if (resource.exists()) {
                        service.addPatchResource(new SpringResource(resource, "text/html"));
                        logger.info("Loaded HTML patch: {}", htmlFile);
                    }
                }
            }
        }
        return service;
    }

    /**
     * Concatenates all extension JavaScript files from guac-manifest.json.
     * verifyCachedVersion.js is prepended first, matching the original
     * ExtensionModule behavior.
     */
    @Bean
    public String extensionJavaScript(List<Map<String, Object>> extensionManifests) {
        StringBuilder sb = new StringBuilder();

        // Prepend verifyCachedVersion.js (matches original ExtensionModule line 631)
        ClassPathResource verifyCached = new ClassPathResource("static/verifyCachedVersion.js");
        if (verifyCached.exists()) {
            try (InputStream is = verifyCached.getInputStream()) {
                sb.append(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
                sb.append("\n");
                logger.info("Loaded verifyCachedVersion.js");
            } catch (IOException e) {
                logger.warn("Failed to load verifyCachedVersion.js", e);
            }
        }

        for (Map<String, Object> manifest : extensionManifests) {
            @SuppressWarnings("unchecked")
            List<String> jsFiles = (List<String>) manifest.get("js");
            if (jsFiles != null) {
                for (String jsFile : jsFiles) {
                    ClassPathResource resource = new ClassPathResource(jsFile);
                    if (resource.exists()) {
                        try (InputStream is = resource.getInputStream()) {
                            sb.append(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
                            sb.append("\n");
                            logger.info("Loaded extension JS: {}", jsFile);
                        } catch (IOException e) {
                            logger.warn("Failed to load JS: {}", jsFile, e);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Concatenates all extension CSS files from guac-manifest.json.
     */
    @Bean
    public String extensionCSS(List<Map<String, Object>> extensionManifests) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> manifest : extensionManifests) {
            @SuppressWarnings("unchecked")
            List<String> cssFiles = (List<String>) manifest.get("css");
            if (cssFiles != null) {
                for (String cssFile : cssFiles) {
                    ClassPathResource resource = new ClassPathResource(cssFile);
                    if (resource.exists()) {
                        try (InputStream is = resource.getInputStream()) {
                            sb.append(StreamUtils.copyToString(is, StandardCharsets.UTF_8));
                            sb.append("\n");
                            logger.info("Loaded extension CSS: {}", cssFile);
                        } catch (IOException e) {
                            logger.warn("Failed to load CSS: {}", cssFile, e);
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Registers ResourceServlet for /app.js with 304 caching support.
     * Matches original Guacamole ExtensionModule behavior.
     */
    @Bean
    public ServletRegistrationBean<ResourceServlet> appJsServlet(String extensionJavaScript) {
        byte[] jsBytes = extensionJavaScript.getBytes(StandardCharsets.UTF_8);
        ResourceServlet servlet = new ResourceServlet(
                new ByteArrayResource("application/javascript", jsBytes));
        ServletRegistrationBean<ResourceServlet> reg =
                new ServletRegistrationBean<>(servlet, "/app.js");
        reg.setLoadOnStartup(1);
        return reg;
    }

    /**
     * Registers ResourceServlet for /app.css with 304 caching support.
     * Matches original Guacamole ExtensionModule behavior.
     */
    @Bean
    public ServletRegistrationBean<ResourceServlet> appCssServlet(String extensionCSS) {
        byte[] cssBytes = extensionCSS.getBytes(StandardCharsets.UTF_8);
        ResourceServlet servlet = new ResourceServlet(
                new ByteArrayResource("text/css", cssBytes));
        ServletRegistrationBean<ResourceServlet> reg =
                new ServletRegistrationBean<>(servlet, "/app.css");
        reg.setLoadOnStartup(1);
        return reg;
    }

    /**
     * Registers ResourceServlet for /images/logo-64.png with 304 caching.
     * If an extension provides a smallIcon via guac-manifest.json, it
     * overrides the default logo. Matches original ExtensionModule behavior.
     */
    @Bean
    public ServletRegistrationBean<ResourceServlet> smallIconServlet(
            List<Map<String, Object>> extensionManifests) {
        byte[] iconData = loadExtensionIcon(extensionManifests, "smallIcon",
                "static/images/logo-64.png");
        ResourceServlet servlet = new ResourceServlet(
                new ByteArrayResource("image/png", iconData));
        ServletRegistrationBean<ResourceServlet> reg =
                new ServletRegistrationBean<>(servlet, "/images/logo-64.png");
        reg.setLoadOnStartup(1);
        return reg;
    }

    /**
     * Registers ResourceServlet for /images/logo-144.png with 304 caching.
     * If an extension provides a largeIcon via guac-manifest.json, it
     * overrides the default logo. Matches original ExtensionModule behavior.
     */
    @Bean
    public ServletRegistrationBean<ResourceServlet> largeIconServlet(
            List<Map<String, Object>> extensionManifests) {
        byte[] iconData = loadExtensionIcon(extensionManifests, "largeIcon",
                "static/images/logo-144.png");
        ResourceServlet servlet = new ResourceServlet(
                new ByteArrayResource("image/png", iconData));
        ServletRegistrationBean<ResourceServlet> reg =
                new ServletRegistrationBean<>(servlet, "/images/logo-144.png");
        reg.setLoadOnStartup(1);
        return reg;
    }

    /**
     * Loads icon data from extension manifests. If any extension provides
     * the named icon field in its manifest, that icon is loaded (last
     * extension wins). Falls back to the default classpath resource.
     */
    private byte[] loadExtensionIcon(List<Map<String, Object>> manifests,
            String fieldName, String defaultPath) {
        ClassLoader classLoader = getClass().getClassLoader();
        for (Map<String, Object> manifest : manifests) {
            String iconPath = (String) manifest.get(fieldName);
            if (iconPath != null) {
                try {
                    Enumeration<URL> urls = classLoader.getResources(iconPath);
                    if (urls.hasMoreElements()) {
                        try (InputStream is = urls.nextElement().openStream()) {
                            byte[] data = is.readAllBytes();
                            logger.info("Extension overrides {}: {} ({} bytes)",
                                    fieldName, iconPath, data.length);
                            return data;
                        }
                    }
                } catch (IOException e) {
                    logger.warn("Failed to load extension {}: {}", fieldName, iconPath, e);
                }
            }
        }
        // Fall back to default icon
        try {
            Enumeration<URL> urls = classLoader.getResources(defaultPath);
            if (urls.hasMoreElements()) {
                try (InputStream is = urls.nextElement().openStream()) {
                    return is.readAllBytes();
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to load default icon: {}", defaultPath, e);
        }
        return new byte[0];
    }

    /**
     * Registers a custom resource handler for /app/ext/** that serves
     * pre-loaded extension static resources from cache. This has priority
     * over Spring Boot's default static resource handler.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/ext/**")
                .addResourceLocations("classpath:/ext-cache/")
                .resourceChain(false)
                .addResolver(new CachedExtensionResourceResolver());
    }

    /**
     * Custom ResourceResolver that serves resources from the pre-loaded cache.
     * Handles nested JARs in Spring Boot fat JARs correctly.
     */
    private static class CachedExtensionResourceResolver extends AbstractResourceResolver {

        private static final Logger log = LoggerFactory.getLogger(CachedExtensionResourceResolver.class);

        @Override
        protected org.springframework.core.io.Resource resolveResourceInternal(
                HttpServletRequest request,
                String requestPath,
                List<? extends org.springframework.core.io.Resource> locations,
                ResourceResolverChain chain) {
            Map<String, CachedResourceData> cache = staticResourceCache;
            if (cache != null) {
                CachedResourceData entry = cache.get(requestPath);
                if (entry != null) {
                    log.debug("Serving extension resource from cache: {} ({} bytes)", requestPath, entry.data.length);
                    return new CachedResource(requestPath, entry.data, entry.mimeType);
                }
            }
            return chain.resolveResource(request, requestPath, locations);
        }

        @Override
        protected String resolveUrlPathInternal(
                String resourcePath,
                List<? extends org.springframework.core.io.Resource> locations,
                ResourceResolverChain chain) {
            return chain.resolveUrlPath(resourcePath, locations);
        }
    }

    /**
     * Resource implementation backed by a byte array from the cache.
     * Supports correct MIME type and HTTP 304 caching.
     */
    private static class CachedResource implements org.springframework.core.io.Resource {
        private static final long STARTUP_TIME = System.currentTimeMillis();

        private final String path;
        private final byte[] data;
        private final String mimeType;

        CachedResource(String path, byte[] data, String mimeType) {
            this.path = path;
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override public boolean exists() { return true; }
        @Override public boolean isReadable() { return true; }
        @Override public boolean isOpen() { return false; }
        @Override public URL getURL() { return null; }
        @Override public URI getURI() { return null; }
        @Override public File getFile() { return null; }
        @Override public long contentLength() { return data.length; }
        @Override public long lastModified() { return STARTUP_TIME; }
        @Override public org.springframework.core.io.Resource createRelative(String relativePath) { return null; }

        @Override
        public String getFilename() {
            // Return only the filename (not full path) so Spring can determine MIME type
            int lastSlash = path.lastIndexOf('/');
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        }

        @Override public String getDescription() { return "Cached extension resource: " + path; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
    }
}
