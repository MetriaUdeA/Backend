package com.udea.metria.planeacion;

import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;

import org.springframework.stereotype.Service;

import com.udea.metria.geocodificacion.DireccionGeocodificada;
import com.udea.metria.geocodificacion.GeocodificacionException;
import com.udea.metria.geocodificacion.ServicioGeocodificacion;

@Service
public class BuscadorLugares {
	private static final int LIMITE = 8;
	private final RedTransporte red;
	private final ServicioGeocodificacion geocodificacion;

	public BuscadorLugares(RedTransporte red, ServicioGeocodificacion geocodificacion) {
		this.red = red;
		this.geocodificacion = geocodificacion;
	}

	public Resultado buscar(String texto) {
		if (texto == null || texto.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException();
		}
		String consulta = Normalizer.normalize(texto.strip(), Normalizer.Form.NFC);
		int longitud = consulta.codePointCount(0, consulta.length());
		if (longitud < 3 || longitud > 200) {
			throw new IllegalArgumentException();
		}
		List<EstacionLocal> estaciones = red.buscarEstaciones(consulta, LIMITE);
		List<DireccionGeocodificada> direcciones;
		boolean disponible = true;
		try {
			direcciones = geocodificacion.buscar(consulta);
		} catch (GeocodificacionException e) {
			direcciones = List.of();
			disponible = false;
		}

		// Reservar un cupo para Photon cuando haya sugerencias disponibles.
		List<Lugar> lugares = new ArrayList<>();
		int cuposEstaciones = LIMITE - (direcciones.isEmpty() ? 0 : 1);
		for (EstacionLocal e : estaciones) {
			if (lugares.size() >= cuposEstaciones) break;
			lugares.add(new Lugar(e.id(), e.nombre(), e.latitud(), e.longitud(), "ESTACION", e.linea()));
		}
		for (DireccionGeocodificada d : direcciones) {
			if (lugares.size() >= LIMITE) break;
			lugares.add(new Lugar(null, d.nombre(), d.latitud(), d.longitud(), "DIRECCION", null));
		}
		return new Resultado(List.copyOf(lugares), disponible, "RED_CARGADA");
	}

	public record Lugar(String id, String nombre, double latitud, double longitud, String tipo, String linea) {
	}

	public record Resultado(List<Lugar> lugares, boolean direccionesDisponibles, String alcanceRed) {
	}
}
