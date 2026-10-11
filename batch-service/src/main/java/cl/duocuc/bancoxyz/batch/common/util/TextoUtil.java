package cl.duocuc.bancoxyz.batch.common.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextoUtil {

    private TextoUtil() {
    }

    public static String truncar(String texto, int largoMaximo) {
        if (texto == null) {
            return null;
        }
        if (texto.length() <= largoMaximo) {
            return texto;
        }
        return texto.substring(0, largoMaximo - 3) + "...";
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
