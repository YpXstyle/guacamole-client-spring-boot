package org.apache.guacamole.auth.sso.user;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.guacamole.net.auth.AbstractAuthenticatedUser;
import org.apache.guacamole.net.auth.AuthenticationProvider;
import org.apache.guacamole.net.auth.Credentials;

/**
 * An AuthenticatedUser whose identity has been supplied by an arbitrary SSO
 * service. An SSOAuthenticatedUser may additionally be associated with a set
 * of user-specific parameter tokens to be injected into any connections used
 * by that user.
 */
public class SSOAuthenticatedUser extends AbstractAuthenticatedUser {

    /**
     * Reference to the authentication provider associated with this
     * authenticated user.
     */
    @Autowired
    private AuthenticationProvider authProvider;

    /**
     * The credentials provided when this user was authenticated.
     */
    private Credentials credentials;

    /**
     * The groups that this user belongs to.
     */
    private Set<String> effectiveGroups;

    /**
     * Parameter tokens to be automatically injected for any connections used
     * by this user.
     */
    private Map<String, String> tokens;

    /**
     * Initializes this SSOAuthenticatedUser, associating it with the given
     * username, credentials, groups, and parameter tokens. This function must
     * be invoked for every SSOAuthenticatedUser created.
     *
     * @param username
     *     The username of the user that was authenticated.
     *
     * @param credentials
     *     The credentials provided when this user was authenticated.
     *
     * @param effectiveGroups
     *     The groups that the authenticated user belongs to.
     *
     * @param tokens
     *     A map of all the name/value pairs that should be available
     *     as tokens when connections are established by this user.
     */
    public void init(String username, Credentials credentials,
            Set<String> effectiveGroups, Map<String, String> tokens) {
        this.credentials = credentials;
        this.effectiveGroups = Collections.unmodifiableSet(effectiveGroups);
        this.tokens = Collections.unmodifiableMap(tokens);
        setIdentifier(username);
    }

    /**
     * Returns a Map of the parameter tokens that should be automatically
     * injected into connections used by this user during their session. If
     * there are no parameter tokens applicable to the SSO implementation, this
     * may simply be an empty map.
     *
     * @return
     *     A map of the parameter token name/value pairs that should be
     *     automatically injected into connections used by this user.
     */
    public Map<String, String> getTokens() {
        return tokens;
    }

    @Override
    public AuthenticationProvider getAuthenticationProvider() {
        return authProvider;
    }

    @Override
    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public Set<String> getEffectiveUserGroups() {
        return effectiveGroups;
    }

}
