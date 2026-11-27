package com.udea.metria.geocodificacion;

public class GeocodificacionException extends RuntimeException {
	public GeocodificacionException(String mensaje) {
		super(mensaje);
	}

	public GeocodificacionException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
