package racetimingms.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class TransactDatasourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSourceProperties tracnsactDataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        return properties;
    }

    @Bean(name = "transactdbDS")
    @ConfigurationProperties("spring.datasource.mysql")
    public DataSource transactDataSource() {
        return tracnsactDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate transactdbJdbcTemplate(@Qualifier("transactdbDS") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate transactdbNamedParameterJdbcTemplate(
            @Qualifier("transactdbDS") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "transactTransactionManager")
    public TransactionManager transactTransactionManager() {
        return new DataSourceTransactionManager(transactDataSource());
    }
}
