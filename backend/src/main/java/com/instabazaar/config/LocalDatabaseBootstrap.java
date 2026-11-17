package com.instabazaar.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Creates the local development database before DataSource or Flyway beans are instantiated. */
@Component
@Profile("!test & !production & (default | local)")
@ConditionalOnProperty(
    prefix = "instabazaar.database.bootstrap",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class LocalDatabaseBootstrap
    implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {
  private static final Logger LOG = LoggerFactory.getLogger(LocalDatabaseBootstrap.class);
  private static final Pattern LOCAL_DATABASE =
      Pattern.compile(
          "(jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost|\\[::1\\])(?::[0-9]{1,5})?)"
              + "/instabazaar(\\?[^\\r\\n]*)?");
  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    var target = LOCAL_DATABASE.matcher(environment.getProperty("spring.datasource.url", ""));
    // Custom database names and remote databases remain owned by their deployment configuration.
    if (!target.matches()) return;
    String maintenanceUrl =
        target.group(1) + "/postgres" + (target.group(2) == null ? "" : target.group(2));
    var properties = new Properties();
    properties.setProperty("user", environment.getRequiredProperty("spring.datasource.username"));
    properties.setProperty("password", environment.getProperty("spring.datasource.password", ""));
    properties.setProperty("connectTimeout", "3");
    properties.setProperty("socketTimeout", "30");
    try (var connection = DriverManager.getConnection(maintenanceUrl, properties)) {
      // PostgreSQL CREATE DATABASE is prohibited inside a transaction.
      connection.setAutoCommit(true);
      if (exists(connection)) return;
      try (var statement = connection.createStatement()) {
        statement.setQueryTimeout(30);
        statement.executeUpdate("CREATE DATABASE instabazaar");
        LOG.info("Created local PostgreSQL database 'instabazaar' before Flyway.");
      } catch (SQLException failure) {
        // Another starting process may create the same database between our check and CREATE.
        if (!(("42P04".equals(failure.getSQLState()) || "23505".equals(failure.getSQLState()))
            && exists(connection))) throw failure;
      }
    } catch (SQLException failure) {
      throw new BeanInitializationException(
          "Local database bootstrap failed before Flyway (SQLSTATE "
              + failure.getSQLState()
              + "). Ensure PostgreSQL is running and the configured account can connect to "
              + "the postgres maintenance database and create instabazaar.",
          failure);
    }
  }

  private static boolean exists(Connection connection) throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
      statement.setQueryTimeout(30);
      statement.setString(1, "instabazaar");
      try (var rows = statement.executeQuery()) {
        return rows.next();
      }
    }
  }
}
