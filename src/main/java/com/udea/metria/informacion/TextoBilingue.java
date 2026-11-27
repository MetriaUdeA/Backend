package com.udea.metria.informacion;

import com.udea.metria.planeacion.Idioma;

public record TextoBilingue(String es, String en) {
	public String obtener(Idioma idioma) {
		return idioma == Idioma.EN ? en : es;
	}
}
