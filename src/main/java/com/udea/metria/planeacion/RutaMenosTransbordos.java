package com.udea.metria.planeacion;

import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class RutaMenosTransbordos implements CriterioRuta {
	public String etiqueta() { return "MENOS_TRANSBORDOS"; }
	public Comparator<Ruta> comparador() {
		return Comparator.comparingInt(Ruta::contarTransbordos)
				.thenComparingDouble(Ruta::calcularDuracionTotal)
				.thenComparing(Ruta::tarifaAproximada, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CriterioRuta::clave);
	}
}
