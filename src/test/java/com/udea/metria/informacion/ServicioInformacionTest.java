package com.udea.metria.informacion;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.udea.metria.planeacion.Idioma;

class ServicioInformacionTest {
	private final ServicioInformacion servicio = new ServicioInformacion();

	@Test
	void tarifasIncluyenPerfilesArviYFuente() {
		var tarifas = servicio.consultarTarifas(Idioma.ES);
		assertTrue(tarifas.fuente().startsWith("https://www.metrodemedellin.gov.co/"));
		assertEquals("2026-11-26", tarifas.fechaRevision());
		assertTrue(tarifas.entradas().stream().anyMatch(e -> e.titulo().contains("Al portador")
				&& e.valor() == 4400 && "COP".equals(e.moneda())));
		assertTrue(tarifas.entradas().stream().anyMatch(e -> e.titulo().contains("Arví") && e.valor() == 26700));
	}

	@Test
	void horariosConservanDomingosFestivosYExcepcionDeMartes() {
		var horarios = servicio.consultarHorarios(Idioma.ES);
		assertTrue(horarios.entradas().stream().anyMatch(e -> e.titulo().contains("Domingos y festivos")
				&& e.titulo().contains("K") && e.detalle().contains("08:30")));
		assertTrue(horarios.entradas().stream().anyMatch(e -> e.titulo().contains("Arví")
				&& e.detalle().contains("martes")));
	}

	@Test
	void normasSeTraducenSinInventarTextoCuandoNoHayConsecuencia() {
		var es = servicio.consultarNormas(Idioma.ES);
		var en = servicio.consultarNormas(Idioma.EN);
		assertFalse(es.entradas().isEmpty());
		assertNotEquals(es.entradas().getFirst().titulo(), en.entradas().getFirst().titulo());
		assertTrue(en.entradas().getFirst().consecuencia().contains("clause 8"));
		assertEquals("English", new TextoBilingue("Español", "English").obtener(Idioma.EN));
	}
}
