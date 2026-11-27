package com.udea.metria.migration;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.util.zip.CRC32;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Carga la copia del GeoJSON de Open Data Metro de Medellín sin fusionar registros.
 * El checksum incluye el archivo para detectar cambios posteriores a la carga.
 */
public class V3__cargar_estaciones_metro extends BaseJavaMigration {
	private static final String RECURSO = "/datos/Estaciones_Sistema_Metro.geojson";

	private static byte[] datos() {
		try (InputStream entrada = V3__cargar_estaciones_metro.class.getResourceAsStream(RECURSO)) {
			if (entrada == null) throw new IllegalStateException("No se encontró el GeoJSON de estaciones");
			return entrada.readAllBytes();
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo leer el GeoJSON de estaciones", e);
		}
	}

	@Override
	public Integer getChecksum() {
		CRC32 checksum = new CRC32();
		checksum.update(datos());
		return (int) checksum.getValue();
	}

	@Override
	public void migrate(Context context) throws Exception {
		JsonNode features = new ObjectMapper().readTree(datos()).path("features");
		if (!features.isArray() || features.isEmpty()) {
			throw new IllegalStateException("El GeoJSON no contiene estaciones");
		}
		String sql = """
				INSERT INTO metria.estacion
				    (id, nombre, coordenada, linea, sentido, sistema, tipo_fuente, estacion_fuente)
				VALUES (?, ?, public.ST_SetSRID(public.ST_MakePoint(?, ?), 4326), ?, ?, ?, ?, ?)
				""";
		try (PreparedStatement sentencia = context.getConnection().prepareStatement(sql)) {
			for (JsonNode feature : features) {
				JsonNode p = feature.path("properties");
				JsonNode coordenadas = feature.path("geometry").path("coordinates");
				if (!p.path("objectid").isIntegralNumber() || p.path("label").asString("").isBlank()
						|| p.path("linea").asString("").isBlank()
						|| !"Point".equals(feature.path("geometry").path("type").asString())
						|| !coordenadas.isArray() || coordenadas.size() < 2
						|| !coordenadas.get(0).isNumber() || !coordenadas.get(1).isNumber()) {
					throw new IllegalStateException("Registro inválido en el GeoJSON de estaciones");
				}
				sentencia.setString(1, "metro-" + p.path("objectid").asInt());
				sentencia.setString(2, p.path("label").asString());
				sentencia.setDouble(3, coordenadas.get(0).asDouble());
				sentencia.setDouble(4, coordenadas.get(1).asDouble());
				sentencia.setString(5, p.path("linea").asString());
				sentencia.setString(6, p.path("sentido").asString(null));
				sentencia.setString(7, p.path("sistema").asString(null));
				if (p.path("tipo").isIntegralNumber()) sentencia.setInt(8, p.path("tipo").asInt());
				else sentencia.setNull(8, java.sql.Types.INTEGER);
				sentencia.setString(9, p.path("estacion").asString(null));
				sentencia.addBatch();
			}
			sentencia.executeBatch();
		}
	}
}
