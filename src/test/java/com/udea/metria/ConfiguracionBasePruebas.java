package com.udea.metria;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Solo para pruebas de contexto. El operador debe comprobar que el servidor y la
 * base sean realmente desechables: el nombre y la confirmación no lo garantizan.
 * Se requiere PostGIS en public y el esquema metria vacío ya creado.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionBasePruebas {

	private static final Pattern URL = Pattern.compile(
			"jdbc:postgresql://[a-zA-Z0-9][a-zA-Z0-9.-]*(?::([0-9]{1,5}))?/[a-zA-Z0-9_]+_(?:test|pruebas)");

	@Bean
	@Primary
	@FlywayDataSource
	DataSource dataSourcePruebas() {
		return crearDataSource(System.getenv("METRIA_TEST_DATABASE_URL"),
				System.getenv("METRIA_TEST_DATABASE_USERNAME"), System.getenv("METRIA_TEST_DATABASE_PASSWORD"),
				System.getenv("METRIA_TEST_DATABASE_DESECHABLE"));
	}

	@Bean
	@Order(Ordered.LOWEST_PRECEDENCE)
	FlywayConfigurationCustomizer flywayPruebas(DataSource dataSourcePruebas) {
		return configuracion -> configuracion.dataSource(dataSourcePruebas)
				.defaultSchema("metria").schemas("metria").createSchemas(false);
	}

	public static DataSource crearDataSource(String url, String usuario, String clave, String confirmacion) {
		Matcher coincidencia = URL.matcher(url == null ? "" : url);
		if (!coincidencia.matches() || usuario == null || usuario.isBlank() || clave == null || clave.isBlank()
				|| !"SI".equals(confirmacion)
				|| (coincidencia.group(1) != null && (Integer.parseInt(coincidencia.group(1)) < 1
						|| Integer.parseInt(coincidencia.group(1)) > 65535))) {
			throw new IllegalStateException("Configure METRIA_TEST_DATABASE_URL, METRIA_TEST_DATABASE_USERNAME, "
					+ "METRIA_TEST_DATABASE_PASSWORD y METRIA_TEST_DATABASE_DESECHABLE=SI para una base PostgreSQL "
					+ "desechable cuyo nombre termine en _test o _pruebas, sin parámetros en la URL.");
		}
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl(url);
		dataSource.setUsername(usuario);
		dataSource.setPassword(clave);
		return dataSource;
	}
}
