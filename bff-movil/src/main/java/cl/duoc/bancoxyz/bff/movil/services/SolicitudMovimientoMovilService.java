package cl.duoc.bancoxyz.bff.movil.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import cl.duoc.bancoxyz.bff.movil.clients.MsPagosClient;
import cl.duoc.bancoxyz.bff.movil.dtos.RespuestaSolicitud;
import cl.duoc.bancoxyz.bff.movil.dtos.SolicitudMovimientoRequest;
import cl.duoc.bancoxyz.bff.movil.dtos.SolicitudMovimientoMovilDTO;
import cl.duoc.bancoxyz.bff.movil.dtos.core.TransaccionCoreDTO;
import cl.duoc.bancoxyz.bff.movil.events.SolicitudIngresadaEvent;
import cl.duoc.bancoxyz.bff.movil.exceptions.SolicitudRechazadaException;
import cl.duoc.bancoxyz.bff.movil.messaging.SolicitudPublisher;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudMovimientoMovilService {

    private static final String CANAL = "MOVIL";
    private static final String TRANSFERENCIA = "TRANSFERENCIA";
    private static final String PAGO = "PAGO";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String NO_REGISTRADA = "NO_REGISTRADA";
    private static final String DESCONOCIDO = "DESCONOCIDO";
    private static final String EN_COLA = "EN_COLA";
    private static final Set<String> TIPOS_VALIDOS = Set.of(TRANSFERENCIA, PAGO);

    private final MsPagosClient msPagosClient;
    private final SolicitudPublisher solicitudPublisher;

    @CircuitBreaker(name = "kafkaBroker", fallbackMethod = "solicitarFallback")
    public RespuestaSolicitud solicitar(Long cuentaId, SolicitudMovimientoRequest solicitud, Jwt token)
            throws Exception {
        validar(cuentaId, solicitud);
        String tipo = solicitud.tipoMovimiento().trim().toUpperCase(Locale.ROOT);
        String clienteOAuth = token.getAudience().isEmpty() ? null : token.getAudience().get(0);
        String usuario = token.getSubject().equals(clienteOAuth) ? null : token.getSubject();
        String solicitudId = UUID.randomUUID().toString();

        SolicitudIngresadaEvent evento = new SolicitudIngresadaEvent(
                UUID.randomUUID().toString(), "SOLICITUD_INGRESADA", Instant.now(), solicitudId, cuentaId,
                tipo, solicitud.monto(), CANAL, solicitud.descripcion(), solicitud.cuentaDestinoId(),
                solicitud.referenciaPago(), clienteOAuth, usuario);
        solicitudPublisher.publicar(evento);

        log.info("[MOVIL] Solicitud {} ({} {} desde la cuenta {}) dejada en Kafka, se responde 202",
                solicitudId, tipo, solicitud.monto(), cuentaId);
        SolicitudMovimientoMovilDTO cuerpo = new SolicitudMovimientoMovilDTO(solicitudId, tipo, solicitud.monto(),
                PENDIENTE, "Solicitud recibida, se esta procesando");
        return new RespuestaSolicitud(HttpStatus.ACCEPTED, cuerpo);
    }

    @CircuitBreaker(name = "msPagos", fallbackMethod = "consultarFallback")
    public SolicitudMovimientoMovilDTO consultar(String solicitudId) {
        TransaccionCoreDTO core;
        try {
            core = msPagosClient.obtenerTransaccion(solicitudId);
        } catch (SolicitudRechazadaException ex) {
            if (ex.getEstadoHttp() == HttpStatus.NOT_FOUND.value()) {
                return sinRespuesta(solicitudId, EN_COLA, "La solicitud esta en cola: ms-pagos todavia no la registra");
            }
            throw ex;
        }
        return adaptar(core);
    }

    @SuppressWarnings("unused")
    private RespuestaSolicitud solicitarFallback(Long cuentaId, SolicitudMovimientoRequest solicitud, Jwt token,
            Throwable error) {
        if (error instanceof SolicitudRechazadaException rechazada) {
            throw rechazada;
        }
        log.warn("[FALLBACK] Solicitud NO registrada para la cuenta {}: Kafka no esta disponible. Causa: {}",
                cuentaId, error.toString());
        SolicitudMovimientoMovilDTO cuerpo = new SolicitudMovimientoMovilDTO(null, solicitud.tipoMovimiento(),
                solicitud.monto(), NO_REGISTRADA,
                "No se pudo registrar la solicitud en este momento y no se hizo ningun cargo. Intenta mas tarde");
        return new RespuestaSolicitud(HttpStatus.SERVICE_UNAVAILABLE, cuerpo);
    }

    @SuppressWarnings("unused")
    private SolicitudMovimientoMovilDTO consultarFallback(String solicitudId, Throwable error) {
        if (error instanceof SolicitudRechazadaException rechazada) {
            throw rechazada;
        }
        log.warn("[FALLBACK] No se pudo consultar la solicitud {}. Causa: {}", solicitudId, error.toString());
        return sinRespuesta(solicitudId, DESCONOCIDO, "No se pudo consultar el estado en este momento, intenta mas tarde");
    }

    private void validar(Long cuentaId, SolicitudMovimientoRequest solicitud) {
        if (solicitud == null || solicitud.tipoMovimiento() == null
                || !TIPOS_VALIDOS.contains(solicitud.tipoMovimiento().trim().toUpperCase(Locale.ROOT))) {
            throw new SolicitudRechazadaException(HttpStatus.BAD_REQUEST.value(),
                    "En el canal movil tipoMovimiento debe ser TRANSFERENCIA o PAGO");
        }
        if (solicitud.monto() == null || solicitud.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SolicitudRechazadaException(HttpStatus.BAD_REQUEST.value(), "El monto debe ser mayor que cero");
        }
        String tipo = solicitud.tipoMovimiento().trim().toUpperCase(Locale.ROOT);
        if (TRANSFERENCIA.equals(tipo) && (solicitud.cuentaDestinoId() == null || solicitud.cuentaDestinoId().equals(cuentaId))) {
            throw new SolicitudRechazadaException(HttpStatus.BAD_REQUEST.value(),
                    "La transferencia necesita cuentaDestinoId distinta a la cuenta de origen");
        }
        if (PAGO.equals(tipo) && (solicitud.referenciaPago() == null || solicitud.referenciaPago().isBlank())) {
            throw new SolicitudRechazadaException(HttpStatus.BAD_REQUEST.value(),
                    "El pago necesita referenciaPago (por ejemplo el numero de la boleta)");
        }
    }

    private String mensajePara(TransaccionCoreDTO core) {
        return switch (core.estado()) {
            case "PENDIENTE" -> "Solicitud recibida, se esta procesando";
            case "APLICADA" -> "Operacion aplicada";
            case "RECHAZADA" -> "Operacion rechazada: " + core.motivo();
            default -> core.estado();
        };
    }

    private SolicitudMovimientoMovilDTO adaptar(TransaccionCoreDTO core) {
        return new SolicitudMovimientoMovilDTO(core.solicitudId(), core.tipo(), core.monto(), core.estado(),
                mensajePara(core));
    }

    private SolicitudMovimientoMovilDTO sinRespuesta(String solicitudId, String estado, String mensaje) {
        return new SolicitudMovimientoMovilDTO(solicitudId, null, null, estado, mensaje);
    }
}
