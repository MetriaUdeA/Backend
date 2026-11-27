package com.udea.metria.migration;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.util.zip.CRC32;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Incorpora Oriente, Las Torres y Villa Sierra a la red del planeador. */
public class V7__agregar_linea_h extends BaseJavaMigration {
	private static final String RECURSO = "/datos/Lineas_Sistema_Metro.geojson";

	private byte[] datos() throws Exception {
		try (InputStream entrada = getClass().getResourceAsStream(RECURSO)) {
			if (entrada == null) throw new IllegalStateException("No se encontró el GeoJSON de líneas");
			return entrada.readAllBytes();
		}
	}

	@Override
	public Integer getChecksum() {
		try {
			CRC32 crc = new CRC32();
			crc.update(datos());
			return (int) crc.getValue();
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo verificar el GeoJSON", e);
		}
	}

	@Override
	public void migrate(Context context) throws Exception {
		JsonNode features = new ObjectMapper().readTree(datos()).path("features");
		if (!features.isArray()) throw new IllegalStateException("GeoJSON inválido");
		JsonNode trazado = null;
		for (JsonNode feature : features) {
			if (!"H".equals(feature.path("properties").path("linea").asString(""))) continue;
			if (trazado != null || !"LineString".equals(feature.path("geometry").path("type").asString())) {
				throw new IllegalStateException("Trazado de línea H duplicado o inválido");
			}
			trazado = feature.path("geometry");
		}
		if (trazado == null) throw new IllegalStateException("Falta el trazado de la línea H");

		try (PreparedStatement linea = context.getConnection().prepareStatement("""
				INSERT INTO metria.linea_ruta(id, duracion_min, trazado)
				VALUES ('H', 5, public.ST_Force2D(public.ST_SetSRID(public.ST_GeomFromGeoJSON(?), 4326)))
				""")) {
			linea.setString(1, trazado.toString());
			linea.executeUpdate();
		}

		try (PreparedStatement estacion = context.getConnection().prepareStatement("""
				INSERT INTO metria.linea_estacion(linea_id, estacion_id, posicion)
				SELECT 'H', id, ? FROM metria.estacion WHERE id = ? AND linea = 'H'
				""")) {
			int orden = 0;
			for (int id : new int[] {43, 39, 37}) {
				estacion.setInt(1, ++orden);
				estacion.setString(2, "metro-" + id);
				if (estacion.executeUpdate() != 1) {
					throw new IllegalStateException("Estación de línea H no encontrada: " + id);
				}
			}
		}

		try (PreparedStatement transbordo = context.getConnection().prepareStatement("""
				INSERT INTO metria.transbordo(estacion_a, estacion_b)
				VALUES (LEAST('metro-43', 'metro-48'), GREATEST('metro-43', 'metro-48'))
				""")) {
			transbordo.executeUpdate();
		}
	}
}
