package com.udea.metria.informacion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.udea.metria.planeacion.Idioma;

@RestController
@RequestMapping("/api/informacion")
public class InformacionController {
	private final ServicioInformacion servicio;

	public InformacionController(ServicioInformacion servicio) { this.servicio = servicio; }

	@GetMapping("/tarifas")
	public ServicioInformacion.Resultado obtenerTarifas(@RequestParam(defaultValue = "ES") Idioma idioma) {
		return servicio.consultarTarifas(idioma);
	}
	@GetMapping("/horarios")
	public ServicioInformacion.Resultado obtenerHorarios(@RequestParam(defaultValue = "ES") Idioma idioma) {
		return servicio.consultarHorarios(idioma);
	}
	@GetMapping("/normas")
	public ServicioInformacion.Resultado obtenerNormas(@RequestParam(defaultValue = "ES") Idioma idioma) {
		return servicio.consultarNormas(idioma);
	}
}
