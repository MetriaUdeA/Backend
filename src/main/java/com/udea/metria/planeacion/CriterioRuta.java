package com.udea.metria.planeacion;

import java.util.Comparator;

/** Ordena candidatas; el identificador estable resuelve el último empate. */
public interface CriterioRuta {
	String etiqueta();

	Comparator<Ruta> comparador();

	static String clave(Ruta ruta) {
		StringBuilder clave = new StringBuilder();
		for (SegmentoRuta s : ruta.segmentos()) {
			for (String campo : new String[] { s.linea(), s.estacionInicio(), s.estacionFin() }) {
				clave.append(campo.length()).append(':').append(campo);
			}
		}
		return clave.toString();
	}
}
