package com.udea.metria.informacion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Service;
import com.udea.metria.planeacion.Idioma;
import tools.jackson.databind.ObjectMapper;

@Service
public class ServicioInformacion {
	private final Catalogo tarifas;
	private final Catalogo horarios;
	private final Catalogo normas;

	public record Entrada(String id, TextoBilingue titulo, TextoBilingue detalle, TextoBilingue consecuencia,
			Integer valor, String moneda) {}
	public record Catalogo(String fuente, String fechaRevision, List<Entrada> entradas) {}
	public record EntradaTraducida(String titulo, String detalle, String consecuencia, Integer valor, String moneda) {}
	public record Resultado(String fuente, String fechaRevision, List<EntradaTraducida> entradas) {}

	public ServicioInformacion() {
		ObjectMapper mapper = new ObjectMapper();
		tarifas = cargar(mapper, "tarifas");
		horarios = cargar(mapper, "horarios");
		normas = cargar(mapper, "normas");
	}

	private Catalogo cargar(ObjectMapper mapper, String nombre) {
		try (InputStream entrada = getClass().getResourceAsStream("/informacion/" + nombre + ".json")) {
			if (entrada == null) throw new IllegalStateException("Falta catálogo " + nombre);
			return mapper.readValue(entrada, Catalogo.class);
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo cargar el catálogo " + nombre, e);
		}
	}

	public Resultado consultarTarifas(Idioma idioma) { return traducir(tarifas, idioma); }
	public Resultado consultarHorarios(Idioma idioma) { return traducir(horarios, idioma); }
	public Resultado consultarNormas(Idioma idioma) { return traducir(normas, idioma); }

	public int valorTarifa(String id) {
		var encontrados = tarifas.entradas().stream().filter(e -> id.equals(e.id())).toList();
		if (encontrados.size() != 1 || encontrados.getFirst().valor() == null
				|| encontrados.getFirst().valor() < 0 || !"COP".equals(encontrados.getFirst().moneda()))
			throw new IllegalStateException("Tarifa de referencia inválida: " + id);
		return encontrados.getFirst().valor();
	}

	private Resultado traducir(Catalogo catalogo, Idioma idioma) {
		return new Resultado(catalogo.fuente(), catalogo.fechaRevision(), catalogo.entradas().stream()
				.map(e -> new EntradaTraducida(e.titulo().obtener(idioma), e.detalle().obtener(idioma),
						e.consecuencia() == null ? null : e.consecuencia().obtener(idioma),
						e.valor(), e.moneda())).toList());
	}
}
