package com.acme.itsm.orchestrator.security;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Every /api call needs a valid token from the corporate IdP (validated against the issuer's keys). DEV mode
 * trusts an X-Dev-User header instead and must never be enabled outside a developer machine.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final Pattern DEV_USER = Pattern.compile("^[A-Za-z0-9._@-]{2,80}$");

    @Bean
    public SecurityFilterChain api(HttpSecurity http, OrchestratorProperties props) throws Exception {
        http.csrf(c -> c.disable()) // bearer tokens only, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/actuator/health").permitAll()
                        // Authenticated by the instance's HMAC signature (ChannelSignatureFilter), not by a session.
                        .requestMatchers("/channel/servicenow/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());
        if (props.getAuth().getMode() == OrchestratorProperties.AuthMode.DEV) {
            log.warn("itsm.auth.mode=DEV: trusting the X-Dev-User header. Never use this outside local development.");
            http.addFilterBefore(new DevUserFilter(), AnonymousAuthenticationFilter.class)
                    .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        } else {
            http.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }

    static final class DevUserFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String user = req.getHeader("X-Dev-User");
            if (user != null && DEV_USER.matcher(user).matches()) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, "dev", AuthorityUtils.createAuthorityList("ROLE_USER")));
            }
            chain.doFilter(req, res);
        }
    }
}
