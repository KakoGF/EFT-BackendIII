package cl.duocuc.bancoxyz.batch.common.util;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class MotivosUtil {

    private static final String PREFIJO = "SKIP: ";

    private MotivosUtil() {
    }

    public static String motivoDeOmision(Throwable t) {
        if (t instanceof DuplicateKeyException) {
            return PREFIJO + "DUPLICADO: el registro ya existe en la base de datos";
        }
        return PREFIJO + t.getMessage();
    }

    public static String codigo(String motivo) {
        if (motivo == null) {
            return "SIN_MOTIVO";
        }
        String texto = motivo.startsWith(PREFIJO) ? motivo.substring(PREFIJO.length()) : motivo;
        int separador = texto.indexOf(':');
        return separador > 0 ? texto.substring(0, separador).trim() : texto.trim();
    }

    public static Map<String, Long> contarPorCodigo(JdbcTemplate jdbcTemplate, String tabla) {
        List<String> motivos = jdbcTemplate.queryForList("SELECT motivo FROM " + tabla, String.class);
        Map<String, Long> conteo = new TreeMap<>();
        for (String motivo : motivos) {
            conteo.merge(codigo(motivo), 1L, Long::sum);
        }
        return conteo;
    }
}
