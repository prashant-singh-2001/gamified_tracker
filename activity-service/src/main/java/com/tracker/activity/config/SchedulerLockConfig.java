package com.tracker.activity.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class SchedulerLockConfig {

    // Table name is schema-qualified because Hibernate's default_schema property makes JPA
    // fully-qualify every generated statement, but this plain JdbcTemplate lookup does not go
    // through Hibernate -- it would otherwise resolve "shedlock" against the connection's own
    // default search_path (public), not the "activity" schema the V5 migration creates it in.
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .withTableName("activity.shedlock")
                        .build());
    }
}
