package com.medai.config;

import com.medai.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "med-ai-assistant", "env", "production");
    }

    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    public static class TraceCorrelationFilter implements Filter {

        public static final String TRACE_ID_HEADER = "X-Trace-Id";
        public static final String MDC_TRACE_ID = "trace_id";
        public static final String MDC_TENANT_ID = "tenant_id";

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            String traceId = req.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            MDC.put(MDC_TRACE_ID, traceId);
            res.setHeader(TRACE_ID_HEADER, traceId);

            UUID tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                MDC.put(MDC_TENANT_ID, tenantId.toString());
            }

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_TRACE_ID);
                MDC.remove(MDC_TENANT_ID);
            }
        }
    }
}
