package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.udea.metria.geocodificacion.DireccionGeocodificada;
import com.udea.metria.geocodificacion.GeocodificacionException;
import com.udea.metria.geocodificacion.ServicioGeocodificacion;

class BuscadorLugaresTest {
	private final RedTransporte red = mock(RedTransporte.class);
	private final ServicioGeocodificacion geocodificacion = mock(ServicioGeocodificacion.class);
	private final BuscadorLugares buscador = new BuscadorLugares(red, geocodificacion);

	@Test
	void validaLimitesSinConsultarFuentes() {
		for (String texto : new String[] { "", "ab", "a".repeat(201) }) {
			assertThrows(IllegalArgumentException.class, () -> buscador.buscar(texto));
		}
		assertThrows(IllegalArgumentException.class, () -> buscador.buscar(null));
		verifyNoInteractions(red, geocodificacion);
	}

	@Test
	void reservaCuposYConservaDatosSeleccionables() {
		var estaciones = IntStream.range(0, 8).mapToObj(i -> new EstacionLocal("e" + i, "Estación", 6, -75)).toList();
		when(red.buscarEstaciones("abc", 8)).thenReturn(estaciones);
		when(geocodificacion.buscar("abc")).thenReturn(List.of(new DireccionGeocodificada("Calle", 6, -75)));
		var resultado = buscador.buscar("  abc  ");
		assertEquals(8, resultado.lugares().size());
		assertEquals("e6", resultado.lugares().get(6).id());
		assertEquals(estaciones.get(6).nombre(), resultado.lugares().get(6).nombre());
		assertEquals("DIRECCION", resultado.lugares().get(7).tipo());
		assertNull(resultado.lugares().get(7).id());
		assertEquals(6, resultado.lugares().get(7).latitud());
		assertTrue(resultado.direccionesDisponibles());
	}

	@Test
	void distingueFalloDeAusenciaDeResultados() {
		when(geocodificacion.buscar("abc")).thenThrow(new GeocodificacionException("error"));
		var fallo = buscador.buscar("abc");
		assertTrue(fallo.lugares().isEmpty());
		assertFalse(fallo.direccionesDisponibles());
		doReturn(List.of()).when(geocodificacion).buscar("abc");
		assertTrue(buscador.buscar("abc").direccionesDisponibles());
	}

	@Test
	void buscaLugarPorNombreSoloEnPhotonSinConsultarPuntosLocales() {
		when(geocodificacion.buscar("Museo")).thenReturn(
				List.of(new DireccionGeocodificada("Museo", 6, -75)));
		var resultado = buscador.buscar("Museo");
		assertEquals(1, resultado.lugares().size());
		assertEquals("DIRECCION", resultado.lugares().getFirst().tipo());
		verify(red).buscarEstaciones("Museo", 8);
		verifyNoMoreInteractions(red);
	}

	@Test
	void conservaEstacionesCuandoFallaPhoton() {
		var estacion = new EstacionLocal("e", "Estación", 6, -75);
		when(red.buscarEstaciones("abc", 8)).thenReturn(List.of(estacion));
		when(geocodificacion.buscar("abc")).thenThrow(new GeocodificacionException("error"));
		var resultado = buscador.buscar("abc");
		assertFalse(resultado.direccionesDisponibles());
		assertEquals(List.of("e"), resultado.lugares().stream().map(BuscadorLugares.Lugar::id).toList());
		verify(red).buscarEstaciones("abc", 8);
		verifyNoMoreInteractions(red);
	}

	@Test
	void normalizaAntesDeValidarYConsultar() {
		assertThrows(IllegalArgumentException.class, () -> buscador.buscar("a\u0301b"));
		verifyNoInteractions(red, geocodificacion);
		buscador.buscar("  a\u0301bc  ");
		verify(red).buscarEstaciones("ábc", 8);
		verifyNoMoreInteractions(red);
		verify(geocodificacion).buscar("ábc");
	}

	@Test
	void rechazaControlesAntesDeConsultar() {
		assertThrows(IllegalArgumentException.class, () -> buscador.buscar("abc\n"));
		assertThrows(IllegalArgumentException.class, () -> buscador.buscar("ab\u0085c"));
		verifyNoInteractions(red, geocodificacion);
		buscador.buscar("a b");
		verify(red).buscarEstaciones("a b", 8);
	}

	@Test
	void propagaFalloDeBaseDeDatos() {
		when(red.buscarEstaciones("abc", 8)).thenThrow(new IllegalStateException("secreto"));
		assertThrows(IllegalStateException.class, () -> buscador.buscar("abc"));
	}
}
