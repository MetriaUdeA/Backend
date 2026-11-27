package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class RedTransporteTest {

	@Test
	void rechazaParametrosInvalidosSinConsultarLaBase() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RedTransporte red = new RedTransporte(jdbc);
		assertThrows(IllegalArgumentException.class, () -> red.buscarEstaciones(null, 8));
		assertThrows(IllegalArgumentException.class, () -> red.buscarEstaciones(" ab ", 8));
		assertThrows(IllegalArgumentException.class, () -> red.buscarEstaciones("a\u0301b", 8));
		assertThrows(IllegalArgumentException.class, () -> red.buscarEstaciones("áb", 8));
		assertThrows(IllegalArgumentException.class, () -> red.buscarEstaciones("metro", 0));
		assertThrows(IllegalArgumentException.class, () -> red.buscarEstaciones("metro", 9));
		verifyNoInteractions(jdbc);
	}

	@Test
	@SuppressWarnings("unchecked")
	void enviaTextoYLimiteComoParametros() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RedTransporte red = new RedTransporte(jdbc);
		red.buscarEstaciones("  Poblado  ", 3);
		red.buscarEstaciones("  Explora  ", 8);
		verify(jdbc).query(anyString(), any(RowMapper.class), eq("Poblado"), eq(3));
		verify(jdbc).query(anyString(), any(RowMapper.class), eq("Explora"), eq(8));
	}

	@Test
	@SuppressWarnings("unchecked")
	void enviaTextoNormalizado() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RedTransporte red = new RedTransporte(jdbc);
		red.buscarEstaciones("a\u0301bc", 8);
		verify(jdbc).query(anyString(), any(RowMapper.class), eq("ábc"), eq(8));
	}

	@Test
	@SuppressWarnings("unchecked")
	void consultasSeparanWhereYNormalizanAmbosTextos() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		RedTransporte red = new RedTransporte(jdbc);
		red.buscarEstaciones("poblado", 8);
		red.buscarEstaciones("explora", 8);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbc, org.mockito.Mockito.times(2)).query(sql.capture(), any(RowMapper.class), anyString(), eq(8));
		for (String consulta : sql.getAllValues()) {
			assertTrue(consulta.matches("(?s).*WHERE\\s+position\\(.*"));
			assertTrue(consulta.contains("normalize(?, NFC)"));
			assertTrue(consulta.contains("normalize(nombre, NFC)"));
			assertTrue(consulta.contains("LIMIT ?"));
		}
		assertTrue(sql.getAllValues().get(1).contains("FROM metria.estacion"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void mapeaLaLineaYLasCoordenadasDeUnaEstacion() throws Exception {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		ResultSet resultado = mock(ResultSet.class);
		when(resultado.getString("id")).thenReturn("metro-1");
		when(resultado.getString("nombre")).thenReturn("Acevedo");
		when(resultado.getString("linea")).thenReturn("A");
		when(resultado.getDouble("latitud")).thenReturn(6.3);
		when(resultado.getDouble("longitud")).thenReturn(-75.5);
		when(jdbc.query(anyString(), any(RowMapper.class), anyString(), eq(8))).thenAnswer(invocacion -> {
			RowMapper<EstacionLocal> mapper = invocacion.getArgument(1);
			return List.of(mapper.mapRow(resultado, 0));
		});
		RedTransporte red = new RedTransporte(jdbc);
		assertEquals(new EstacionLocal("metro-1", "Acevedo", 6.3, -75.5, "A"),
				red.buscarEstaciones("Acevedo", 8).getFirst());
	}
}
