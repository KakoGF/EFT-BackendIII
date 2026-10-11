package cl.duoc.bancoxyz.ms.notificaciones.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

import cl.duoc.bancoxyz.ms.notificaciones.clients.ProveedorNotificacionesClient;
import cl.duoc.bancoxyz.ms.notificaciones.events.AlertaSeguridadEvent;
import cl.duoc.bancoxyz.ms.notificaciones.events.CuentaEvent;
import cl.duoc.bancoxyz.ms.notificaciones.events.TransaccionCompletadaEvent;
import cl.duoc.bancoxyz.ms.notificaciones.models.EstadoNotificacion;
import cl.duoc.bancoxyz.ms.notificaciones.models.MensajeDlt;
import cl.duoc.bancoxyz.ms.notificaciones.models.Notificacion;
import cl.duoc.bancoxyz.ms.notificaciones.models.TipoNotificacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final ProveedorNotificacionesClient proveedorClient;

    private final List<Notificacion> notificaciones = new CopyOnWriteArrayList<>();
    private final Set<String> eventosProcesados = ConcurrentHashMap.newKeySet();

    public void notificarTransaccion(TransaccionCompletadaEvent evento) {
        if (yaProcesado(evento.eventId())) {
            return;
        }
        Notificacion notificacion = nueva(evento.eventId(), TipoNotificacion.TRANSACCION, evento.solicitudId(),
                evento.cuentaOrigenId(), evento.titular(), evento.canal());
        notificacion.setMensaje(redactarTransaccion(evento));
        enviar(notificacion);
        log.info("[SAGA] Paso 5: transaccion {} {} notificada al cliente -> {}",
                evento.solicitudId(), evento.estado(), notificacion.getEstado());
    }

    public void notificarAlerta(AlertaSeguridadEvent evento) {
        if (yaProcesado(evento.eventId())) {
            return;
        }
        Notificacion notificacion = nueva(evento.eventId(), TipoNotificacion.ALERTA_SEGURIDAD, evento.solicitudId(),
                evento.cuentaId(), null, evento.canal());
        String accion = "ALTA".equals(evento.nivel())
                ? " Por seguridad tu cuenta quedo bloqueada hasta que un ejecutivo la revise."
                : " Si no reconoces esta operacion, comunicate con el banco.";
        notificacion.setMensaje("Alerta de seguridad en tu cuenta " + evento.cuentaId() + ": " + evento.tipoAlerta()
                + " (" + evento.detalle() + ")." + accion);
        enviar(notificacion);
        log.warn("[ALERTA] Alerta {} de la cuenta {} notificada -> {}", evento.tipoAlerta(), evento.cuentaId(),
                notificacion.getEstado());
    }

    public void notificarCuenta(CuentaEvent evento) {
        if (yaProcesado(evento.eventId())) {
            return;
        }
        Notificacion notificacion = nueva(evento.eventId(), TipoNotificacion.CUENTA, null,
                evento.cuentaId(), evento.titular(), "SUCURSAL");
        notificacion.setMensaje(redactarCuenta(evento));
        enviar(notificacion);
        log.info("[CUENTAS] Evento {} de la cuenta {} notificado -> {}", evento.tipoEvento(), evento.cuentaId(),
                notificacion.getEstado());
    }

    public void notificarSoporteDlt(MensajeDlt mensaje, boolean critica, long mensajesEnVentana) {
        String eventId = "dlt-" + mensaje.topicoDlt() + "-" + mensaje.particion() + "-" + mensaje.offset();
        if (yaProcesado(eventId)) {
            return;
        }
        Notificacion notificacion = nueva(eventId, TipoNotificacion.ALERTA_DLT, null, null, "equipo de soporte",
                "SOPORTE");
        String nivel = critica ? "CRITICA" : "AVISO";
        notificacion.setMensaje("[" + nivel + "] Mensaje no procesado en " + mensaje.topicoOriginal()
                + " por el grupo " + mensaje.grupoConsumidor() + ": " + mensaje.tipoError() + " ("
                + mensaje.detalleError() + "). Quedo guardado en " + mensaje.topicoDlt() + ". Mensajes en DLT en la"
                + " ventana actual: " + mensajesEnVentana + ".");
        enviar(notificacion);
        log.warn("[DLT] Aviso {} enviado a soporte -> {}", nivel, notificacion.getEstado());
    }

    public List<Notificacion> listarNotificaciones() {
        return new ArrayList<>(notificaciones);
    }

    private boolean yaProcesado(String eventId) {
        if (!eventosProcesados.add(eventId)) {
            log.warn("[DUPLICADO] Evento {} ya notificado: se descarta", eventId);
            return true;
        }
        return false;
    }

    private Notificacion nueva(String eventId, TipoNotificacion tipo, String solicitudId, Long cuentaId,
            String nombreCliente, String canal) {
        Notificacion notificacion = new Notificacion();
        notificacion.setNotificacionId(UUID.randomUUID().toString());
        notificacion.setEventId(eventId);
        notificacion.setTipo(tipo);
        notificacion.setSolicitudId(solicitudId);
        notificacion.setCuentaId(cuentaId);
        notificacion.setNombreCliente(nombreCliente == null ? "cliente" : nombreCliente);
        notificacion.setCanal(canal);
        notificacion.setFechaCreacion(LocalDateTime.now());
        return notificacion;
    }

    private void enviar(Notificacion notificacion) {
        boolean enviada = proveedorClient.enviar(notificacion);
        if (enviada) {
            notificacion.setEstado(EstadoNotificacion.ENVIADA);
            notificacion.setFechaEnvio(LocalDateTime.now());
        } else {
            notificacion.setEstado(EstadoNotificacion.PENDIENTE);
        }
        notificaciones.add(0, notificacion);
    }

    private String redactarTransaccion(TransaccionCompletadaEvent evento) {
        String nombre = evento.titular() == null ? "cliente" : evento.titular();
        String operacion = switch (evento.tipoTransaccion()) {
            case "RETIRO" -> "retiro";
            case "DEPOSITO" -> "deposito";
            case "PAGO" -> "pago";
            case "TRANSFERENCIA" -> "transferencia a la cuenta " + evento.cuentaDestinoId();
            default -> evento.tipoTransaccion().toLowerCase();
        };
        if ("APLICADA".equals(evento.estado())) {
            String saldo = evento.saldoFinal() == null ? "" : " Saldo actual: $" + evento.saldoFinal().toPlainString();
            return "Hola " + nombre + ", tu " + operacion + " de $" + evento.monto().toPlainString()
                    + " por canal " + evento.canal() + " fue aplicado." + saldo;
        }
        return "Hola " + nombre + ", tu " + operacion + " de $" + evento.monto().toPlainString()
                + " por canal " + evento.canal() + " fue rechazado (" + evento.motivo() + "). No se hizo ningun cargo.";
    }

    private String redactarCuenta(CuentaEvent evento) {
        return switch (evento.tipoEvento()) {
            case "CUENTA_ABIERTA" -> "Hola " + evento.titular() + ", tu nueva cuenta " + evento.tipo() + " numero "
                    + evento.cuentaId() + " ya esta activa.";
            case "CUENTA_BLOQUEADA" -> "Hola " + evento.titular() + ", tu cuenta " + evento.cuentaId()
                    + " fue bloqueada: " + evento.motivo() + ".";
            case "CUENTA_DESBLOQUEADA" -> "Hola " + evento.titular() + ", tu cuenta " + evento.cuentaId()
                    + " fue desbloqueada y ya puedes operar.";
            case "CUENTA_CERRADA" -> "Hola " + evento.titular() + ", tu cuenta " + evento.cuentaId() + " fue cerrada.";
            default -> "Hola " + evento.titular() + ", hubo un cambio en tu cuenta " + evento.cuentaId() + ".";
        };
    }
}
