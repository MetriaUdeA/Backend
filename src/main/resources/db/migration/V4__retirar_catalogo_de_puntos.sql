-- No eliminar datos existentes sin una decisión explícita.
LOCK TABLE metria.punto_interes IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM metria.punto_interes) THEN
        RAISE EXCEPTION 'No se puede retirar punto_interes: la tabla contiene datos';
    END IF;
END
$$;
DROP TABLE metria.punto_interes;
