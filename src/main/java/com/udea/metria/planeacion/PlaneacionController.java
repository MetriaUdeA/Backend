package com.udea.metria.planeacion;

import java.text.Normalizer;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planeacion")
public class PlaneacionController {
	private final BuscadorLugares buscador;
	private final PlaneadorRutas planeador;

	public PlaneacionController(BuscadorLugares buscador, PlaneadorRutas planeador) {
		this.buscador = buscador;
		this.planeador = planeador;
	}

	@PostMapping("/rutas")
	public ResponseEntity<?> planearViaje(@RequestBody PlaneadorRutas.Solicitud solicitud) {
		try {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(planeador.planear(solicitud));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
		}
	}

	@GetMapping("/lugares")
	public ResponseEntity<?> buscarLugares(@RequestParam(required = false) String texto) {
		// La URL puede quedar registrada por intermediarios; no registrar consultas y
		// advertir al usuario que el texto se envía al proveedor externo Photon.
		if (texto == null || texto.codePoints().anyMatch(Character::isISOControl)) {
			return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
		}
		String consulta = Normalizer.normalize(texto.strip(), Normalizer.Form.NFC);
		int longitud = consulta.codePointCount(0, consulta.length());
		if (longitud < 3 || longitud > 200) {
			return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
		}
		try {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(buscador.buscar(consulta));
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).cacheControl(CacheControl.noStore()).build();
		}
	}
}
