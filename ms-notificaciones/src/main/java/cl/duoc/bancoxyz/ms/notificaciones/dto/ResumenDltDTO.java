package cl.duoc.bancoxyz.ms.notificaciones.dto;

import java.util.List;
import java.util.Map;

import cl.duoc.bancoxyz.ms.notificaciones.models.MensajeDlt;

public record ResumenDltDTO(
        long totalMensajes,
        long mensajesEnVentana,
        int umbralAlerta,
        long ventanaSegundos,
        boolean alertaCriticaActiva,
        Map<String, Long> mensajesPorTopico,
        List<MensajeDlt> ultimosMensajes) {
}
