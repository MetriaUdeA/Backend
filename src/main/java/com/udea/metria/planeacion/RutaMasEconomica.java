package com.udea.metria.planeacion;

import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class RutaMasEconomica implements CriterioRuta {
	public String etiqueta() { return "MAS_ECONOMICA"; }
	public Comparator<Ruta> comparador() {
		return Comparator.comparing(Ruta::tarifaAproximada, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparingDouble(Ruta::calcularDuracionTotal)
				.thenComparingInt(Ruta::contarTransbordos).thenComparing(CriterioRuta::clave);
	}
}
