package com.udea.metria.planeacion;

import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class RutaMasRapida implements CriterioRuta {
	public String etiqueta() { return "MAS_RAPIDA"; }
	public Comparator<Ruta> comparador() {
		return Comparator.comparingDouble(Ruta::calcularDuracionTotal)
				.thenComparingInt(Ruta::contarTransbordos)
				.thenComparing(Ruta::tarifaAproximada, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CriterioRuta::clave);
	}
}
