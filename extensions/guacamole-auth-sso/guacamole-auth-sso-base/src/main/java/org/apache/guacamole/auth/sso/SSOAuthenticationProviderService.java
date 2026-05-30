package org.apache.guacamole.auth.sso;

import java.net.URI;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.auth.sso.user.SSOAuthenticatedUser;
import org.apache.guacamole.net.auth.Credentials;

/**
 * Service that authenticates Guacamole users by leveraging an arbitrary SSO
 * service.
 */
public interface SSOAuthenticationProviderService {

    /**
     * Returns an SSOAuthenticatedUser representing the user authenticated by
     * the given credentials. Tokens associated with the returned
     * SSOAuthenticatedUser will automatically be injected into any connections
     * used by that user during their session.
     *
     * @param credentials
     *     The credentials to use for authentication.
     *
     * @return
     *     An SSOAuthenticatedUser representing the user authenticated by the
     *     given credentials.
     *
     * @throws GuacamoleException
     *     If an error occurs while authenticating the user, or if access is
     *     denied.
     */
    SSOAuthenticatedUser authenticateUser(Credentials credentials)
            throws GuacamoleException;

    /**
     * Returns the full URI of the login endpoint to which a user must be
     * redirected in order to authenticate with the SSO identity provider.
     *
     * @return
     *     The full URI of the SSO login endpoint.
     *
     * @throws GuacamoleException
     *     If configuration information required for generating the login URI
     *     cannot be read.
     */
    URI getLoginURI() throws GuacamoleException;

    /**
     * Frees all resources associated with the relevant
     * SSOAuthenticationProvider implementation. This function is automatically
     * invoked when an implementation of SSOAuthenticationProvider is shut
     * down.
     */
    void shutdown();

}
