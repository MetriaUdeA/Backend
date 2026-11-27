package com.udea.metria.planeacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.udea.metria.ConfiguracionBasePruebas;

class ProteccionBasePruebasTest {

	@Test
	void aceptaSoloConfiguracionExplicitaDePruebas() {
		assertDoesNotThrow(() -> validar("jdbc:postgresql://servidor:5432/metria_test", "usuario", "clave", "SI"));
		assertDoesNotThrow(() -> validar("jdbc:postgresql://servidor/metria_pruebas", "usuario", "clave", "SI"));
		assertDoesNotThrow(() -> validar("jdbc:postgresql://servidor:1/metria_test", "usuario", "clave", "SI"));
		assertDoesNotThrow(() -> validar("jdbc:postgresql://servidor:65535/metria_test", "usuario", "clave", "SI"));
		assertThrows(IllegalStateException.class, () -> validar(null, "usuario", "clave", "SI"));
		assertThrows(IllegalStateException.class, () -> validar("jdbc:postgresql://servidor/metria_test", null, "clave", "SI"));
		assertThrows(IllegalStateException.class, () -> validar("jdbc:postgresql://servidor/metria_test", "usuario", " ", "SI"));
		assertThrows(IllegalStateException.class, () -> validar("jdbc:postgresql://servidor/metria_test", "usuario", "clave", "si"));
		assertThrows(IllegalStateException.class, () -> validar("jdbc:postgresql://servidor/metria_test", "usuario", "clave", null));
	}

	@Test
	void rechazaDestinosDudosos() {
		for (String url : new String[] {
				"jdbc:postgresql://servidor/metria",
				"jdbc:postgresql://servidor/metria_test?databaseName=produccion",
				"jdbc:postgresql://usuario@servidor/metria_test",
				"jdbc:postgresql://servidor/metria_test/otra",
				"jdbc:postgresql://servidor/metria_test#fragmento",
				"jdbc:postgresql://servidor:0/metria_test",
				"jdbc:postgresql://servidor:65536/metria_test",
				"jdbc:postgresql:metria_test",
				"jdbc:mysql://servidor/metria_test"
		}) {
			assertThrows(IllegalStateException.class, () -> validar(url, "usuario", "clave", "SI"));
		}
	}

	private void validar(String url, String usuario, String clave, String confirmacion) {
		ConfiguracionBasePruebas.crearDataSource(url, usuario, clave, confirmacion);
	}
}
