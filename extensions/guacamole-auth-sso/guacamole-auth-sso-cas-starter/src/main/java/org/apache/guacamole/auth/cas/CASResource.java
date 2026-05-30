package org.apache.guacamole.auth.cas;

import org.apache.guacamole.auth.sso.SSOResource;
import jakarta.ws.rs.Path;

/**
 * REST API resource for CAS authentication.
 * Maps the SSOResource to the correct path for CAS.
 */
@Path("ext/cas")
public class CASResource extends SSOResource {
    // Inherits all functionality from SSOResource
}
