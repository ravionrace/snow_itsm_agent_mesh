package com.acme.itsm.orchestrator.security;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** The signed-in user, how ServiceNow knows them, and their IdP token (for the IdP-based token modes). */
@Component
public class CurrentUser {

    private final OrchestratorProperties props;

    public CurrentUser(OrchestratorProperties props) {
        this.props = props;
    }

    /**
     * @param id        stable user id used for sessions and auditing in the mesh
     * @param snSubject the value ServiceNow matches against the OAuth registry's user field (JWT "sub")
     * @param idpToken  the raw SSO access token (only for PASSTHROUGH / JWT_BEARER)
     */
    public record User(String id, String snSubject, String idpToken) {
    }

    public User resolve(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) {
            String id = claim(jwt, props.getAuth().getUserClaim());
            String subject = claim(jwt, props.getServicenow().getSubjectClaim());
            return new User(id, subject != null ? subject : id, jwt.getToken().getTokenValue());
        }
        return new User(auth.getName(), auth.getName(), null);
    }

    private static String claim(JwtAuthenticationToken jwt, String name) {
        String v = jwt.getToken().getClaimAsString(name);
        return v == null || v.isBlank() ? jwt.getToken().getSubject() : v;
    }
}
