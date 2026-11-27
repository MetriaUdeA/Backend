package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaneadorRutasTest {
	private final RedTransporte red = mock(RedTransporte.class);
	private final PlaneadorRutas planeador = new PlaneadorRutas(red, new ServicioTarifas(),
			List.of(new RutaMasEconomica(), new RutaMasRapida(), new RutaMenosTransbordos()));
	private static final String GEOMETRIA = """
			{"type":"LineString","coordinates":[[-75.5,6.2],[-75.4,6.3]]}
			""";

	@Test
	void seleccionaDistintosCriteriosSinDuplicarLaRuta() {
		when(red.estacionAcceso("a")).thenReturn(Optional.of(acceso("a")));
		when(red.estacionAcceso("d")).thenReturn(Optional.of(acceso("d")));
		when(red.conexiones()).thenReturn(List.of(
				new RedTransporte.Conexion("A", "a", "d", 20, GEOMETRIA),
				new RedTransporte.Conexion("A", "a", "b", 1, GEOMETRIA),
				new RedTransporte.Conexion("B", "c", "d", 1, GEOMETRIA)));
		when(red.transbordos()).thenReturn(List.of(new RedTransporte.Transbordo("b", "c")));
		when(red.estacionesRuta()).thenReturn(List.of(
				new RedTransporte.EstacionRuta("A", "a", "A", 1),
				new RedTransporte.EstacionRuta("A", "b", "B", 2),
				new RedTransporte.EstacionRuta("A", "d", "D", 3),
				new RedTransporte.EstacionRuta("B", "c", "C", 1),
				new RedTransporte.EstacionRuta("B", "d-b", "D", 2)));
		var resultado = planeador.planear(new PlaneadorRutas.Solicitud(estacion("a"), estacion("d")));
		assertEquals("OK", resultado.estado());
		assertEquals(2, resultado.rutas().size());
		assertEquals(List.of("MAS_ECONOMICA", "MAS_RAPIDA"), resultado.rutas().get(0).etiquetas());
		assertEquals(List.of("MENOS_TRANSBORDOS"), resultado.rutas().get(1).etiquetas());
		assertEquals(6, resultado.rutas().get(0).duracionMin());
		assertEquals(4400, resultado.rutas().get(0).tarifaAproximada().intValueExact());
		assertEquals(List.of(AccionPaso.ABORDAR, AccionPaso.BAJAR, AccionPaso.TRANSBORDAR, AccionPaso.BAJAR),
				resultado.rutas().get(0).pasos().stream().map(PasoViaje::accion).toList());
	}

	@Test
	void incluyeCaminatasEnOrdenYEnElIdiomaSolicitado() {
		when(red.estacionMasCercana(6.2, -75.5)).thenReturn(Optional.of(
				new RedTransporte.Acceso("a", "Norte", "A", 6.2, -75.5, 80)));
		when(red.estacionMasCercana(6.3, -75.6)).thenReturn(Optional.of(
				new RedTransporte.Acceso("b", "Sur", "A", 6.3, -75.6, 30)));
		when(red.conexiones()).thenReturn(List.of(new RedTransporte.Conexion("A", "a", "b", 1, GEOMETRIA)));
		when(red.estacionesRuta()).thenReturn(List.of(
				new RedTransporte.EstacionRuta("A", "a", "Norte", 1),
				new RedTransporte.EstacionRuta("A", "b", "Sur", 2)));
		var origen = new PlaneadorRutas.LugarEntrada("DIRECCION", null, 6.2, -75.5);
		var destino = new PlaneadorRutas.LugarEntrada("DIRECCION", null, 6.3, -75.6);
		var pasos = planeador.planear(new PlaneadorRutas.Solicitud(origen, destino, Idioma.EN)).rutas().get(0).pasos();
		assertEquals(List.of(AccionPaso.CAMINAR, AccionPaso.ABORDAR, AccionPaso.BAJAR, AccionPaso.CAMINAR),
				pasos.stream().map(PasoViaje::accion).toList());
		assertTrue(pasos.get(0).descripcion().contains("straight line"));
		assertTrue(pasos.get(3).descripcion().contains("destination"));
	}

	@Test
	void criterioEconomicoUsaCostoRealAunqueSeaMasLenta() {
		when(red.estacionAcceso("a")).thenReturn(Optional.of(acceso("a")));
		when(red.estacionAcceso("d")).thenReturn(Optional.of(acceso("d")));
		when(red.conexiones()).thenReturn(List.of(
				new RedTransporte.Conexion("A", "a", "d", 20, GEOMETRIA),
				new RedTransporte.Conexion("L", "a", "b", 1, GEOMETRIA),
				new RedTransporte.Conexion("B", "c", "d", 1, GEOMETRIA)));
		when(red.transbordos()).thenReturn(List.of(new RedTransporte.Transbordo("b", "c")));
		when(red.estacionesRuta()).thenReturn(List.of(
				new RedTransporte.EstacionRuta("A", "a", "A", 1),
				new RedTransporte.EstacionRuta("A", "d", "D", 2),
				new RedTransporte.EstacionRuta("L", "b", "B", 2),
				new RedTransporte.EstacionRuta("B", "c", "C", 1),
				new RedTransporte.EstacionRuta("B", "d-b", "D", 2)));
		var rutas = planeador.planear(new PlaneadorRutas.Solicitud(estacion("a"), estacion("d"))).rutas();
		var economica = rutas.stream().filter(r -> r.etiquetas().contains("MAS_ECONOMICA")).findFirst().orElseThrow();
		var rapida = rutas.stream().filter(r -> r.etiquetas().contains("MAS_RAPIDA")).findFirst().orElseThrow();
		assertEquals(4400, economica.tarifaAproximada().intValueExact());
		assertEquals(31100, rapida.tarifaAproximada().intValueExact());
	}

	@Test
	void conservaLaRutaRapidaSinTarifaYComparaSoloLaCalculable() {
		when(red.estacionAcceso("a")).thenReturn(Optional.of(acceso("a")));
		when(red.estacionAcceso("f")).thenReturn(Optional.of(acceso("f")));
		when(red.conexiones()).thenReturn(List.of(
				new RedTransporte.Conexion("A", "a", "f", 20, GEOMETRIA),
				new RedTransporte.Conexion("K", "a", "b", 1, GEOMETRIA),
				new RedTransporte.Conexion("A", "c", "d", 1, GEOMETRIA),
				new RedTransporte.Conexion("J", "e", "f", 1, GEOMETRIA)));
		when(red.transbordos()).thenReturn(List.of(new RedTransporte.Transbordo("b", "c"),
				new RedTransporte.Transbordo("d", "e")));
		when(red.estacionesRuta()).thenReturn(List.of(
				new RedTransporte.EstacionRuta("A", "a", "A", 1),
				new RedTransporte.EstacionRuta("K", "b", "B", 2),
				new RedTransporte.EstacionRuta("A", "c", "C", 2),
				new RedTransporte.EstacionRuta("A", "d", "D", 3),
				new RedTransporte.EstacionRuta("J", "e", "E", 1),
				new RedTransporte.EstacionRuta("A", "f", "F", 4),
				new RedTransporte.EstacionRuta("J", "j-fin", "F", 2)));
		var rutas = planeador.planear(new PlaneadorRutas.Solicitud(estacion("a"), estacion("f"))).rutas();
		var economica = rutas.stream().filter(r -> r.etiquetas().contains("MAS_ECONOMICA")).findFirst().orElseThrow();
		var rapida = rutas.stream().filter(r -> r.etiquetas().contains("MAS_RAPIDA")).findFirst().orElseThrow();
		assertEquals(4400, economica.tarifaAproximada().intValueExact());
		assertNull(rapida.tarifaAproximada());
		assertFalse(rapida.pasos().isEmpty());
		assertTrue(rapida.desgloseTarifa().isEmpty());
	}

	@Test
	void distingueFaltaDeConexionYCoordenadasInvalidas() {
		when(red.estacionAcceso("a")).thenReturn(Optional.of(acceso("a")));
		when(red.estacionAcceso("d")).thenReturn(Optional.of(acceso("d")));
		when(red.conexiones()).thenReturn(List.of());
		when(red.transbordos()).thenReturn(List.of());
		assertEquals("SIN_RUTA", planeador.planear(new PlaneadorRutas.Solicitud(estacion("a"), estacion("d"))).estado());
		assertThrows(IllegalArgumentException.class, () -> planeador.planear(new PlaneadorRutas.Solicitud(
				new PlaneadorRutas.LugarEntrada("DIRECCION", null, 91.0, 0.0), estacion("d"))));
	}

	private static RedTransporte.Acceso acceso(String id) {
		return new RedTransporte.Acceso(id, id, "A", 6.2, -75.5, 0);
	}
	private static PlaneadorRutas.LugarEntrada estacion(String id) {
		return new PlaneadorRutas.LugarEntrada("ESTACION", id, null, null);
	}
}
