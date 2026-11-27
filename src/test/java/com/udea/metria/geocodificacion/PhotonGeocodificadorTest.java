package com.udea.metria.geocodificacion;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class PhotonGeocodificadorTest {
	private static final URI URI_PHOTON = URI.create("https://photon.example/api");

	@Test
	void codificaLaConsultaYPriorizaSinFiltrar() {
		AtomicReference<URI> llamada = new AtomicReference<>();
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6.25, -75.57, uri -> {
			llamada.set(uri);
			return new PhotonGeocodificador.Respuesta(200, """
					{"features":[{"properties":{"name":"Café"},"geometry":{"coordinates":[-75.5,6.2]}}]}
					""");
		});
		assertEquals(List.of(new DireccionGeocodificada("Café", 6.2, -75.5)), servicio.buscar("café & metro"));
		assertEquals("q=caf%C3%A9+%26+metro&lat=6.25&lon=-75.57&limit=8", llamada.get().getRawQuery());
	}

	@Test
	void distingueResultadosVaciosDeFallos() {
		var vacio = new PhotonGeocodificador(URI_PHOTON, 6, -75,
				uri -> new PhotonGeocodificador.Respuesta(200, "{\"features\":[]}"));
		assertTrue(vacio.buscar("metro").isEmpty());
		var error = new PhotonGeocodificador(URI_PHOTON, 6, -75,
				uri -> new PhotonGeocodificador.Respuesta(503, ""));
		assertThrows(GeocodificacionException.class, () -> error.buscar("metro"));
		var red = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> { throw new IOException(); });
		assertThrows(GeocodificacionException.class, () -> red.buscar("metro"));
	}

	@Test
	void rechazaDatosInvalidosYUriInsegura() {
		assertThrows(IllegalArgumentException.class,
				() -> new PhotonGeocodificador(URI.create("http://localhost"), 6, -75));
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75,
				uri -> new PhotonGeocodificador.Respuesta(200,
						"{\"features\":[{\"properties\":{},\"geometry\":{\"coordinates\":[1,2]}}]}"));
		assertThrows(GeocodificacionException.class, () -> servicio.buscar("metro"));
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar(" "));
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar("x".repeat(257)));
	}

	@Test
	void conservaResultadosValidosYConstruyeDireccion() {
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75,
				uri -> new PhotonGeocodificador.Respuesta(200, """
						{"features":[
						  {"properties":{},"geometry":{"coordinates":[1,2]}},
						  {"properties":{"street":"Calle 10","housenumber":"20-30"},"geometry":{"coordinates":[-75,6]}}
						]}
						"""));
		assertEquals(List.of(new DireccionGeocodificada("Calle 10 20-30", 6, -75)), servicio.buscar("calle"));
	}

	@Test
	void usaDireccionCuandoNombreEsEspacioUnicode() {
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75,
				uri -> new PhotonGeocodificador.Respuesta(200, """
						{"features":[
						  {"properties":{"name":"\u00a0","street":"\u2007Calle 10\u202f","housenumber":"\u00a020-30\u2003"},"geometry":{"coordinates":[-75,6]}},
						  {"properties":{"name":"\u2003","street":"\u00a0","housenumber":"\u2003"},"geometry":{"coordinates":[-75,6]}},
						  {"properties":{"name":"\u00a0Sitio\u00a0interior\u00a0"},"geometry":{"coordinates":[-75,6]}}
						]}
						"""));
		assertEquals(List.of(new DireccionGeocodificada("Calle 10 20-30", 6, -75),
				new DireccionGeocodificada("Sitio\u00a0interior", 6, -75)), servicio.buscar("calle"));
	}

	@Test
	void limitaPuntosDeCodigoTrasNormalizarYRecortar() {
		AtomicReference<URI> llamada = new AtomicReference<>();
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> {
			llamada.set(uri);
			return new PhotonGeocodificador.Respuesta(200, "{\"features\":[]}");
		});
		String suplementarios = "😀".repeat(129);
		servicio.buscar("  " + suplementarios + "  ");
		String consulta = llamada.get().getRawQuery().split("&")[0].substring(2);
		assertEquals(suplementarios, URLDecoder.decode(consulta, StandardCharsets.UTF_8));
		llamada.set(null);
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar("😀".repeat(201)));
		assertNull(llamada.get());
		servicio.buscar("e\u0301".repeat(200));
		assertEquals("é".repeat(200), URLDecoder.decode(
				llamada.get().getRawQuery().split("&")[0].substring(2), StandardCharsets.UTF_8));
	}

	@Test
	void rechazaTextoCortoYControlesAntesDelTransporte() {
		AtomicInteger llamadas = new AtomicInteger();
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> {
			llamadas.incrementAndGet();
			return new PhotonGeocodificador.Respuesta(200, "{\"features\":[]}");
		});
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar("😀😀"));
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar("e\u0301x"));
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar("metro\n"));
		assertThrows(IllegalArgumentException.class, () -> servicio.buscar("me\u0000tro"));
		assertEquals(0, llamadas.get());
		assertTrue(servicio.buscar("😀😀😀").isEmpty());
		assertEquals(1, llamadas.get());
	}

	@Test
	void liberaCupoTrasFallosDeTransporteYRespuesta() {
		AtomicInteger llamadas = new AtomicInteger();
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> {
			int intento = llamadas.incrementAndGet();
			if (intento <= 5) {
				throw new IOException();
			}
			if (intento <= 10) {
				return new PhotonGeocodificador.Respuesta(503, "");
			}
			return new PhotonGeocodificador.Respuesta(200, "{\"features\":[]}");
		});
		for (int i = 0; i < 10; i++) {
			assertThrows(GeocodificacionException.class, () -> servicio.buscar("metro"));
		}
		assertTrue(servicio.buscar("metro").isEmpty());
		assertEquals(11, llamadas.get());
	}

	@Test
	void limitaOchoSinFiltrarResultadosLejanos() {
		String cercano = "{\"properties\":{\"name\":\"Cerca\"},\"geometry\":{\"coordinates\":[-75,6]}}";
		String lejano = "{\"properties\":{\"name\":\"Lejos\"},\"geometry\":{\"coordinates\":[2,48]}}";
		String json = "{\"features\":[" + cercano + "," + lejano + "," + (cercano + ",").repeat(7) + cercano + "]}";
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75,
				uri -> new PhotonGeocodificador.Respuesta(200, json));
		var resultados = servicio.buscar("lugar");
		assertEquals(8, resultados.size());
		assertEquals("Lejos", resultados.get(1).nombre());
	}

	@Test
	void limitaConcurrenciaYLiberaCupos() throws Exception {
		CountDownLatch ocupados = new CountDownLatch(4);
		CountDownLatch liberar = new CountDownLatch(1);
		var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> {
			ocupados.countDown();
			if (!liberar.await(5, TimeUnit.SECONDS)) {
				throw new IOException("Prueba agotada");
			}
			return new PhotonGeocodificador.Respuesta(200, "{\"features\":[]}");
		});
		try (var ejecutor = Executors.newFixedThreadPool(5)) {
			try {
				var tareas = java.util.stream.IntStream.range(0, 4)
						.mapToObj(i -> ejecutor.submit(() -> servicio.buscar("lugar"))).toList();
				assertTrue(ocupados.await(3, TimeUnit.SECONDS));
				var adicional = ejecutor.submit(() ->
						assertThrows(GeocodificacionException.class, () -> servicio.buscar("lugar")));
				adicional.get(1, TimeUnit.SECONDS);
				liberar.countDown();
				for (var tarea : tareas) {
					assertTrue(tarea.get(3, TimeUnit.SECONDS).isEmpty());
				}
				assertTrue(servicio.buscar("lugar").isEmpty());
			} finally {
				liberar.countDown();
			}
		}
	}

	@Test
	void rechazaUtf8InvalidoEnTransporteReal() throws Exception {
		HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/", intercambio -> {
			var contenido = new ByteArrayOutputStream();
			contenido.writeBytes("{\"features\":[{\"properties\":{\"name\":\""
					.getBytes(StandardCharsets.UTF_8));
			if (intercambio.getRequestURI().getPath().equals("/valido")) {
				contenido.writeBytes("é(".getBytes(StandardCharsets.UTF_8));
			} else {
				contenido.writeBytes(new byte[] {(byte) 0xc3, (byte) 0x28});
			}
			contenido.writeBytes("\"},\"geometry\":{\"coordinates\":[-75,6]}}]}"
					.getBytes(StandardCharsets.UTF_8));
			byte[] datos = contenido.toByteArray();
			intercambio.sendResponseHeaders(200, datos.length);
			try (var salida = intercambio.getResponseBody()) {
				salida.write(datos);
			}
		});
		servidor.start();
		try (HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
			var transporte = PhotonGeocodificador.crearTransporte(cliente, Duration.ofSeconds(3), 100);
			URI local = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/");
			var servicio = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> transporte.enviar(local));
			var fallo = assertThrows(GeocodificacionException.class, () -> servicio.buscar("lugar"));
			Throwable causa = fallo;
			while (causa != null && !(causa instanceof CharacterCodingException)) {
				causa = causa.getCause();
			}
			assertInstanceOf(CharacterCodingException.class, causa);
			URI valida = local.resolve("/valido");
			var servicioValido = new PhotonGeocodificador(URI_PHOTON, 6, -75, uri -> transporte.enviar(valida));
			assertEquals(List.of(new DireccionGeocodificada("é(", 6, -75)), servicioValido.buscar("lugar"));
		} finally {
			servidor.stop(0);
		}
	}

	@Test
	void transporteRealLimitaCuerpoYTiempoTotal() throws Exception {
		CountDownLatch cabeceras = new CountDownLatch(1);
		CountDownLatch liberar = new CountDownLatch(1);
		HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/grande", intercambio -> {
			byte[] cuerpo = new byte[4096];
			intercambio.sendResponseHeaders(200, cuerpo.length);
			try (var salida = intercambio.getResponseBody()) {
				salida.write(cuerpo);
			} catch (IOException ignorada) {
				// El cliente puede cerrar al superar el límite.
			}
		});
		servidor.createContext("/lento", intercambio -> {
			intercambio.sendResponseHeaders(200, 0);
			try (var salida = intercambio.getResponseBody()) {
				salida.write('a');
				salida.flush();
				cabeceras.countDown();
				liberar.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (IOException ignorada) {
				// El cliente puede cerrar después de agotar el plazo.
			}
		});
		servidor.start();
		try (HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
			String base = "http://127.0.0.1:" + servidor.getAddress().getPort();
			var limitado = PhotonGeocodificador.crearTransporte(cliente, Duration.ofSeconds(3), 100);
			assertThrows(IOException.class, () -> limitado.enviar(URI.create(base + "/grande")));
			var lento = PhotonGeocodificador.crearTransporte(cliente, Duration.ofMillis(300), 100);
			assertThrows(IOException.class, () -> lento.enviar(URI.create(base + "/lento")));
			assertEquals(0, cabeceras.getCount());
		} finally {
			liberar.countDown();
			servidor.stop(0);
		}
	}
}
