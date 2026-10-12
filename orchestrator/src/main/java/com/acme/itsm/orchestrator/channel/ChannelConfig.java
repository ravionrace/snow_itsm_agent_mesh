package com.acme.itsm.orchestrator.channel;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

@Configuration
@ConditionalOnProperty(prefix = "itsm.channel", name = "enabled", havingValue = "true")
public class ChannelConfig {

    @Bean
    public ChannelSignature channelSignature(OrchestratorProperties props) {
        var c = props.getChannel();
        return new ChannelSignature(c.getSecret(), c.getMaxSkew(), Clock.systemUTC());
    }

    @Bean(destroyMethod = "shutdown")
    public TurnJobs turnJobs(OrchestratorProperties props) {
        var c = props.getChannel();
        return new TurnJobs(c.getMaxConcurrentTurns(), c.getMaxQueuedTurns(), c.getResultTtl(), Clock.systemUTC());
    }

    /** Runs before Spring Security, so unsigned requests never reach the application. */
    @Bean
    public FilterRegistrationBean<ChannelSignatureFilter> channelSignatureFilter(ChannelSignature signature) {
        FilterRegistrationBean<ChannelSignatureFilter> reg = new FilterRegistrationBean<>(new ChannelSignatureFilter(signature));
        reg.addUrlPatterns("/channel/servicenow/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}
