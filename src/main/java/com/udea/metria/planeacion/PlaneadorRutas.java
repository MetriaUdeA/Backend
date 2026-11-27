package com.udea.metria.planeacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlaneadorRutas {
	private final RedTransporte red;
	private final ServicioTarifas tarifas;
	private final List<CriterioRuta> criterios;
	private final ObjectMapper json = new ObjectMapper();

	public PlaneadorRutas(RedTransporte red, ServicioTarifas tarifas, List<CriterioRuta> criterios) {
		this.red = red;
		this.tarifas = tarifas;
		this.criterios = criterios;
	}

	public record LugarEntrada(String tipo, String id, Double latitud, Double longitud) {}
	public record Solicitud(LugarEntrada origen, LugarEntrada destino, Idioma idioma) {
		public Solicitud(LugarEntrada origen, LugarEntrada destino) { this(origen, destino, null); }
	}
	public record Geometria(String tipo, String linea, JsonNode geometria) {}
	public record Opcion(List<String> etiquetas, double duracionMin, int transbordos,
			BigDecimal tarifaAproximada, String moneda, List<Geometria> trazados, List<PasoViaje> pasos,
			List<ServicioTarifas.Cobro> desgloseTarifa, String fuenteTarifa, String fechaRevisionTarifa) {}
	public record Resultado(String estado, List<Opcion> rutas) {}
	private record Arista(String destino, String linea, double minutos, JsonNode geometria) {}
	private record Paso(String desde, Arista arista) {}
	private record Estado(String estacion, String ultimaLinea, int mascara, boolean tarifaDesconocida) {}
	private record Camino(Estado estado, List<Paso> pasos, List<SegmentoRuta> segmentos) {}

	public Resultado planear(Solicitud solicitud) {
		if (solicitud == null) throw new IllegalArgumentException("Solicitud inválida");
		var origen = acceso(solicitud.origen());
		var destino = acceso(solicitud.destino());
		if (origen == null || destino == null) return new Resultado("FUERA_DE_COBERTURA", List.of());
		if (origen.id().equals(destino.id()) || red.transbordos().stream().anyMatch(t ->
				(t.a().equals(origen.id()) && t.b().equals(destino.id()))
				|| (t.b().equals(origen.id()) && t.a().equals(destino.id())))) {
			return new Resultado("MISMA_ESTACION", List.of());
		}

		Map<String, List<Arista>> grafo = grafo();
		Map<String, List<String>> etiquetas = new LinkedHashMap<>();
		Map<String, Camino> caminos = new LinkedHashMap<>();
		for (CriterioRuta criterio : criterios) {
			Camino mejor = buscar(grafo, origen.id(), destino.id(), criterio);
			if (mejor == null) continue;
			String clave = CriterioRuta.clave(ruta(mejor));
			caminos.putIfAbsent(clave, mejor);
			etiquetas.computeIfAbsent(clave, k -> new ArrayList<>()).add(criterio.etiqueta());
		}
		if (caminos.isEmpty()) return new Resultado("SIN_RUTA", List.of());
		var estaciones = red.estacionesRuta();
		Idioma idioma = solicitud.idioma() == null ? Idioma.ES : solicitud.idioma();
		List<Opcion> opciones = new ArrayList<>();
		for (var entrada : caminos.entrySet()) {
			Camino camino = entrada.getValue();
			Ruta ruta = ruta(camino);
			var estimacion = tarifas.estimar(camino.segmentos().stream().map(SegmentoRuta::linea).toList());
			List<Geometria> trazados = new ArrayList<>();
			agregarCaminata(trazados, solicitud.origen(), origen, true);
			for (Paso paso : camino.pasos()) {
				if (paso.arista().linea() != null)
					trazados.add(new Geometria("TRANSPORTE", paso.arista().linea(), paso.arista().geometria()));
			}
			agregarCaminata(trazados, solicitud.destino(), destino, false);
			double caminata = (origen.distancia() + destino.distancia()) * 60 / 4000;
			List<PasoViaje> instrucciones = new ArrayList<>();
			if ("DIRECCION".equals(solicitud.origen().tipo()) && origen.distancia() >= 0.01)
				instrucciones.add(PasoViaje.caminar(origen.nombre(), origen.distancia(), true, idioma));
			instrucciones.addAll(ruta.generarPasos(estaciones, idioma));
			if ("DIRECCION".equals(solicitud.destino().tipo()) && destino.distancia() >= 0.01)
				instrucciones.add(PasoViaje.caminar(destino.nombre(), destino.distancia(), false, idioma));
			opciones.add(new Opcion(List.copyOf(etiquetas.get(entrada.getKey())),
					Math.ceil(ruta.calcularDuracionTotal() + caminata), ruta.contarTransbordos(),
					ruta.tarifaAproximada(), ServicioTarifas.MONEDA, List.copyOf(trazados), List.copyOf(instrucciones),
					estimacion.desglose(), tarifas.fuente(), tarifas.fechaRevision()));
		}
		return new Resultado("OK", List.copyOf(opciones));
	}

	private RedTransporte.Acceso acceso(LugarEntrada lugar) {
		if (lugar == null || lugar.tipo() == null) throw new IllegalArgumentException("Lugar inválido");
		if ("ESTACION".equals(lugar.tipo())) {
			if (lugar.id() == null || lugar.id().isBlank()) throw new IllegalArgumentException("Estación inválida");
			return red.estacionAcceso(lugar.id()).orElse(null);
		}
		if ("DIRECCION".equals(lugar.tipo())) {
			if (lugar.latitud() == null || lugar.longitud() == null || !Double.isFinite(lugar.latitud())
					|| !Double.isFinite(lugar.longitud()) || Math.abs(lugar.latitud()) > 90
					|| Math.abs(lugar.longitud()) > 180) throw new IllegalArgumentException("Coordenadas inválidas");
			return red.estacionMasCercana(lugar.latitud(), lugar.longitud()).orElse(null);
		}
		throw new IllegalArgumentException("Tipo inválido");
	}

	private Map<String, List<Arista>> grafo() {
		Map<String, List<Arista>> grafo = new HashMap<>();
		for (var c : red.conexiones()) {
			JsonNode geometria = json.readTree(c.geometria());
			var coordenadas = geometria.path("coordinates");
			var invertidas = json.createArrayNode();
			for (int i = coordenadas.size() - 1; i >= 0; i--) invertidas.add(coordenadas.get(i));
			var inversa = json.createObjectNode().put("type", "LineString").set("coordinates", invertidas);
			agregar(grafo, c.inicio(), new Arista(c.fin(), c.linea(), c.duracion(), geometria));
			agregar(grafo, c.fin(), new Arista(c.inicio(), c.linea(), c.duracion(), inversa));
		}
		for (var t : red.transbordos()) {
			agregar(grafo, t.a(), new Arista(t.b(), null, 0, null));
			agregar(grafo, t.b(), new Arista(t.a(), null, 0, null));
		}
		return grafo;
	}

	private static void agregar(Map<String, List<Arista>> grafo, String inicio, Arista arista) {
		grafo.computeIfAbsent(inicio, k -> new ArrayList<>()).add(arista);
	}

	private Camino buscar(Map<String, List<Arista>> grafo, String origen, String destino, CriterioRuta criterio) {
		Comparator<Camino> comparador = (a, b) -> {
			if (a.segmentos().isEmpty() || b.segmentos().isEmpty())
				return Boolean.compare(!a.segmentos().isEmpty(), !b.segmentos().isEmpty());
			return criterio.comparador().compare(ruta(a), ruta(b));
		};
		PriorityQueue<Camino> cola = new PriorityQueue<>(comparador);
		Map<Estado, Camino> mejores = new HashMap<>();
		Camino inicial = new Camino(new Estado(origen, "", 0, false), List.of(), List.of());
		cola.add(inicial);
		mejores.put(inicial.estado(), inicial);
		while (!cola.isEmpty()) {
			Camino actual = cola.remove();
			if (mejores.get(actual.estado()) != actual) continue;
			if (actual.estado().estacion().equals(destino) && !actual.segmentos().isEmpty()) return actual;
			for (Arista arista : grafo.getOrDefault(actual.estado().estacion(), List.of())) {
				String linea = arista.linea() == null ? actual.estado().ultimaLinea() : arista.linea();
				List<SegmentoRuta> segmentos = new ArrayList<>(actual.segmentos());
				if (arista.linea() != null) {
					segmentos.add(new SegmentoRuta(linea, actual.estado().estacion(), arista.destino(), arista.minutos()));
				}
				var estimacion = segmentos.isEmpty() ? null : tarifas.estimar(segmentos.stream().map(SegmentoRuta::linea).toList());
				if ("MAS_ECONOMICA".equals(criterio.etiqueta()) && estimacion != null && estimacion.total() == null) continue;
				List<Paso> pasos = new ArrayList<>(actual.pasos());
				pasos.add(new Paso(actual.estado().estacion(), arista));
				Estado siguiente = new Estado(arista.destino(), linea, estimacion == null ? 0 : estimacion.mascara(),
						estimacion != null && estimacion.total() == null);
				Camino candidato = new Camino(siguiente, List.copyOf(pasos), List.copyOf(segmentos));
				Camino anterior = mejores.get(siguiente);
				if (anterior == null || comparador.compare(candidato, anterior) < 0) {
					mejores.put(siguiente, candidato);
					cola.add(candidato);
				}
			}
		}
		return null;
	}

	private Ruta ruta(Camino camino) {
		return new Ruta(camino.segmentos(), tarifas.calcular(camino.segmentos().stream().map(SegmentoRuta::linea).toList()));
	}

	private void agregarCaminata(List<Geometria> trazados, LugarEntrada lugar, RedTransporte.Acceso acceso, boolean inicio) {
		if (!"DIRECCION".equals(lugar.tipo()) || acceso.distancia() < 0.01) return;
		var origen = json.createArrayNode().add(lugar.longitud()).add(lugar.latitud());
		var estacion = json.createArrayNode().add(acceso.longitud()).add(acceso.latitud());
		var coordenadas = json.createArrayNode();
		if (inicio) { coordenadas.add(origen); coordenadas.add(estacion); }
		else { coordenadas.add(estacion); coordenadas.add(origen); }
		var linea = json.createObjectNode().put("type", "LineString").set("coordinates", coordenadas);
		trazados.add(new Geometria("CAMINATA", null, linea));
	}
}
