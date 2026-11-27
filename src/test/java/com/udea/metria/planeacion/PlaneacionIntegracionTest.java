package com.udea.metria.planeacion;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.udea.metria.geocodificacion.DireccionGeocodificada;
import com.udea.metria.ConfiguracionBasePruebas;
import com.udea.metria.geocodificacion.GeocodificacionException;
import com.udea.metria.geocodificacion.ServicioGeocodificacion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Integración real de HU01: HTTP -> controlador -> BuscadorLugares ->
 * JdbcTemplate/PostGIS, con las migraciones de Flyway aplicadas sobre una base
 * de PRUEBAS separada.
 *
 * Requisitos del entorno:
 * <ul>
 * <li>{@code METRIA_TEST_DATABASE_URL}, {@code METRIA_TEST_DATABASE_USERNAME}
 * y {@code METRIA_TEST_DATABASE_PASSWORD} para una base PostgreSQL desechable,
 * cuyo nombre termine en {@code _test} o {@code _pruebas}; la URL no admite
 * parámetros ni credenciales.</li>
 * <li>{@code METRIA_TEST_DATABASE_DESECHABLE=SI}. Esta confirmación y el
 * nombre no garantizan aislamiento real: verifique el servidor y la base.</li>
 * <li>{@code MetriaApplicationTests} usa la misma protección y también ejecuta
 * migraciones.</li>
 * <li>Esquema {@code metria} vacío precreado y PostGIS instalado en
 * {@code public}.</li>
 * <li>No se usa Testcontainers ni Docker.</li>
 * </ul>
 *
 * Las fixtures se insertan dentro de la transacción de cada prueba y se
 * revierten al finalizar, por lo que no dejan datos en la base de pruebas.
 */
@SpringBootTest
@Import(ConfiguracionBasePruebas.class)
@AutoConfigureMockMvc
@Transactional
class PlaneacionIntegracionTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RedTransporte red;

	@MockitoBean
	private ServicioGeocodificacion geocodificacion;

	@BeforeEach
	void photonSinResultadosPorDefecto() {
		when(geocodificacion.buscar(anyString())).thenReturn(List.of());
	}

	@Test
	void limitaAccesoPeatonalAOchocientosMetros() {
		double[] cerca = puntoNorteDeNiquia(799);
		double[] lejos = puntoNorteDeNiquia(801);
		assertEquals("metro-22", red.estacionMasCercana(cerca[0], cerca[1]).orElseThrow().id());
		assertTrue(red.estacionMasCercana(lejos[0], lejos[1]).isEmpty());
	}

	private double[] puntoNorteDeNiquia(double distancia) {
		return jdbc.queryForObject("""
				SELECT public.ST_Y(punto) latitud, public.ST_X(punto) longitud FROM (
				  SELECT public.ST_Project(coordenada::public.geography, ?, 0)::public.geometry punto
				  FROM metria.estacion WHERE id = 'metro-22'
				) p
				""", (rs, n) -> new double[] {rs.getDouble("latitud"), rs.getDouble("longitud")}, distancia);
	}

	@Test
	void calculaRutasRealesConTarifasYUneEtiquetas() throws Exception {
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"metro-22"},
				 "destino":{"tipo":"ESTACION","id":"metro-21"}}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"))
				.andExpect(jsonPath("$.rutas.length()").value(1))
				.andExpect(jsonPath("$.rutas[0].etiquetas.length()").value(3))
				.andExpect(jsonPath("$.rutas[0].tarifaAproximada").value(4400))
				.andExpect(jsonPath("$.rutas[0].transbordos").value(0))
				.andExpect(jsonPath("$.rutas[0].pasos[0].accion").value("ABORDAR"))
				.andExpect(jsonPath("$.rutas[0].pasos[0].direccionViaje").isNotEmpty())
				.andExpect(jsonPath("$.rutas[0].pasos[1].accion").value("BAJAR"))
				.andExpect(jsonPath("$.rutas[0].trazados[0].geometria.type").value("LineString"));
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"metro-22"},
				 "destino":{"tipo":"ESTACION","id":"metro-19"}}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"))
				.andExpect(jsonPath("$.rutas[0].tarifaAproximada").value(4400))
				.andExpect(jsonPath("$.rutas[0].transbordos").value(1))
				.andExpect(jsonPath("$.rutas[0].pasos[2].accion").value("TRANSBORDAR"))
				.andExpect(jsonPath("$.rutas[0].pasos[2].direccionViaje").isNotEmpty());
	}

	@Test
	void calculaViajesDeLineaHYTransbordoConTranvia() throws Exception {
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"metro-43"},
				 "destino":{"tipo":"ESTACION","id":"metro-37"}}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"))
				.andExpect(jsonPath("$.rutas[0].tarifaAproximada").value(4400))
				.andExpect(jsonPath("$.rutas[0].duracionMin").value(5))
				.andExpect(jsonPath("$.rutas[0].transbordos").value(0))
				.andExpect(jsonPath("$.rutas[0].trazados[0].linea").value("H"));

		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"metro-53"},
				 "destino":{"tipo":"ESTACION","id":"metro-37"}}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"))
				.andExpect(jsonPath("$.rutas[0].tarifaAproximada").value(4400))
				.andExpect(jsonPath("$.rutas[0].transbordos").value(1));
	}

	@Test
	void calculaArviDirectoYConTransbordoEnAmbosSentidos() throws Exception {
		for (String[] caso : new String[][] {
				{"metro-40", "metro-29", "26700", "ARVI_GENERAL"},
				{"metro-29", "metro-40", "26700", "ARVI_GENERAL"},
				{"metro-22", "metro-29", "31100", "RED_EVENTUAL"},
				{"metro-29", "metro-22", "31100", "ARVI_GENERAL"}}) {
			mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
					{"origen":{"tipo":"ESTACION","id":"%s"},"destino":{"tipo":"ESTACION","id":"%s"}}
					""".formatted(caso[0], caso[1])))
					.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"))
					.andExpect(jsonPath("$.rutas[0].tarifaAproximada").value(Integer.parseInt(caso[2])))
					.andExpect(jsonPath("$.rutas[0].desgloseTarifa[0].tipo").value(caso[3]));
		}
	}

	@Test
	void conservaRutaSinPrecioCuandoCombinaDosCables() throws Exception {
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"metro-30"},"destino":{"tipo":"ESTACION","id":"metro-35"}}
				"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"))
				.andExpect(jsonPath("$.rutas[0].tarifaAproximada").value(nullValue()))
				.andExpect(jsonPath("$.rutas[0].desgloseTarifa").isEmpty())
				.andExpect(jsonPath("$.rutas[0].pasos").isNotEmpty());
	}

	@Test
	void validaOrigenYDestinoFueraDeCobertura() throws Exception {
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"DIRECCION","latitud":0,"longitud":0},
				 "destino":{"tipo":"ESTACION","id":"metro-19"}}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FUERA_DE_COBERTURA"));
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"DIRECCION","latitud":91,"longitud":0},
				 "destino":{"tipo":"ESTACION","id":"metro-19"}}
				""")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"metro-8"},
				 "destino":{"tipo":"ESTACION","id":"metro-28"}}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("MISMA_ESTACION"));
	}

	@Test
	void cargaRedDeRutasYTransbordosVerificados() {
		assertEquals(9L, jdbc.queryForObject("SELECT count(*) FROM metria.linea_ruta", Long.class));
		assertEquals(57L, jdbc.queryForObject("SELECT count(*) FROM metria.linea_estacion", Long.class));
		assertEquals(10L, jdbc.queryForObject("SELECT count(*) FROM metria.transbordo", Long.class));
		assertEquals(5.0, jdbc.queryForObject("SELECT duracion_min FROM metria.linea_ruta WHERE id = 'H'", Double.class));
		assertEquals("metro-43", jdbc.queryForObject("""
				SELECT estacion_id FROM metria.linea_estacion WHERE linea_id = 'H' AND posicion = 1
				""", String.class));
		assertEquals("metro-22", jdbc.queryForObject("""
				SELECT estacion_id FROM metria.linea_estacion WHERE linea_id = 'A' AND posicion = 1
				""", String.class));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT count(*) FROM metria.transbordo
				WHERE estacion_a = 'metro-28' AND estacion_b = 'metro-8'
				""", Long.class));
	}

	@Test
	void rechazaTransbordoConEstacionDesconocida() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
				INSERT INTO metria.transbordo(estacion_a, estacion_b) VALUES ('metro-22', 'metro-inexistente')
				"""));
	}

	@Test
	void cargaTodasLasLineasYConservaRegistrosRepetidos() throws Exception {
		assertEquals(167L, jdbc.queryForObject(
				"SELECT count(*) FROM metria.estacion WHERE id LIKE 'metro-%'", Long.class));
		assertEquals(12L, jdbc.queryForObject(
				"SELECT count(DISTINCT linea) FROM metria.estacion WHERE id LIKE 'metro-%'", Long.class));
		mvc.perform(get("/api/planeacion/lugares").param("texto", "Universidad de Medellín"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lugares.length()").value(4))
				.andExpect(jsonPath("$.lugares[0].linea").value("1"))
				.andExpect(jsonPath("$.lugares[1].linea").value("1"))
				.andExpect(jsonPath("$.lugares[2].linea").value("2"))
				.andExpect(jsonPath("$.lugares[3].linea").value("2"));
	}

	@Test
	void buscaLineasDeDistintosModos() throws Exception {
		for (String texto : List.of("Cisneros", "Santo Domingo", "Alejandro Echavarría", "Barrio Colombia")) {
			mvc.perform(get("/api/planeacion/lugares").param("texto", texto))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.lugares[0].linea").isNotEmpty());
		}
	}

	@Test
	void aplicaMigracionFlywayYCreaTablasConRestricciones() {
		assertEquals(1L, jdbc.queryForObject("""
				SELECT count(*) FROM metria.flyway_schema_history
				WHERE version = '1' AND success
				""", Long.class));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'metria' AND table_type = 'BASE TABLE'
				  AND table_name = 'estacion'
				""", Long.class));
		assertEquals(0L, jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.tables
				WHERE table_schema = 'metria' AND table_name = 'punto_interes'
				""", Long.class));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT count(*) FROM metria.flyway_schema_history
				WHERE version = '4' AND success
				""", Long.class));

		afirmarColumnaGeografica("estacion");
		afirmarRestricciones("estacion");

		assertEquals(0L, jdbc.queryForObject("""
				SELECT count(*) FROM information_schema.columns
				WHERE table_schema = 'metria' AND table_name = 'estacion'
				  AND column_name IN ('id', 'nombre', 'coordenada') AND is_nullable = 'YES'
				""", Long.class));
	}

	@Test
	void retiradaDelCatalogoRechazaUnaTablaConDatos() throws IOException {
		jdbc.execute("CREATE TABLE metria.punto_interes (id text PRIMARY KEY)");
		jdbc.update("INSERT INTO metria.punto_interes (id) VALUES (?)", "dato-existente");
		try (var archivo = getClass().getResourceAsStream("/db/migration/V4__retirar_catalogo_de_puntos.sql")) {
			assertTrue(archivo != null);
			String migracion = new String(archivo.readAllBytes(), StandardCharsets.UTF_8);
			assertThrows(DataAccessException.class, () -> jdbc.execute(migracion));
		}
	}

	@Test
	void buscaEstacionesSinAcentosYDevuelveCoordenadasCorrectas() throws Exception {
		insertarEstacion("qa-est-poblado", "QAEstación Poblado", 6.2105, -75.5700);

		mvc.perform(get("/api/planeacion/lugares").param("texto", "qaestacion"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.lugares.length()").value(1))
				.andExpect(jsonPath("$.lugares[0].id").value("qa-est-poblado"))
				.andExpect(jsonPath("$.lugares[0].nombre").value("QAEstación Poblado"))
				.andExpect(jsonPath("$.lugares[0].tipo").value("ESTACION"))
				.andExpect(jsonPath("$.lugares[0].latitud").value(closeTo(6.2105, 1e-9)))
				.andExpect(jsonPath("$.lugares[0].longitud").value(closeTo(-75.5700, 1e-9)))
				.andExpect(jsonPath("$.direccionesDisponibles").value(true))
				.andExpect(jsonPath("$.alcanceRed").value("RED_CARGADA"));

		// La consulta con acentos y mayúsculas también debe coincidir.
		mvc.perform(get("/api/planeacion/lugares").param("texto", "QAESTACIÓN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lugares.length()").value(1))
				.andExpect(jsonPath("$.lugares[0].id").value("qa-est-poblado"));
	}

	@Test
	void lugarDePhotonSeDevuelveSinEstacionAsignada() throws Exception {
		insertarEstacion("qa-est-cercana", "QAEstación Cercana", 6.0, -75.0);
		insertarEstacion("qa-est-lejana", "QAEstación Lejana", 6.0, -75.01);
		mvc.perform(get("/api/planeacion/lugares").param("texto", "qamuseo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lugares").isEmpty())
				.andExpect(jsonPath("$.direccionesDisponibles").value(true));
		when(geocodificacion.buscar("qamuseo"))
				.thenReturn(List.of(new DireccionGeocodificada("QAMuseo Photon", 6.0, -75.002)));

		mvc.perform(get("/api/planeacion/lugares").param("texto", "qamuseo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lugares.length()").value(1))
				.andExpect(jsonPath("$.lugares[0].id").value(nullValue()))
				.andExpect(jsonPath("$.lugares[0].nombre").value("QAMuseo Photon"))
				.andExpect(jsonPath("$.lugares[0].tipo").value("DIRECCION"))
				.andExpect(jsonPath("$.lugares[0].latitud").value(closeTo(6.0, 1e-9)))
				.andExpect(jsonPath("$.lugares[0].longitud").value(closeTo(-75.002, 1e-9)));
	}

	@Test
	void limitaAOchoSugerenciasYConservaDatosSeleccionables() throws Exception {
		for (int i = 1; i <= 10; i++) {
			insertarEstacion(String.format("qa-est-viaje-%02d", i),
					String.format("QAViaje Estación %02d", i), 6.0 + i * 0.0001, -75.0);
		}
		when(geocodificacion.buscar("qaviaje")).thenReturn(List.of(
				new DireccionGeocodificada("Calle QA Viaje 1", 6.0, -75.0),
				new DireccionGeocodificada("Calle QA Viaje 2", 6.0, -75.0)));

		MvcResult resultado = mvc.perform(get("/api/planeacion/lugares").param("texto", "qaviaje"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.lugares.length()").value(8))
				.andReturn();

		JsonNode lugares = new ObjectMapper()
				.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
				.get("lugares");
		assertEquals(8, lugares.size());

		// Siete estaciones y una sugerencia de Photon.
		for (int i = 0; i < 7; i++) {
			JsonNode estacion = lugares.get(i);
			assertEquals("ESTACION", estacion.get("tipo").asString());
			assertFalse(estacion.get("id").asString().isBlank());
			assertFalse(estacion.get("nombre").asString().isBlank());
			assertTrue(estacion.get("latitud").isNumber());
			assertTrue(estacion.get("longitud").isNumber());
		}
		assertEquals("QAViaje Estación 01", lugares.get(0).get("nombre").asString());
		assertEquals("QAViaje Estación 07", lugares.get(6).get("nombre").asString());

		// La dirección no tiene id local, pero sí coordenadas seleccionables.
		JsonNode direccion = lugares.get(7);
		assertEquals("DIRECCION", direccion.get("tipo").asString());
		assertTrue(direccion.get("id").isNull());
		assertEquals("Calle QA Viaje 1", direccion.get("nombre").asString());
		assertEquals(6.0, direccion.get("latitud").asDouble(), 1e-9);
		assertEquals(-75.0, direccion.get("longitud").asDouble(), 1e-9);
	}

	@Test
	void conservaResultadosLocalesCuandoPhotonFalla() throws Exception {
		insertarEstacion("qa-est-fallo", "QAEstación Poblado", 6.2105, -75.5700);
		when(geocodificacion.buscar("qaestacion"))
				.thenThrow(new GeocodificacionException("Photon no disponible"));

		mvc.perform(get("/api/planeacion/lugares").param("texto", "qaestacion"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.lugares.length()").value(1))
				.andExpect(jsonPath("$.lugares[0].id").value("qa-est-fallo"))
				.andExpect(jsonPath("$.lugares[0].tipo").value("ESTACION"))
				.andExpect(jsonPath("$.direccionesDisponibles").value(false))
				.andExpect(jsonPath("$.alcanceRed").value("RED_CARGADA"));
	}

	@Test
	void distingueFalloDePhotonDeUnaBusquedaSinResultados() throws Exception {
		when(geocodificacion.buscar("qasinesperado"))
				.thenThrow(new GeocodificacionException("Photon no disponible"));

		mvc.perform(get("/api/planeacion/lugares").param("texto", "qasinesperado"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lugares").isEmpty())
				.andExpect(jsonPath("$.direccionesDisponibles").value(false));
	}

	@Test
	void devuelveVacioCuandoNoHayCoincidencias() throws Exception {
		mvc.perform(get("/api/planeacion/lugares").param("texto", "qasinesperado"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.lugares").isEmpty())
				.andExpect(jsonPath("$.direccionesDisponibles").value(true))
				.andExpect(jsonPath("$.alcanceRed").value("RED_CARGADA"));
	}

	@Test
	void rechazaTextosInvalidos() throws Exception {
		mvc.perform(get("/api/planeacion/lugares"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(get("/api/planeacion/lugares").param("texto", "   "))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/planeacion/lugares").param("texto", "ab"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/planeacion/lugares").param("texto", "a".repeat(201)))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/planeacion/lugares").param("texto", "abc\n"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rechazaLongitudFueraDeRango() {
		assertThrows(DataIntegrityViolationException.class,
				() -> insertarEstacion("qa-invalida-lon", "QAInválida", 6.0, 181.0));
	}

	@Test
	void rechazaLatitudFueraDeRango() {
		assertThrows(DataIntegrityViolationException.class,
				() -> insertarEstacion("qa-invalida-lat", "QAInválida", 91.0, -75.0));
	}

	@Test
	void rechazaGeometriaVacia() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
				INSERT INTO metria.estacion (id, nombre, coordenada)
				VALUES (?, ?, public.ST_GeomFromText('POINT EMPTY', 4326))
				""", "qa-vacia", "QAVacía"));
	}

	@Test
	void rechazaIdentificadorDuplicado() {
		insertarEstacion("qa-duplicada", "QADuplicada", 6.0, -75.0);
		assertThrows(DataIntegrityViolationException.class,
				() -> insertarEstacion("qa-duplicada", "QADuplicada 2", 6.0, -75.0));
	}

	@Test
	void rechazaNombreNulo() {
		assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
				INSERT INTO metria.estacion (id, nombre, coordenada)
				VALUES (?, NULL, public.ST_SetSRID(public.ST_MakePoint(?, ?), 4326))
				""", "qa-sin-nombre", -75.0, 6.0));
	}

	private void afirmarColumnaGeografica(String tabla) {
		assertEquals("POINT", jdbc.queryForObject("""
				SELECT type FROM public.geometry_columns
				WHERE f_table_schema = 'metria' AND f_table_name = ?
				  AND f_geometry_column = 'coordenada'
				""", String.class, tabla));
		assertEquals(4326, jdbc.queryForObject("""
				SELECT srid FROM public.geometry_columns
				WHERE f_table_schema = 'metria' AND f_table_name = ?
				  AND f_geometry_column = 'coordenada'
				""", Integer.class, tabla));
		assertEquals(1L, jdbc.queryForObject("""
				SELECT count(*) FROM pg_constraint
				WHERE conrelid = ('metria.' || ?)::regclass AND contype = 'p'
				""", Long.class, tabla));
	}

	private void afirmarRestricciones(String tabla) {
		List<String> restricciones = jdbc.queryForList("""
				SELECT lower(pg_get_constraintdef(oid)) FROM pg_constraint
				WHERE conrelid = ('metria.' || ?)::regclass AND contype = 'c'
				""", String.class, tabla);
		assertTrue(restricciones.stream().anyMatch(d -> d.contains("st_isempty")),
				"Falta la restricción de geometría vacía en " + tabla);
		assertTrue(restricciones.stream().anyMatch(d -> d.contains("st_x")),
				"Falta la restricción de longitud en " + tabla);
		assertTrue(restricciones.stream().anyMatch(d -> d.contains("st_y")),
				"Falta la restricción de latitud en " + tabla);
	}

	private void insertarEstacion(String id, String nombre, double latitud, double longitud) {
		jdbc.update("""
				INSERT INTO metria.estacion (id, nombre, coordenada)
				VALUES (?, ?, public.ST_SetSRID(public.ST_MakePoint(?, ?), 4326))
				""", id, nombre, longitud, latitud);
	}

}
