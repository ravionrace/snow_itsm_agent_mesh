package com.acme.itsm.orchestrator.channel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Rejects any /channel/servicenow request that isn't signed by the instance; passes the verified user on. */
public class ChannelSignatureFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "itsm.channel.user";
    private static final Logger log = LoggerFactory.getLogger(ChannelSignatureFilter.class);
    private static final int MAX_BODY = 64 * 1024;

    private final ChannelSignature signature;

    public ChannelSignatureFilter(ChannelSignature signature) {
        this.signature = signature;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = req.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            res.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        String user = req.getHeader("X-Mesh-User");
        String problem = signature.verify(req.getHeader("X-Mesh-Timestamp"), req.getHeader("X-Mesh-Nonce"),
                req.getMethod(), req.getRequestURI(), user, new String(body, StandardCharsets.UTF_8),
                req.getHeader("X-Mesh-Signature"));
        if (problem != null) {
            log.warn("ServiceNow channel request rejected from {}: {}", req.getRemoteAddr(), problem);
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        req.setAttribute(USER_ATTRIBUTE, user);
        chain.doFilter(new CachedBody(req, body), res);
    }

    /** Lets the controller read the body the filter already consumed. */
    static final class CachedBody extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBody(HttpServletRequest req, byte[] body) {
            super(req);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public boolean isFinished() { return in.available() == 0; }
                public boolean isReady() { return true; }
                public void setReadListener(ReadListener l) { }
                public int read() { return in.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
