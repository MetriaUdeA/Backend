package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CriterioRutaTest {
	private final ServicioTarifas tarifas = new ServicioTarifas();

	private Ruta ruta(SegmentoRuta... segmentos) {
		List<SegmentoRuta> lista = List.of(segmentos);
		return new Ruta(lista, tarifas.calcular(lista.stream().map(SegmentoRuta::linea).toList()));
	}

	@Test
	void calculaTarifaDuracionYTransbordos() {
		Ruta ruta = ruta(new SegmentoRuta("A", "1", "2", 5),
				new SegmentoRuta("B", "3", "4", 6));
		assertEquals(1, ruta.contarTransbordos());
		assertEquals(15, ruta.calcularDuracionTotal());
		assertEquals("4400", ruta.tarifaAproximada().toPlainString());
	}

	@Test
	void criteriosComparanYDesempatan() {
		Ruta lenta = ruta(new SegmentoRuta("A", "1", "2", 20));
		Ruta rapida = ruta(new SegmentoRuta("B", "3", "4", 10));
		Ruta transbordo = ruta(new SegmentoRuta("A", "1", "2", 1),
				new SegmentoRuta("B", "3", "4", 1));
		assertTrue(new RutaMasEconomica().comparador().compare(rapida, lenta) < 0);
		assertTrue(new RutaMasRapida().comparador().compare(transbordo, rapida) < 0);
		assertTrue(new RutaMenosTransbordos().comparador().compare(rapida, transbordo) < 0);
	}

	@Test
	void rechazaSegmentosInvalidosYDiscontinuos() {
		assertThrows(IllegalArgumentException.class, () -> new SegmentoRuta("A", "1", "1", 1));
		assertThrows(IllegalArgumentException.class, () -> new SegmentoRuta("A", "1", "2", Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> ruta(new SegmentoRuta("A", "1", "2", 1),
				new SegmentoRuta("A", "3", "4", 1)));
	}
}
