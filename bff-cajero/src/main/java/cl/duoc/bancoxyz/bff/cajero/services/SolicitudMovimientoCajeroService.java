package cl.duoc.bancoxyz.bff.cajero.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import cl.duoc.bancoxyz.bff.cajero.clients.MsPagosClient;
import cl.duoc.bancoxyz.bff.cajero.dtos.RespuestaSolicitud;
import cl.duoc.bancoxyz.bff.cajero.dtos.SolicitudMovimientoRequest;
import cl.duoc.bancoxyz.bff.cajero.dtos.ComprobanteCajeroDTO;
import cl.duoc.bancoxyz.bff.cajero.dtos.core.TransaccionCoreDTO;
import cl.duoc.bancoxyz.bff.cajero.events.SolicitudIngresadaEvent;
import cl.duoc.bancoxyz.bff.cajero.exceptions.SolicitudRechazadaException;
import cl.duoc.bancoxyz.bff.cajero.messaging.SolicitudPublisher;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudMovimientoCajeroService {

    private static final String CANAL = "CAJERO";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String NO_REGISTRADA = "NO_REGISTRADA";
    private static final String DESCONOCIDO = "DESCONOCIDO";
    private static final String EN_COLA = "EN_COLA";
    private static final Set<String> TIPOS_VALIDOS = Set.of("RETIRO", "DEPOSITO");

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
                tipo, solicitud.monto(), CANAL, solicitud.descripcion(), null, null, clienteOAuth, usuario);
        solicitudPublisher.publicar(evento);

        log.info("[CAJERO] Solicitud {} ({} {} en la cuenta {}) dejada en Kafka, se responde 202",
                solicitudId, tipo, solicitud.monto(), cuentaId);
        ComprobanteCajeroDTO cuerpo = new ComprobanteCajeroDTO(solicitudId, PENDIENTE, solicitud.monto(), null,
                "Solicitud recibida, se esta procesando");
        return new RespuestaSolicitud(HttpStatus.ACCEPTED, cuerpo);
    }

    @CircuitBreaker(name = "msPagos", fallbackMethod = "consultarFallback")
    public ComprobanteCajeroDTO consultar(String solicitudId) {
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
        ComprobanteCajeroDTO cuerpo = new ComprobanteCajeroDTO(null, NO_REGISTRADA, solicitud.monto(), null,
                "No se pudo registrar la solicitud en este momento y no se hizo ningun cargo. Intenta mas tarde");
        return new RespuestaSolicitud(HttpStatus.SERVICE_UNAVAILABLE, cuerpo);
    }

    @SuppressWarnings("unused")
    private ComprobanteCajeroDTO consultarFallback(String solicitudId, Throwable error) {
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
                    "En el cajero tipoMovimiento debe ser RETIRO o DEPOSITO");
        }
        if (solicitud.monto() == null || solicitud.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SolicitudRechazadaException(HttpStatus.BAD_REQUEST.value(), "El monto debe ser mayor que cero");
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

    private ComprobanteCajeroDTO adaptar(TransaccionCoreDTO core) {
        return new ComprobanteCajeroDTO(core.solicitudId(), core.estado(), core.monto(), core.saldoFinal(),
                mensajePara(core));
    }

    private ComprobanteCajeroDTO sinRespuesta(String solicitudId, String estado, String mensaje) {
        return new ComprobanteCajeroDTO(solicitudId, estado, null, null, mensaje);
    }
}
