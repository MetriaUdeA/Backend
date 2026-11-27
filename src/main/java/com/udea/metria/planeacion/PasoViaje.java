package com.udea.metria.planeacion;

/** Campos no aplicables a la acción se dejan en null. */
public record PasoViaje(AccionPaso accion, String linea, String estacion, String estacionAnterior,
		String direccionViaje, Integer paradas, Double distanciaMetros, String descripcion) {
	static PasoViaje caminar(String estacion, double distancia, boolean inicio, Idioma idioma) {
		double metros = Math.ceil(distancia);
		String texto = idioma == Idioma.ES
				? (inicio ? "Camina desde el origen hasta " + estacion : "Camina desde " + estacion + " hasta el destino")
					+ " (aprox. " + (long) metros + " m en línea recta; no es una ruta peatonal)."
				: (inicio ? "Walk from the origin to " + estacion : "Walk from " + estacion + " to the destination")
					+ " (approx. " + (long) metros + " m in a straight line; not walking directions).";
		return new PasoViaje(AccionPaso.CAMINAR, null, estacion, null, null, null, metros, texto);
	}

	static PasoViaje transporte(AccionPaso accion, String linea, String estacion, String anterior,
			String direccion, Integer paradas, Idioma idioma) {
		String texto;
		if (accion == AccionPaso.BAJAR) {
			texto = idioma == Idioma.ES ? "Bájate en " + estacion + " de la línea " + linea + "."
					: "Get off at " + estacion + " from line " + linea + ".";
		} else {
			String lugar = anterior != null && !anterior.equals(estacion)
					? anterior + " → " + estacion : estacion;
			texto = idioma == Idioma.ES
					? (accion == AccionPaso.ABORDAR ? "Aborda" : "Transborda") + " en " + lugar
						+ " a la línea " + linea + ", dirección " + direccion + "; recorre " + paradas
						+ (paradas == 1 ? " parada." : " paradas.")
					: (accion == AccionPaso.ABORDAR ? "Board" : "Transfer") + " at " + lugar
						+ " to line " + linea + ", toward " + direccion + "; travel " + paradas
						+ (paradas == 1 ? " stop." : " stops.");
		}
		return new PasoViaje(accion, linea, estacion, anterior, direccion, paradas, null, texto);
	}
}
