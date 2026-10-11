package cl.duocuc.bancoxyz.batch.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

public final class FechaLegacyUtil {

    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private FechaLegacyUtil() {
    }

    public static LocalDate parsear(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignorada) {
                continue;
            }
        }
        return null;
    }
}
