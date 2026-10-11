package cl.duocuc.bancoxyz.batch.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;

public final class ArchivoCsvUtil {

    private ArchivoCsvUtil() {
    }

    public static int contarRegistros(Resource archivo) {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            long registros = lector.lines().skip(1).filter(linea -> !linea.isBlank()).count();
            return (int) registros;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo legacy " + archivo, e);
        }
    }
}
