package org.apache.guacamole.auth.sso;

import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.apache.guacamole.GuacamoleException;

/**
 * REST API resource that provides allows the user to be manually redirected to
 * the applicable identity provider. Implementations may also provide
 * additional resources and endpoints beneath this resource as needed.
 */
@RestController
@RequestMapping("login")
public class SSOResource {

    /**
     * Service for authenticating users using SSO.
     */
    @Autowired
    private SSOAuthenticationProviderService authService;

    /**
     * Redirects the user to the relevant identity provider. If the SSO
     * extension defining this resource is not the primary extension, and thus
     * the user will not be automatically redirected to the IdP, this endpoint
     * allows that redirect to occur manually upon a link/button click.
     *
     * @return
     *     An HTTP Response that will redirect the user to the IdP.
     *
     * @throws GuacamoleException
     *     If an error occurs preventing the redirect from being created.
     */
    @GetMapping
    public ResponseEntity<Object> redirectToIdentityProvider() throws GuacamoleException {
        URI loginURI = authService.getLoginURI();
        return ResponseEntity.status(HttpStatus.FOUND).location(loginURI).build();
    }

}
