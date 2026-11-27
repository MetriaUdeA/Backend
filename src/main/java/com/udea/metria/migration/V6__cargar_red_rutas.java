package com.udea.metria.migration;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.zip.CRC32;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Datos verificados y conexiones explícitas; no se infieren por proximidad. */
public class V6__cargar_red_rutas extends BaseJavaMigration {
	private static final String RECURSO = "/datos/Lineas_Sistema_Metro.geojson";
	private static final Map<String, Double> DURACIONES = Map.of(
			"A", 42.0, "B", 10.5, "J", 12.0, "K", 9.0,
			"L", 15.0, "M", 4.0, "P", 10.0, "T", 19.0);
	private static final Map<String, int[]> ESTACIONES = Map.of(
			"A", new int[] {22,21,20,1,2,3,4,5,6,7,8,10,11,12,13,14,23,24,25,26,27},
			"B", new int[] {28,9,15,16,17,18,19},
			"J", new int[] {44,33,34,35}, "K", new int[] {41,32,31,30},
			"L", new int[] {40,29}, "M", new int[] {42,38,36},
			"P", new int[] {59,167,57,58}, "T", new int[] {53,50,49,46,51,52,47,45,48});
	private static final int[][] TRANSBORDOS = {
			{8,28}, {8,53}, {28,53}, {1,41}, {1,59}, {41,59},
			{19,44}, {30,40}, {42,52}
	};

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
		int cargadas = 0;
		try (PreparedStatement linea = context.getConnection().prepareStatement("""
				INSERT INTO metria.linea_ruta(id, duracion_min, trazado)
				VALUES (?, ?, public.ST_Force2D(public.ST_SetSRID(public.ST_GeomFromGeoJSON(?), 4326)))
				""")) {
			for (JsonNode feature : features) {
				String id = feature.path("properties").path("linea").asString("");
				if (!DURACIONES.containsKey(id)) continue;
				if (!"LineString".equals(feature.path("geometry").path("type").asString())) {
					throw new IllegalStateException("Trazado inválido: " + id);
				}
				linea.setString(1, id);
				linea.setDouble(2, DURACIONES.get(id));
				linea.setString(3, feature.path("geometry").toString());
				linea.executeUpdate();
				cargadas++;
			}
		}
		if (cargadas != DURACIONES.size()) throw new IllegalStateException("Faltan trazados verificados");
		try (PreparedStatement estacion = context.getConnection().prepareStatement("""
				INSERT INTO metria.linea_estacion(linea_id, estacion_id, posicion)
				SELECT ?, id, ? FROM metria.estacion WHERE id = ? AND linea = ?
				""")) {
			for (var entrada : ESTACIONES.entrySet()) {
				int orden = 0;
				for (int id : entrada.getValue()) {
					estacion.setString(1, entrada.getKey());
					estacion.setInt(2, ++orden);
					estacion.setString(3, "metro-" + id);
					estacion.setString(4, entrada.getKey());
					if (estacion.executeUpdate() != 1) throw new IllegalStateException("Estación no encontrada: " + id);
				}
			}
		}
		try (PreparedStatement transbordo = context.getConnection().prepareStatement("""
				INSERT INTO metria.transbordo(estacion_a, estacion_b)
				SELECT LEAST(?, ?), GREATEST(?, ?)
				""")) {
			for (int[] par : TRANSBORDOS) {
				String a = "metro-" + par[0], b = "metro-" + par[1];
				transbordo.setString(1, a);
				transbordo.setString(2, b);
				transbordo.setString(3, a);
				transbordo.setString(4, b);
				transbordo.executeUpdate();
			}
		}
	}
}
