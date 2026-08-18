package com.medai.config;

import com.medai.tenant.TenantAwareDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wraps the application's {@link DataSource} in {@link TenantAwareDataSource} so that row-level
 * security sees the right tenant on every connection.
 *
 * <p>Done as a post-processor rather than by declaring the bean directly, so Spring Boot keeps
 * ownership of Hikari's configuration and property binding — this only decorates the result.
 */
@Configuration
public class DataSourceTenantConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(dataSource);
        }
        return bean;
    }
}
