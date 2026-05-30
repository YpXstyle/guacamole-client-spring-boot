package org.apache.guacamole.auth.openid;

import org.apache.guacamole.auth.sso.SSOResource;
import jakarta.ws.rs.Path;

/**
 * REST API resource for OpenID Connect authentication.
 * Maps the SSOResource to the correct path for OpenID.
 */
@Path("ext/openid")
public class OpenIDResource extends SSOResource {
    // Inherits all functionality from SSOResource
}
