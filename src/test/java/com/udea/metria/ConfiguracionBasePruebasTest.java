package com.udea.metria;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ConfiguracionBasePruebasTest {

	@Test
	void ignoraDestinosYEsquemasContradictoriosSinConectarse() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
				.withUserConfiguration(ConfiguracionSimulada.class)
				.withPropertyValues(
						"spring.datasource.url=jdbc:postgresql://otro/datos",
						"spring.datasource.hikari.jdbc-url=jdbc:postgresql://otro/datos",
						"spring.flyway.url=jdbc:postgresql://otro/datos",
						"spring.flyway.user=otro",
						"spring.flyway.password=otra",
						"spring.flyway.default-schema=otro",
						"spring.flyway.schemas=otro",
						"spring.flyway.create-schemas=true")
				.run(contexto -> {
					DataSource dataSource = contexto.getBean(DataSource.class);
					Flyway flyway = contexto.getBean(Flyway.class);
					assertEquals("jdbc:postgresql://servidor/metria_test",
							((DriverManagerDataSource) dataSource).getUrl());
					assertSame(dataSource, flyway.getConfiguration().getDataSource());
					assertEquals("metria", flyway.getConfiguration().getDefaultSchema());
					assertArrayEquals(new String[] { "metria" }, flyway.getConfiguration().getSchemas());
					assertFalse(flyway.getConfiguration().isCreateSchemas());
				});
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ConfiguracionSimulada extends ConfiguracionBasePruebas {
		@Override
		@Bean
		@Primary
		@FlywayDataSource
		DataSource dataSourcePruebas() {
			return crearDataSource("jdbc:postgresql://servidor/metria_test", "usuario", "clave", "SI");
		}

		@Bean
		FlywayMigrationStrategy sinConexion() {
			return flyway -> { };
		}
	}
}
