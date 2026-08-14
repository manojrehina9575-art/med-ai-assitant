package com.medai.config;

import com.medai.tenant.TenantHibernateFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the tenant Hibernate filter interceptor so it runs
 * after Spring Security (which sets TenantContext from the JWT).
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TenantHibernateFilter tenantHibernateFilter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantHibernateFilter)
                .addPathPatterns("/api/**");
    }
}
