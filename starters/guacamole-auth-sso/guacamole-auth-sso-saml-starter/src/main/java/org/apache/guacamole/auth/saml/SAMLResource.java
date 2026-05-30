package org.apache.guacamole.auth.saml;

import org.apache.guacamole.auth.sso.SSOResource;
import jakarta.ws.rs.Path;

/**
 * REST API resource for SAML authentication.
 * Maps the SSOResource to the correct path for SAML.
 */
@Path("ext/saml")
public class SAMLResource extends SSOResource {
    // Inherits all functionality from SSOResource
}
