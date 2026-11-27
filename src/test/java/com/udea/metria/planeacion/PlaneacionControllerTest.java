package com.udea.metria.planeacion;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlaneacionControllerTest {
	private final BuscadorLugares buscador = mock(BuscadorLugares.class);
	private final PlaneadorRutas planeador = mock(PlaneadorRutas.class);
	private final org.springframework.test.web.servlet.MockMvc mvc =
			MockMvcBuilders.standaloneSetup(new PlaneacionController(buscador, planeador)).build();

	@Test
	void validaParametros() throws Exception {
		mvc.perform(get("/api/planeacion/lugares")).andExpect(status().isBadRequest())
				.andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(get("/api/planeacion/lugares").param("texto", " ab ")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/planeacion/lugares").param("texto", "a".repeat(201))).andExpect(status().isBadRequest());
		mvc.perform(get("/api/planeacion/lugares").param("texto", "abc\n")).andExpect(status().isBadRequest())
				.andExpect(header().string("Cache-Control", "no-store"));
		verifyNoInteractions(buscador);
	}

	@Test
	void normalizaAntesDeValidarYDelegar() throws Exception {
		mvc.perform(get("/api/planeacion/lugares").param("texto", "a\u0301b"))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(buscador);
		when(buscador.buscar("ábc")).thenReturn(new BuscadorLugares.Resultado(List.of(), true, "LINEA_A"));
		mvc.perform(get("/api/planeacion/lugares").param("texto", "  a\u0301bc  "))
				.andExpect(status().isOk());
		verify(buscador).buscar("ábc");
	}

	@Test
	void respondeEstadoYResultadosVacios() throws Exception {
		when(buscador.buscar(anyString())).thenReturn(new BuscadorLugares.Resultado(List.of(), false, "LINEA_A"));
		mvc.perform(get("/api/planeacion/lugares").param("texto", "abc"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.lugares").isEmpty())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.direccionesDisponibles").value(false));
	}

	@Test
	void ocultaErroresInternos() throws Exception {
		when(buscador.buscar(anyString())).thenThrow(new IllegalStateException("secreto"));
		mvc.perform(get("/api/planeacion/lugares").param("texto", "abc"))
				.andExpect(status().isInternalServerError()).andExpect(content().string(""))
				.andExpect(header().string("Cache-Control", "no-store"));
	}

	@Test
	void devuelvePasosEnRespuestaDeRutas() throws Exception {
		var paso = PasoViaje.transporte(AccionPaso.ABORDAR, "A", "Norte", null, "Sur", 2, Idioma.EN);
		when(planeador.planear(any())).thenReturn(new PlaneadorRutas.Resultado("OK",
				List.of(new PlaneadorRutas.Opcion(List.of("MAS_RAPIDA"), 3, 0, BigDecimal.TEN, "COP",
						List.of(), List.of(paso), List.of(), "https://example.org", "2026-11-26"))));
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"a"},"destino":{"tipo":"ESTACION","id":"b"},"idioma":"EN"}
				"""))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.rutas[0].pasos[0].accion").value("ABORDAR"))
				.andExpect(jsonPath("$.rutas[0].pasos[0].descripcion").value(paso.descripcion()));
		verify(planeador).planear(argThat(s -> s.idioma() == Idioma.EN));
	}

	@Test
	void rechazaIdiomaDesconocido() throws Exception {
		mvc.perform(post("/api/planeacion/rutas").contentType("application/json").content("""
				{"origen":{"tipo":"ESTACION","id":"a"},"destino":{"tipo":"ESTACION","id":"b"},"idioma":"FR"}
				""")).andExpect(status().isBadRequest());
		verifyNoInteractions(planeador);
	}
}
