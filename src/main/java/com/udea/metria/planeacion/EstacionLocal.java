package com.udea.metria.planeacion;

public record EstacionLocal(String id, String nombre, double latitud, double longitud, String linea) {
	public EstacionLocal(String id, String nombre, double latitud, double longitud) {
		this(id, nombre, latitud, longitud, null);
	}
}
