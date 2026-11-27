-- Requiere el esquema metria creado previamente y PostGIS instalado en public.
CREATE TABLE metria.estacion (
    id text PRIMARY KEY,
    nombre text NOT NULL,
    coordenada public.geometry(Point, 4326) NOT NULL,
    CHECK (NOT public.ST_IsEmpty(coordenada)),
    CHECK (public.ST_X(coordenada) BETWEEN -180 AND 180),
    CHECK (public.ST_Y(coordenada) BETWEEN -90 AND 90)
);

CREATE TABLE metria.punto_interes (
    id text PRIMARY KEY,
    nombre text NOT NULL,
    descripcion text,
    coordenada public.geometry(Point, 4326) NOT NULL,
    CHECK (NOT public.ST_IsEmpty(coordenada)),
    CHECK (public.ST_X(coordenada) BETWEEN -180 AND 180),
    CHECK (public.ST_Y(coordenada) BETWEEN -90 AND 90)
);
