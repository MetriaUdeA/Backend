package com.udea.metria.planeacion;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedTransporte {

	// PostgreSQL requiere codificación UTF8 para normalize(..., NFC).
	private static final String FILTRO = """
			position(lower(translate(normalize(?, NFC), 'ÁÉÍÓÚÜáéíóúü', 'AEIOUUaeiouu'))
			    in lower(translate(normalize(nombre, NFC), 'ÁÉÍÓÚÜáéíóúü', 'AEIOUUaeiouu'))) > 0
			""";

	private final JdbcTemplate jdbc;

	public RedTransporte(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<EstacionLocal> buscarEstaciones(String texto, int limite) {
		texto = validar(texto, limite);
		return jdbc.query("""
				SELECT id, nombre, linea, public.ST_Y(coordenada) AS latitud, public.ST_X(coordenada) AS longitud
				FROM metria.estacion
				WHERE
				""" + FILTRO + """
				ORDER BY nombre, id
				LIMIT ?
				""", (rs, numero) -> new EstacionLocal(rs.getString("id"), rs.getString("nombre"),
				rs.getDouble("latitud"), rs.getDouble("longitud"), rs.getString("linea")), texto, limite);
	}

	public Optional<Acceso> estacionAcceso(String id) {
		return jdbc.query("""
				SELECT e.id, e.nombre, e.linea, public.ST_Y(e.coordenada) latitud,
				       public.ST_X(e.coordenada) longitud, 0.0 distancia
				FROM metria.estacion e JOIN metria.linea_estacion le ON le.estacion_id = e.id
				WHERE e.id = ?
				""", (rs, n) -> new Acceso(rs.getString("id"), rs.getString("nombre"),
				rs.getString("linea"), rs.getDouble("latitud"), rs.getDouble("longitud"),
				rs.getDouble("distancia")), id).stream().findFirst();
	}

	public Optional<Acceso> estacionMasCercana(double latitud, double longitud) {
		return jdbc.query("""
				WITH punto AS (SELECT public.ST_SetSRID(public.ST_MakePoint(?, ?), 4326)::public.geography g)
				SELECT e.id, e.nombre, e.linea, public.ST_Y(e.coordenada) latitud,
				       public.ST_X(e.coordenada) longitud,
				       public.ST_Distance(e.coordenada::public.geography, punto.g) distancia
				FROM metria.estacion e JOIN metria.linea_estacion le ON le.estacion_id = e.id
				CROSS JOIN punto
				WHERE public.ST_DWithin(e.coordenada::public.geography, punto.g, 800)
				ORDER BY distancia, e.id LIMIT 1
				""", (rs, n) -> new Acceso(rs.getString("id"), rs.getString("nombre"),
				rs.getString("linea"), rs.getDouble("latitud"), rs.getDouble("longitud"),
				rs.getDouble("distancia")), longitud, latitud).stream().findFirst();
	}

	public List<Conexion> conexiones() {
		return jdbc.query("""
				WITH puntos AS (
				  SELECT le.linea_id, le.estacion_id, le.posicion, l.duracion_min, l.trazado,
				    public.ST_LineLocatePoint(l.trazado, e.coordenada) fraccion
				  FROM metria.linea_estacion le
				  JOIN metria.linea_ruta l ON l.id = le.linea_id
				  JOIN metria.estacion e ON e.id = le.estacion_id
				), tramos AS (
				  SELECT a.linea_id, a.estacion_id inicio, b.estacion_id fin,
				    a.duracion_min * abs(b.fraccion - a.fraccion) duracion,
				    CASE WHEN a.fraccion < b.fraccion
				      THEN public.ST_LineSubstring(a.trazado, a.fraccion, b.fraccion)
				      ELSE public.ST_Reverse(public.ST_LineSubstring(a.trazado, b.fraccion, a.fraccion)) END geom
				  FROM puntos a JOIN puntos b ON a.linea_id = b.linea_id AND b.posicion = a.posicion + 1
				)
				SELECT linea_id, inicio, fin, duracion, public.ST_AsGeoJSON(geom) geometria FROM tramos
				ORDER BY linea_id, inicio
				""", (rs, n) -> new Conexion(rs.getString("linea_id"), rs.getString("inicio"),
				rs.getString("fin"), rs.getDouble("duracion"), rs.getString("geometria")));
	}

	public List<Transbordo> transbordos() {
		return jdbc.query("SELECT estacion_a, estacion_b FROM metria.transbordo ORDER BY estacion_a, estacion_b",
				(rs, n) -> new Transbordo(rs.getString(1), rs.getString(2)));
	}

	public List<EstacionRuta> estacionesRuta() {
		return jdbc.query("""
				SELECT le.linea_id, le.estacion_id, e.nombre, le.posicion
				FROM metria.linea_estacion le JOIN metria.estacion e ON e.id = le.estacion_id
				ORDER BY le.linea_id, le.posicion
				""", (rs, n) -> new EstacionRuta(rs.getString(1), rs.getString(2),
				rs.getString(3), rs.getInt(4)));
	}

	public record Acceso(String id, String nombre, String linea, double latitud, double longitud, double distancia) {}
	public record Conexion(String linea, String inicio, String fin, double duracion, String geometria) {}
	public record Transbordo(String a, String b) {}
	public record EstacionRuta(String linea, String id, String nombre, int posicion) {}

	private static String validar(String texto, int limite) {
		if (texto == null) {
			throw new IllegalArgumentException("El texto debe tener al menos tres caracteres");
		}
		String normalizado = Normalizer.normalize(texto.strip(), Normalizer.Form.NFC);
		if (normalizado.codePointCount(0, normalizado.length()) < 3) {
			throw new IllegalArgumentException("El texto debe tener al menos tres caracteres");
		}
		if (limite < 1 || limite > 8) {
			throw new IllegalArgumentException("El límite debe estar entre 1 y 8");
		}
		return normalizado;
	}
}
