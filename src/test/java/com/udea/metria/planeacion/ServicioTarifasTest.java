package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ServicioTarifasTest {
	private final ServicioTarifas servicio = new ServicioTarifas();

	@Test
	void calculaPorParejasDeTramos() {
		assertEquals(new BigDecimal("4400"), servicio.calcular(List.of("A")));
		assertEquals(new BigDecimal("4400"), servicio.calcular(List.of("A", "B")));
		assertEquals(new BigDecimal("4400"), servicio.calcular(List.of("A", "B", "T", "H")));
		assertEquals(new BigDecimal("26700"), servicio.calcular(List.of("L")));
		assertNull(servicio.calcular(List.of("A", "B", "C")));
	}

	@Test
	void agrupaLineasConsecutivasPeroCuentaLasRetomadas() {
		assertEquals(new BigDecimal("4400"), servicio.calcular(List.of("A", "A", "B")));
		assertEquals(new BigDecimal("4400"), servicio.calcular(List.of("A", "B", "A")));
	}

	@Test
	void desglosaArviEnAmbosSentidosYRechazaCombinacionNoVerificada() {
		assertEquals(new BigDecimal("31100"), servicio.calcular(List.of("A", "K", "L")));
		assertEquals(new BigDecimal("31100"), servicio.calcular(List.of("L", "K", "A")));
		assertEquals(List.of("RED_EVENTUAL", "ARVI_GENERAL"), servicio.estimar(List.of("K", "L"))
				.desglose().stream().map(ServicioTarifas.Cobro::tipo).toList());
		assertNull(servicio.calcular(List.of("K", "A", "J")));
	}

	@Test
	void rechazaRutasSinLineasValidas() {
		assertThrows(IllegalArgumentException.class, () -> servicio.calcular(null));
		assertThrows(IllegalArgumentException.class, () -> servicio.calcular(List.of()));
		assertThrows(IllegalArgumentException.class, () -> servicio.calcular(List.of("A", " ")));
		assertThrows(IllegalArgumentException.class, () -> servicio.calcular(Arrays.asList("A", null)));
	}
}
