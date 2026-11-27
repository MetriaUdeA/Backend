package com.udea.metria.geocodificacion;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador de Photon. La URI HTTPS y un punto de referencia aproximado del
 * Valle de Aburrá se proporcionan desde la configuración de la aplicación.
 * El punto solo prioriza resultados: no limita geográficamente la búsqueda.
 */
public class PhotonGeocodificador implements ServicioGeocodificacion {
	private static final int LIMITE = 8;
	private static final int MAXIMO_TEXTO = 200;
	private static final int MAXIMO_RESPUESTA = 256 * 1024;
	private static final Duration TIEMPO_CONEXION = Duration.ofSeconds(3);
	private static final Duration TIEMPO_LECTURA = Duration.ofSeconds(5);
	private final URI uri;
	private final double latitud;
	private final double longitud;
	private final Transporte transporte;
	private final Semaphore cupos = new Semaphore(4);
	private final ObjectMapper mapper = new ObjectMapper();

	public PhotonGeocodificador(URI uri, double latitud, double longitud) {
		this(uri, latitud, longitud, crearTransporte());
	}

	PhotonGeocodificador(URI uri, double latitud, double longitud, Transporte transporte) {
		if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
				|| uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
			throw new IllegalArgumentException("La URI de Photon debe ser HTTPS, sin credenciales, consulta ni fragmento");
		}
		if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90
				|| !Double.isFinite(longitud) || longitud < -180 || longitud > 180 || transporte == null) {
			throw new IllegalArgumentException("Configuración de geocodificación inválida");
		}
		this.uri = uri;
		this.latitud = latitud;
		this.longitud = longitud;
		this.transporte = transporte;
	}

	@Override
	public List<DireccionGeocodificada> buscar(String texto) {
		if (texto == null || texto.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("El texto de búsqueda es obligatorio");
		}
		texto = Normalizer.normalize(texto.strip(), Normalizer.Form.NFC);
		int longitudTexto = texto.codePointCount(0, texto.length());
		if (longitudTexto < 3 || longitudTexto > MAXIMO_TEXTO) {
			throw new IllegalArgumentException("El texto de búsqueda es inválido");
		}
		URI consulta = URI.create(uri.toASCIIString() + "?q="
				+ URLEncoder.encode(texto, StandardCharsets.UTF_8) + "&lat=" + latitud
				+ "&lon=" + longitud + "&limit=" + LIMITE);
		if (!cupos.tryAcquire()) {
			throw new GeocodificacionException("Capacidad de geocodificación agotada");
		}
		try {
			Respuesta respuesta = transporte.enviar(consulta);
			if (respuesta.estado() != 200) {
				throw new GeocodificacionException("Photon no pudo atender la búsqueda");
			}
			JsonNode raiz = mapper.readTree(respuesta.cuerpo());
			JsonNode elementos = raiz == null ? null : raiz.get("features");
			if (elementos == null || !elementos.isArray()) {
				throw new GeocodificacionException("Respuesta inválida de Photon");
			}
			List<DireccionGeocodificada> direcciones = new ArrayList<>();
			for (JsonNode elemento : elementos) {
				JsonNode propiedades = elemento.path("properties");
				JsonNode nombre = propiedades.path("name");
				String etiqueta = nombre.isString() ? limpiarEtiqueta(nombre.asString()) : "";
				if (etiqueta.isEmpty()) {
					JsonNode calle = propiedades.path("street");
					JsonNode numero = propiedades.path("housenumber");
					if (calle.isString()) {
						etiqueta = limpiarEtiqueta(calle.asString());
						String numeroLimpio = numero.isString() ? limpiarEtiqueta(numero.asString()) : "";
						if (!etiqueta.isEmpty() && !numeroLimpio.isEmpty()) {
							etiqueta += " " + numeroLimpio;
						}
					}
				}
				JsonNode coordenadas = elemento.path("geometry").path("coordinates");
				if (etiqueta.isEmpty() || !coordenadas.isArray()
						|| coordenadas.size() < 2 || !coordenadas.get(0).isNumber()
						|| !coordenadas.get(1).isNumber()) {
					continue;
				}
				double lon = coordenadas.get(0).asDouble();
				double lat = coordenadas.get(1).asDouble();
				if (!Double.isFinite(lat) || lat < -90 || lat > 90
						|| !Double.isFinite(lon) || lon < -180 || lon > 180) {
					continue;
				}
				direcciones.add(new DireccionGeocodificada(etiqueta, lat, lon));
				if (direcciones.size() == LIMITE) {
					break;
				}
			}
			if (!elementos.isEmpty() && direcciones.isEmpty()) {
				throw new GeocodificacionException("Respuesta inválida de Photon");
			}
			return List.copyOf(direcciones);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GeocodificacionException("Consulta a Photon interrumpida", e);
		} catch (IOException | RuntimeException e) {
			if (e instanceof GeocodificacionException fallo) {
				throw fallo;
			}
			throw new GeocodificacionException("No fue posible consultar Photon", e);
		} finally {
			cupos.release();
		}
	}

	private static String limpiarEtiqueta(String texto) {
		int inicio = 0;
		int fin = texto.length();
		while (inicio < fin) {
			int punto = texto.codePointAt(inicio);
			if (!Character.isWhitespace(punto) && !Character.isSpaceChar(punto)) {
				break;
			}
			inicio += Character.charCount(punto);
		}
		while (fin > inicio) {
			int punto = texto.codePointBefore(fin);
			if (!Character.isWhitespace(punto) && !Character.isSpaceChar(punto)) {
				break;
			}
			fin -= Character.charCount(punto);
		}
		return texto.substring(inicio, fin);
	}

	private static Transporte crearTransporte() {
		HttpClient cliente = HttpClient.newBuilder().connectTimeout(TIEMPO_CONEXION)
				.followRedirects(HttpClient.Redirect.NEVER).build();
		return crearTransporte(cliente, TIEMPO_LECTURA, MAXIMO_RESPUESTA);
	}

	// Se inyectan cliente y límites para probar el transporte sin servicios externos.
	static Transporte crearTransporte(HttpClient cliente, Duration plazo, int maximoBytes) {
		return uri -> {
			HttpRequest solicitud = HttpRequest.newBuilder(uri).timeout(plazo).GET().build();
			CuerpoLimitado cuerpo = new CuerpoLimitado(maximoBytes);
			CompletableFuture<HttpResponse<String>> futuro = cliente.sendAsync(solicitud, info -> cuerpo);
			try {
				HttpResponse<String> respuesta = futuro.get(plazo.toNanos(), TimeUnit.NANOSECONDS);
				return new Respuesta(respuesta.statusCode(), respuesta.body());
			} catch (TimeoutException e) {
				throw new IOException("Tiempo de respuesta de Photon agotado", e);
			} catch (ExecutionException e) {
				throw new IOException("Error en respuesta de Photon", e.getCause());
			} finally {
				cuerpo.cancelar();
				futuro.cancel(true);
			}
		};
	}

	private static final class CuerpoLimitado implements HttpResponse.BodySubscriber<String> {
		private final int maximo;
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private final CompletableFuture<String> resultado = new CompletableFuture<>();
		private Flow.Subscription suscripcion;
		private boolean cancelado;

		CuerpoLimitado(int maximo) {
			this.maximo = maximo;
		}

		@Override
		public CompletionStage<String> getBody() {
			return resultado;
		}

		@Override
		public synchronized void onSubscribe(Flow.Subscription suscripcion) {
			if (cancelado || this.suscripcion != null) {
				suscripcion.cancel();
				return;
			}
			this.suscripcion = suscripcion;
			suscripcion.request(1);
		}

		@Override
		public synchronized void onNext(List<ByteBuffer> partes) {
			if (cancelado) {
				return;
			}
			for (ByteBuffer parte : partes) {
				if (parte.remaining() > maximo - bytes.size()) {
					cancelar();
					return;
				}
				byte[] bloque = new byte[parte.remaining()];
				parte.get(bloque);
				bytes.writeBytes(bloque);
			}
			suscripcion.request(1);
		}

		@Override
		public void onError(Throwable error) {
			resultado.completeExceptionally(error);
		}

		@Override
		public synchronized void onComplete() {
			if (!cancelado) {
				try {
					resultado.complete(StandardCharsets.UTF_8.newDecoder()
							.onMalformedInput(CodingErrorAction.REPORT)
							.onUnmappableCharacter(CodingErrorAction.REPORT)
							.decode(ByteBuffer.wrap(bytes.toByteArray())).toString());
				} catch (CharacterCodingException e) {
					resultado.completeExceptionally(e);
				}
			}
		}

		synchronized void cancelar() {
			cancelado = true;
			if (suscripcion != null) {
				suscripcion.cancel();
			}
			resultado.completeExceptionally(new IOException("Lectura de Photon cancelada o demasiado grande"));
		}
	}

	@FunctionalInterface
	interface Transporte {
		Respuesta enviar(URI uri) throws IOException, InterruptedException;
	}

	record Respuesta(int estado, String cuerpo) {
	}
}
