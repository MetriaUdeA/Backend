package com.udea.metria.informacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InformacionControllerTest {
	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
			new InformacionController(new ServicioInformacion())).build();

	@Test
	void consultaSinViajeYTraduceInformacion() throws Exception {
		mvc.perform(get("/api/informacion/tarifas"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entradas[0].moneda").value("COP"))
				.andExpect(jsonPath("$.entradas[0].titulo").value("Integraciones 1 a 4 · Frecuente"));
		mvc.perform(get("/api/informacion/horarios").param("idioma", "EN"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entradas[0].titulo").value(
						"Monday to Saturday · A, B, T-A, H, J, K, M, O, P, 1, 2"));
		mvc.perform(get("/api/informacion/normas").param("idioma", "EN"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entradas[0].titulo").value("Respect queues"));
	}

	@Test
	void rechazaIdiomaNoSoportado() throws Exception {
		mvc.perform(get("/api/informacion/normas").param("idioma", "FR"))
				.andExpect(status().isBadRequest());
	}
}
