package com.udea.metria.planeacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.udea.metria.informacion.ServicioInformacion;

/** Estimaciones para Cívica eventual; Arví usa la tarifa general. */
@Service
public class ServicioTarifas {
	public static final String MONEDA = "COP";
	private static final Set<String> CABLES = Set.of("H", "J", "K", "M", "P");
	private final BigDecimal red;
	private final BigDecimal arvi;
	private final String fuente;
	private final String fechaRevision;
	public record Cobro(String tipo, BigDecimal valor) {}
	public record Estimacion(BigDecimal total, List<Cobro> desglose, int mascara) {}

	public ServicioTarifas() {
		var informacion = new ServicioInformacion();
		red = BigDecimal.valueOf(informacion.valorTarifa("RED_EVENTUAL"));
		arvi = BigDecimal.valueOf(informacion.valorTarifa("ARVI_GENERAL"));
		var catalogo = informacion.consultarTarifas(Idioma.ES);
		fuente = catalogo.fuente();
		fechaRevision = catalogo.fechaRevision();
	}
	public String fuente() { return fuente; }
	public String fechaRevision() { return fechaRevision; }

	/** Retorna null para una combinación de modos sin regla verificada. */
	public BigDecimal calcular(List<String> lineas) { return estimar(lineas).total(); }

	public Estimacion estimar(List<String> lineas) {
		if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("Ruta sin líneas");
		List<Cobro> cobros = new ArrayList<>();
		String anterior = "";
		int mascara = 0;
		boolean desconocida = false;
		for (String linea : lineas) {
			if (linea == null || linea.isBlank()) throw new IllegalArgumentException("Línea inválida");
			if (linea.equals(anterior)) continue;
			String modo = modo(linea);
			String modoAnterior = anterior.isEmpty() ? "" : modo(anterior);
			if ("ARVI".equals(modo)) {
				cobros.add(new Cobro("ARVI_GENERAL", arvi));
				mascara = 0;
			} else if ("DESCONOCIDO".equals(modo)) {
				desconocida = true;
			} else {
				int bit = switch (modo) { case "METRO" -> 1; case "CABLE" -> 2; default -> 4; };
				if (mascara == 0) cobros.add(new Cobro("RED_EVENTUAL", red));
				// Dos cables distintos o volver a un modo anterior no están cubiertos
				// por las combinaciones verificadas; no se inventa su tarifa.
				if ((mascara & bit) != 0 && (!modo.equals(modoAnterior) || "CABLE".equals(modo)))
					desconocida = true;
				mascara |= bit;
			}
			anterior = linea;
		}
		if (desconocida) return new Estimacion(null, List.of(), mascara);
		return new Estimacion(cobros.stream().map(Cobro::valor).reduce(BigDecimal.ZERO, BigDecimal::add), List.copyOf(cobros), mascara);
	}

	private String modo(String linea) {
		if ("L".equals(linea)) return "ARVI";
		if ("A".equals(linea) || "B".equals(linea)) return "METRO";
		if (CABLES.contains(linea)) return "CABLE";
		if ("T".equals(linea)) return "TRANVIA";
		return "DESCONOCIDO";
	}
}
