package com.udea.metria.planeacion;

/**
 * Un tramo de transporte entre dos estaciones de la misma línea.
 * La duración todavía no incluye caminatas ni transbordos.
 */
public record SegmentoRuta(String linea, String estacionInicio, String estacionFin, double duracionMin) {
	public SegmentoRuta {
		if (linea == null || linea.isBlank() || estacionInicio == null || estacionInicio.isBlank()
				|| estacionFin == null || estacionFin.isBlank() || estacionInicio.equals(estacionFin)
				|| !Double.isFinite(duracionMin) || duracionMin < 0) {
			throw new IllegalArgumentException("Segmento de ruta inválido");
		}
	}
}
