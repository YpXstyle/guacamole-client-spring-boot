package org.apache.guacamole.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.guacamole.extension.LanguageResourceService;
import org.apache.guacamole.resource.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Loads translation files ({@code /translations/*.json}) from core and from
 * enabled extension modules only.
 *
 * <h3>How filtering works</h3>
 * Every classpath resource URL carries the module it belongs to.
 * We extract a <em>module area</em> from each URL — the portion before the
 * class-output directory ({@code /target/classes/}, {@code /build/classes/},
 * …) or before the JAR entry path.  Two areas that are equal (or where one is
 * a parent of the other) belong to the same logical module group.
 *
 * <ol>
 *   <li>Parse every {@code guac-manifest.json}, extract its area, check
 *       {@code configProperty} → build {@code enabledAreas} plus
 *       {@code manifestGroupAreas} (= manifest areas + their parents for
 *       shared-base detection).</li>
 *   <li>For sub-module manifests (JDBC, SSO) add the <em>parent area</em> to
 *       {@code enabledAreas} so shared-base translations are also loaded.</li>
 *   <li>For every {@code /translations/*.json} resource:
 *       <ul>
 *         <li>area ∈ enabledAreas → load (enabled extension)</li>
 *         <li>area ∉ manifestGroupAreas → load (core / external)</li>
 *         <li>otherwise → skip (disabled extension territory)</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * This logic is independent of directory layout conventions — it only relies on
 * class-output-directory markers that are identical across Maven, Gradle and
 * IDE builds.  You may freely rename {@code extensions/}, {@code guacamole/},
 * or any other module directory without affecting translation loading.
 */
@Configuration
public class LanguageConfig {

    private static final Logger logger = LoggerFactory.getLogger(LanguageConfig.class);

    /** Class-output directory markers (checked in order). */
    private static final String[] CLASS_OUTPUT_MARKERS = {
        "/target/classes/",
        "/build/classes/",
        "/out/production/",
    };

    @Autowired
    private LanguageResourceService languageResourceService;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void loadLanguageResources() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // ── Step 1: scan guac-manifest.json → build enabled + group areas ──
        Set<String> enabledAreas = new HashSet<>();
        // manifestGroupAreas = manifest areas + their parents — used to tell
        // "extension territory" (has manifests) from "core territory" (no manifest).
        // Shared-base modules (JDBC base, SSO base) fall into extension territory
        // via the parent match even though they don't have their own manifest.
        Set<String> manifestGroupAreas = new HashSet<>();

        org.springframework.core.io.Resource[] manifestResources =
                resolver.getResources("classpath*:guac-manifest.json");

        for (org.springframework.core.io.Resource mRes : manifestResources) {
            String manifestUrl = mRes.getURL().toString();
            Map<String, Object> manifest = parseManifest(mRes);
            if (manifest == null) continue;

            String area = extractModuleArea(manifestUrl);
            if (area == null) continue;

            // Always record this module's territory (manifest area + parent)
            manifestGroupAreas.add(area);
            String parent = extractParentArea(area);
            if (parent != null) {
                manifestGroupAreas.add(parent);
            }

            String configProperty = (String) manifest.get("configProperty");
            boolean enabled = configProperty == null
                    || environment.getProperty(configProperty, Boolean.class, false);

            if (enabled) {
                enabledAreas.add(area);
                // shared-base modules: also allow the parent area
                if (parent != null) {
                    enabledAreas.add(parent);
                }
                logger.info("Extension translations enabled: {} [area={}]", manifest.get("name"), area);
            } else {
                logger.debug("Extension translations disabled: {} (config={})",
                        manifest.get("name"), configProperty);
            }
        }

        logger.info("Enabled areas: {}", enabledAreas);

        // ── Step 2: load translations ──
        org.springframework.core.io.Resource[] resources =
                resolver.getResources("classpath*:translations/*.json");

        int loaded = 0;
        int skipped = 0;

        for (org.springframework.core.io.Resource resource : resources) {
            String url = resource.getURL().toString();
            String filename = resource.getFilename();
            if (filename == null) continue;

            String area = extractModuleArea(url);
            String source = extractSource(resource);

            if (area != null && areaMatches(area, enabledAreas)) {
                // Belongs to an enabled module (or its shared base)
                loadOne(resource, filename, source);
                loaded++;
            } else if (area == null || !areaMatches(area, manifestGroupAreas)) {
                // Not from any extension territory → core or external → always load
                loadOne(resource, filename, source);
                loaded++;
            } else {
                logger.debug("Skipping translation from disabled module: /translations/{} [{}]", filename, source);
                skipped++;
            }
        }

        logger.info("Translation loading complete: {} loaded, {} skipped (disabled modules)", loaded, skipped);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void loadOne(org.springframework.core.io.Resource resource,
                         String filename, String source) {
        String languageKey = filename.replace(".json", "");
        try (InputStream in = resource.getInputStream()) {
            byte[] data = in.readAllBytes();
            languageResourceService.addLanguageResource(languageKey,
                    new ByteArrayResource("application/json", data));
            logger.info("Registered translation: /translations/{}.json [{}]", languageKey, source);
        } catch (IOException e) {
            logger.warn("Failed to load translation: {} [{}]", filename, source, e);
        }
    }

    /**
     * Extracts the <em>module area</em> from a classpath resource URL.
     * The area is the path prefix up to (but not including) the class-output
     * directory or JAR entry boundary.
     *
     * <pre>
     *   file:/.../guacamole-core/target/classes/translations/en.json  →  .../guacamole-core
     *   file:/.../some-group/my-starter/target/classes/translations/  →  .../some-group/my-starter
     *   jar:file:/app.jar!/BOOT-INF/classes/translations/en.json      →  .../app.jar
     *   jar:file:/app.jar!/BOOT-INF/lib/my-starter-1.0.jar!/...       →  .../app.jar!/BOOT-INF/lib/my-starter-1.0.jar
     * </pre>
     *
     * @return lower-case area string, or {@code null} if indeterminable
     */
    static String extractModuleArea(String url) {
        String lower = url.toLowerCase();

        // ── Fat JAR / nested JAR ──
        if (lower.contains("!/")) {
            // Core inside fat JAR: .../app.jar!/BOOT-INF/classes/...
            int classesIdx = lower.indexOf("/boot-inf/classes/");
            if (classesIdx >= 0) {
                return lower.substring(0, classesIdx);
            }
            // Extension inside fat JAR: .../app.jar!/BOOT-INF/lib/<jar>!/
            int bootLib = lower.indexOf("/boot-inf/lib/");
            if (bootLib >= 0) {
                int jarEnd = lower.indexOf(".jar!", bootLib);
                if (jarEnd > 0) {
                    return lower.substring(0, jarEnd + 4); // include ".jar"
                }
            }
            // Other nested JAR
            int jarEnd = lower.indexOf(".jar!");
            if (jarEnd > 0) {
                return lower.substring(0, jarEnd + 4);
            }
        }

        // ── Exploded / dev layout ──
        for (String marker : CLASS_OUTPUT_MARKERS) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                return lower.substring(0, idx);
            }
        }

        return null;
    }

    /**
     * Returns the parent area (one directory level up), or {@code null} if
     * the area has no meaningful parent.
     *
     * Only returns a parent when the parent directory name itself looks like a
     * module-group (contains at least one hyphen).  This prevents top-level
     * container directories ("extensions", "modules", …) from being added to the
     * allow-list and accidentally matching every module beneath them.
     *
     * <pre>
     *   .../group/my-starter          →  .../group      (group = "guacamole-auth-jdbc")
     *   .../extensions/my-starter     →  null           (parent = "extensions", not a module group)
     *   .../BOOT-INF/lib/jar          →  null           (parent = "lib", not a module group)
     * </pre>
     */
    static String extractParentArea(String area) {
        int lastSlash = area.lastIndexOf('/');
        if (lastSlash <= 0) return null;
        String candidate = area.substring(0, lastSlash);
        // For fat-JAR areas that end with ".jar", strip the jar filename first
        if (candidate.endsWith(".jar")) {
            int prevSlash = candidate.lastIndexOf('/');
            if (prevSlash > 0) candidate = candidate.substring(0, prevSlash);
        }
        // Only accept parent if its name contains a hyphen (module-group convention)
        String parentName = candidate.substring(candidate.lastIndexOf('/') + 1);
        if (!parentName.contains("-")) return null;
        return candidate;
    }

    /**
     * Returns {@code true} when {@code area} matches at least one entry in
     * {@code areas}.  Matching is either exact or prefix-with-slash-boundary
     * (to prevent {@code guacamole-auth} from accidentally matching
     * {@code guacamole-auth-totp}).
     */
    static boolean areaMatches(String area, Set<String> areas) {
        if (areas.contains(area)) return true;
        for (String a : areas) {
            if (area.startsWith(a + "/")) return true;
        }
        return false;
    }

    private Map<String, Object> parseManifest(org.springframework.core.io.Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            String json = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new ObjectMapper().readValue(json, Map.class);
            return map;
        } catch (Exception e) {
            logger.warn("Failed to parse manifest: {}", resource, e);
            return null;
        }
    }

    /** Human-readable source name for logging (best-effort). */
    private String extractSource(org.springframework.core.io.Resource resource) {
        try {
            String url = resource.getURL().toString();
            if (url.startsWith("jar:")) {
                int start = url.lastIndexOf("/");
                int end = url.lastIndexOf(".jar!");
                if (start >= 0 && end > start) {
                    return url.substring(start + 1, end + 4);
                }
            }
            // Try to extract a meaningful directory name
            String area = extractModuleArea(url);
            if (area != null) {
                int lastSlash = area.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < area.length() - 1) {
                    return area.substring(lastSlash + 1);
                }
            }
            return "guacamole";
        } catch (IOException e) {
            return "unknown";
        }
    }
}
