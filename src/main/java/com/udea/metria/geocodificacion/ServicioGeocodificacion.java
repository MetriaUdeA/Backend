package com.udea.metria.geocodificacion;

import java.util.List;

public interface ServicioGeocodificacion {
	List<DireccionGeocodificada> buscar(String texto);
}
