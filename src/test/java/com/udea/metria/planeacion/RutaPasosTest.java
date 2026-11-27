package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RutaPasosTest {
	private final List<RedTransporte.EstacionRuta> estaciones = List.of(
			new RedTransporte.EstacionRuta("A", "a", "Norte", 1),
			new RedTransporte.EstacionRuta("A", "b", "Centro", 2),
			new RedTransporte.EstacionRuta("A", "c", "Sur", 3),
			new RedTransporte.EstacionRuta("B", "d", "Centro B", 1),
			new RedTransporte.EstacionRuta("B", "e", "Occidente", 2));

	@Test
	void generaPasosOrdenadosConTransbordoYTerminalEnSentidoCorrecto() {
		Ruta ruta = new Ruta(List.of(
				new SegmentoRuta("A", "c", "b", 2),
				new SegmentoRuta("B", "d", "e", 2)), BigDecimal.ZERO);
		var pasos = ruta.generarPasos(estaciones, Idioma.ES);
		assertEquals(List.of(AccionPaso.ABORDAR, AccionPaso.BAJAR, AccionPaso.TRANSBORDAR, AccionPaso.BAJAR),
				pasos.stream().map(PasoViaje::accion).toList());
		assertEquals("Norte", pasos.get(0).direccionViaje());
		assertEquals("Centro", pasos.get(2).estacionAnterior());
		assertEquals("Centro B", pasos.get(2).estacion());
		assertEquals("Occidente", pasos.get(2).direccionViaje());
		assertEquals(1, pasos.get(2).paradas());
		assertTrue(pasos.get(2).descripcion().contains("línea B"));
	}

	@Test
	void agrupaTramosYTraduceSinTraducirNombres() {
		Ruta ruta = new Ruta(List.of(new SegmentoRuta("A", "a", "b", 1),
				new SegmentoRuta("A", "b", "c", 1)), BigDecimal.ZERO);
		var pasos = ruta.generarPasos(estaciones, Idioma.EN);
		assertEquals(2, pasos.size());
		assertEquals(2, pasos.get(0).paradas());
		assertEquals("Sur", pasos.get(0).direccionViaje());
		assertTrue(pasos.get(0).descripcion().contains("Board at Norte"));
		assertTrue(pasos.get(1).descripcion().contains("Get off at Sur"));
	}

	@Test
	void caminataExplicitaAproximacionYDireccion() {
		var inicio = PasoViaje.caminar("Norte", 42.2, true, Idioma.ES);
		var fin = PasoViaje.caminar("Sur", 10, false, Idioma.EN);
		assertEquals(43, inicio.distanciaMetros());
		assertTrue(inicio.descripcion().contains("línea recta"));
		assertTrue(fin.descripcion().contains("from Sur to the destination"));
		assertNull(inicio.linea());
	}

	@Test
	void rechazaEstacionesDesconocidasEnLugarDeInventarDireccion() {
		Ruta ruta = new Ruta(List.of(new SegmentoRuta("A", "a", "desconocida", 1)), BigDecimal.ZERO);
		assertThrows(NullPointerException.class, () -> ruta.generarPasos(estaciones, Idioma.ES));
	}
}
