package com.udea.metria.planeacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Recorrido candidato. Los tiempos son estimaciones y no incluyen espera. */
public record Ruta(List<SegmentoRuta> segmentos, BigDecimal tarifaAproximada) {
	public Ruta {
		segmentos = List.copyOf(segmentos);
		if (segmentos.isEmpty() || (tarifaAproximada != null && tarifaAproximada.signum() < 0)) {
			throw new IllegalArgumentException("Ruta inválida");
		}
		for (int i = 1; i < segmentos.size(); i++) {
			SegmentoRuta anterior = segmentos.get(i - 1);
			SegmentoRuta actual = segmentos.get(i);
			// Los identificadores de dos líneas pueden ser distintos incluso en
			// una misma estación de transbordo; la red valida esa conexión.
			if (anterior.linea().equals(actual.linea())
					&& !anterior.estacionFin().equals(actual.estacionInicio())) {
				throw new IllegalArgumentException("Segmentos discontinuos en la misma línea");
			}
		}
	}

	public int contarTransbordos() {
		int transbordos = 0;
		for (int i = 1; i < segmentos.size(); i++) {
			if (!segmentos.get(i - 1).linea().equals(segmentos.get(i).linea())) transbordos++;
		}
		return transbordos;
	}

	public double calcularDuracionTotal() {
		return segmentos.stream().mapToDouble(SegmentoRuta::duracionMin).sum() + 4.0 * contarTransbordos();
	}

	public List<PasoViaje> generarPasos(List<RedTransporte.EstacionRuta> estaciones, Idioma idioma) {
		Map<String, RedTransporte.EstacionRuta> porId = estaciones.stream()
				.collect(Collectors.toMap(RedTransporte.EstacionRuta::id, Function.identity()));
		List<PasoViaje> pasos = new ArrayList<>();
		for (int i = 0; i < segmentos.size();) {
			SegmentoRuta primero = segmentos.get(i);
			int fin = i + 1;
			while (fin < segmentos.size() && segmentos.get(fin).linea().equals(primero.linea())) fin++;
			SegmentoRuta ultimo = segmentos.get(fin - 1);
			var inicio = Objects.requireNonNull(porId.get(primero.estacionInicio()), "Estación inicial desconocida");
			var llegada = Objects.requireNonNull(porId.get(ultimo.estacionFin()), "Estación final desconocida");
			boolean creciente = llegada.posicion() > inicio.posicion();
			var terminal = estaciones.stream().filter(e -> e.linea().equals(primero.linea()))
					.reduce((a, b) -> (creciente ? a.posicion() > b.posicion() : a.posicion() < b.posicion()) ? a : b)
					.orElseThrow();
			String anterior = i == 0 ? null : Objects.requireNonNull(porId.get(segmentos.get(i - 1).estacionFin())).nombre();
			pasos.add(PasoViaje.transporte(i == 0 ? AccionPaso.ABORDAR : AccionPaso.TRANSBORDAR,
					primero.linea(), inicio.nombre(), anterior, terminal.nombre(), fin - i, idioma));
			pasos.add(PasoViaje.transporte(AccionPaso.BAJAR, primero.linea(), llegada.nombre(), null, null, null, idioma));
			i = fin;
		}
		return List.copyOf(pasos);
	}
}
