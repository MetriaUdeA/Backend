CREATE TABLE metria.linea_ruta (
    id text PRIMARY KEY,
    duracion_min double precision NOT NULL CHECK (duracion_min > 0),
    trazado public.geometry(LineString, 4326) NOT NULL,
    CHECK (NOT public.ST_IsEmpty(trazado))
);

CREATE TABLE metria.linea_estacion (
    linea_id text NOT NULL REFERENCES metria.linea_ruta(id),
    estacion_id text NOT NULL REFERENCES metria.estacion(id),
    posicion integer NOT NULL CHECK (posicion > 0),
    PRIMARY KEY (linea_id, estacion_id),
    UNIQUE (linea_id, posicion),
    UNIQUE (estacion_id)
);

CREATE TABLE metria.transbordo (
    estacion_a text NOT NULL REFERENCES metria.linea_estacion(estacion_id),
    estacion_b text NOT NULL REFERENCES metria.linea_estacion(estacion_id),
    PRIMARY KEY (estacion_a, estacion_b),
    CHECK (estacion_a < estacion_b)
);
