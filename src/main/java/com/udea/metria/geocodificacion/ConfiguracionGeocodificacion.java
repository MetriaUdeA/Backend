package com.udea.metria.geocodificacion;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionGeocodificacion {
	@Bean
	ServicioGeocodificacion servicioGeocodificacion(
			@Value("${metria.photon.uri:https://photon.komoot.io/api}") URI uri,
			@Value("${metria.photon.latitud:6.2442}") double latitud,
			@Value("${metria.photon.longitud:-75.5812}") double longitud) {
		return new PhotonGeocodificador(uri, latitud, longitud);
	}
}
