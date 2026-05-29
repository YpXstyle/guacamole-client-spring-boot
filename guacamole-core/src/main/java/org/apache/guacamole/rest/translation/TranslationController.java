package org.apache.guacamole.rest.translation;

import java.io.IOException;
import java.io.InputStream;
import org.apache.guacamole.extension.LanguageResourceService;
import org.apache.guacamole.resource.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TranslationController {

    private static final long STARTUP_TIME = System.currentTimeMillis();

    @Autowired
    private LanguageResourceService languageResourceService;

    @GetMapping("/translations/{key}.json")
    public ResponseEntity<byte[]> getTranslation(
            @PathVariable String key,
            HttpServletRequest request) throws IOException {

        Resource resource = languageResourceService.getLanguageResources().get(key);
        if (resource == null)
            return ResponseEntity.notFound().build();

        // Check 304 Not Modified
        long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        if (ifModifiedSince != -1 && STARTUP_TIME - ifModifiedSince < 1000) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        try (InputStream in = resource.asStream()) {
            if (in == null)
                return ResponseEntity.notFound().build();

            byte[] data = in.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .lastModified(STARTUP_TIME)
                    .body(data);
        }
    }
}
