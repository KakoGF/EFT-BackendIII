package cl.duoc.bancoxyz.ms.pagos.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.bancoxyz.ms.pagos.clients.MsCuentasClient;
import cl.duoc.bancoxyz.ms.pagos.dtos.TransaccionDTO;
import cl.duoc.bancoxyz.ms.pagos.dtos.core.CuentaCoreDTO;
import cl.duoc.bancoxyz.ms.pagos.entities.EstadoTransaccion;
import cl.duoc.bancoxyz.ms.pagos.entities.EventoProcesadoEntity;
import cl.duoc.bancoxyz.ms.pagos.entities.TransaccionEntity;
import cl.duoc.bancoxyz.ms.pagos.events.AjusteCuentaResultadoEvent;
import cl.duoc.bancoxyz.ms.pagos.events.AjusteCuentaSolicitadoEvent;
import cl.duoc.bancoxyz.ms.pagos.events.SolicitudIngresadaEvent;
import cl.duoc.bancoxyz.ms.pagos.events.TransaccionCompletadaEvent;
import cl.duoc.bancoxyz.ms.pagos.events.TransaccionSolicitadaEvent;
import cl.duoc.bancoxyz.ms.pagos.events.TransaccionValidadaEvent;
import cl.duoc.bancoxyz.ms.pagos.exceptions.CuentaNoEncontradaException;
import cl.duoc.bancoxyz.ms.pagos.exceptions.TransaccionNoEncontradaException;
import cl.duoc.bancoxyz.ms.pagos.messaging.EventoPublisher;
import cl.duoc.bancoxyz.ms.pagos.repositories.EventoProcesadoRepository;
import cl.duoc.bancoxyz.ms.pagos.repositories.TransaccionRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TransaccionService {

    private static final String TRANSFERENCIA = "TRANSFERENCIA";
    private static final String PAGO = "PAGO";
    private static final String APROBADO = "APROBADO";
    private static final String APLICADO = "APLICADO";
    private static final String CUENTA_ACTIVA = "ACTIVA";
    private static final String TIPO_CUENTA_DESCONOCIDO = "DESCONOCIDO";
    private static final Map<String, Set<String>> OPERACIONES_POR_CANAL = Map.of(
            "WEB", Set.of(TRANSFERENCIA, PAGO),
            "MOVIL", Set.of(TRANSFERENCIA, PAGO),
            "CAJERO", Set.of("RETIRO", "DEPOSITO"));

    private final TransaccionRepository transaccionRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final MsCuentasClient msCuentasClient;
    private final EventoPublisher eventoPublisher;
    private final String topicoSolicitadas;
    private final String topicoAjustes;
    private final String topicoCompletadas;

    public TransaccionService(
            TransaccionRepository transaccionRepository,
            EventoProcesadoRepository eventoProcesadoRepository,
            MsCuentasClient msCuentasClient,
            EventoPublisher eventoPublisher,
            @Value("${topicos.transacciones-solicitadas}") String topicoSolicitadas,
            @Value("${topicos.cuentas-ajustes}") String topicoAjustes,
            @Value("${topicos.transacciones-completadas}") String topicoCompletadas) {
        this.transaccionRepository = transaccionRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
        this.msCuentasClient = msCuentasClient;
        this.eventoPublisher = eventoPublisher;
        this.topicoSolicitadas = topicoSolicitadas;
        this.topicoAjustes = topicoAjustes;
        this.topicoCompletadas = topicoCompletadas;
    }

    @Transactional(rollbackFor = Exception.class)
    public void registrar(SolicitudIngresadaEvent evento) throws Exception {
        if (eventoProcesadoRepository.existsById(evento.eventId())
                || transaccionRepository.findBySolicitudId(evento.solicitudId()).isPresent()) {
            log.warn("[DUPLICADO] Solicitud {} (evento {}) ya registrada antes: se descarta",
                    evento.solicitudId(), evento.eventId());
            return;
        }

        TransaccionEntity transaccion = new TransaccionEntity();
        transaccion.setSolicitudId(evento.solicitudId());
        transaccion.setTipo(normalizar(evento.tipoMovimiento()));
        transaccion.setCuentaOrigenId(evento.cuentaId());
        transaccion.setCuentaDestinoId(evento.cuentaDestinoId());
        transaccion.setReferenciaPago(evento.referenciaPago() == null ? null : evento.referenciaPago().trim());
        transaccion.setMonto(evento.monto());
        transaccion.setCanal(normalizar(evento.canal()));
        transaccion.setDescripcion(evento.descripcion());
        transaccion.setEstado(EstadoTransaccion.PENDIENTE);
        transaccion.setOrigen(origenDe(evento));
        transaccion.setFechaRegistro(LocalDateTime.now());

        String motivoInvalida = revisarDatos(transaccion);
        if (motivoInvalida != null) {
            rechazar(transaccion, motivoInvalida);
            registrarProcesado(evento.eventId(), evento.tipoEvento());
            return;
        }

        CuentaCoreDTO cuenta;
        try {
            cuenta = msCuentasClient.obtenerCuenta(evento.cuentaId());
        } catch (CuentaNoEncontradaException ex) {
            rechazar(transaccion, "CUENTA_NO_EXISTE: la cuenta " + evento.cuentaId() + " no existe");
            registrarProcesado(evento.eventId(), evento.tipoEvento());
            return;
        }

        String tipoCuenta = TIPO_CUENTA_DESCONOCIDO;
        BigDecimal saldoDisponible = null;
        if (cuenta != null) {
            if (!CUENTA_ACTIVA.equals(cuenta.estado())) {
                rechazar(transaccion, "CUENTA_" + cuenta.estado() + ": la cuenta " + cuenta.cuentaId()
                        + " no esta habilitada para operar");
                registrarProcesado(evento.eventId(), evento.tipoEvento());
                return;
            }
            transaccion.setClienteId(cuenta.clienteId());
            transaccion.setTitular(cuenta.titular());
            tipoCuenta = cuenta.tipo();
            saldoDisponible = cuenta.saldo();
        }

        transaccion = transaccionRepository.save(transaccion);
        registrarProcesado(evento.eventId(), evento.tipoEvento());

        TransaccionSolicitadaEvent solicitada = new TransaccionSolicitadaEvent(
                UUID.randomUUID().toString(), "TRANSACCION_SOLICITADA", Instant.now(),
                transaccion.getSolicitudId(), transaccion.getId(), transaccion.getCuentaOrigenId(),
                transaccion.getCuentaDestinoId(), transaccion.getClienteId(), transaccion.getTipo(),
                transaccion.getMonto(), transaccion.getCanal(), tipoCuenta, saldoDisponible);
        eventoPublisher.publicar(topicoSolicitadas, String.valueOf(transaccion.getCuentaOrigenId()), solicitada);

        log.info("[SAGA] Paso 1: {} {} de la cuenta {} (canal {}, origen {}) registrada PENDIENTE en ms-pagos",
                transaccion.getTipo(), transaccion.getMonto(), transaccion.getCuentaOrigenId(),
                transaccion.getCanal(), transaccion.getOrigen());
    }

    @Transactional(rollbackFor = Exception.class)
    public void procesarValidacion(TransaccionValidadaEvent evento) throws Exception {
        if (eventoProcesadoRepository.existsById(evento.eventId())) {
            log.warn("[DUPLICADO] Validacion {} ya procesada antes: se descarta", evento.eventId());
            return;
        }
        TransaccionEntity transaccion = transaccionRepository.findById(evento.transaccionId())
                .orElseThrow(() -> new TransaccionNoEncontradaException(evento.solicitudId()));
        registrarProcesado(evento.eventId(), evento.tipoEvento());

        if (transaccion.getEstado() != EstadoTransaccion.PENDIENTE) {
            log.warn("[DUPLICADO] La transaccion {} ya estaba {}: la validacion no cambia nada",
                    transaccion.getSolicitudId(), transaccion.getEstado());
            return;
        }
        transaccion.setValidadoPor(evento.validadoPor());

        if (!APROBADO.equals(evento.resultado())) {
            rechazar(transaccion, evento.motivo());
            return;
        }

        transaccionRepository.save(transaccion);
        AjusteCuentaSolicitadoEvent ajuste = new AjusteCuentaSolicitadoEvent(
                UUID.randomUUID().toString(), "AJUSTE_CUENTA_SOLICITADO", Instant.now(),
                transaccion.getSolicitudId(), transaccion.getId(), transaccion.getTipo(),
                transaccion.getCuentaOrigenId(), transaccion.getCuentaDestinoId(), transaccion.getMonto(),
                transaccion.getCanal(), descripcionPara(transaccion));
        eventoPublisher.publicar(topicoAjustes, String.valueOf(transaccion.getCuentaOrigenId()), ajuste);

        log.info("[SAGA] Paso 2: transaccion {} APROBADA por {}: se pide a ms-cuentas aplicar el {}",
                transaccion.getSolicitudId(), evento.validadoPor(), transaccion.getTipo());
    }

    @Transactional(rollbackFor = Exception.class)
    public void procesarResultadoAjuste(AjusteCuentaResultadoEvent evento) throws Exception {
        if (eventoProcesadoRepository.existsById(evento.eventId())) {
            log.warn("[DUPLICADO] Resultado de ajuste {} ya procesado antes: se descarta", evento.eventId());
            return;
        }
        TransaccionEntity transaccion = transaccionRepository.findBySolicitudId(evento.solicitudId())
                .orElseThrow(() -> new TransaccionNoEncontradaException(evento.solicitudId()));
        registrarProcesado(evento.eventId(), evento.tipoEvento());

        if (transaccion.getEstado() != EstadoTransaccion.PENDIENTE) {
            log.warn("[DUPLICADO] La transaccion {} ya estaba {}: el resultado no cambia nada",
                    transaccion.getSolicitudId(), transaccion.getEstado());
            return;
        }
        if (transaccion.getTitular() == null) {
            transaccion.setTitular(evento.titularOrigen());
        }

        if (APLICADO.equals(evento.resultado())) {
            transaccion.setEstado(EstadoTransaccion.APLICADA);
            transaccion.setSaldoFinal(evento.saldoOrigen());
            transaccion.setFechaResolucion(LocalDateTime.now());
            transaccionRepository.save(transaccion);
            publicarCompletada(transaccion);
            log.info("[SAGA] Paso 4: transaccion {} APLICADA, saldo final de la cuenta {} = {}",
                    transaccion.getSolicitudId(), transaccion.getCuentaOrigenId(), transaccion.getSaldoFinal());
        } else {
            log.info("[SAGA][COMPENSACION] ms-cuentas rechazo la transaccion {} ({}): se cierra como RECHAZADA sin mover dinero",
                    transaccion.getSolicitudId(), evento.motivo());
            transaccion.setSaldoFinal(evento.saldoOrigen());
            rechazar(transaccion, evento.motivo());
        }
    }

    @Transactional(readOnly = true)
    public TransaccionDTO obtener(String solicitudId) {
        return transaccionRepository.findBySolicitudId(solicitudId)
                .map(this::aDTO)
                .orElseThrow(() -> new TransaccionNoEncontradaException(solicitudId));
    }

    @Transactional(readOnly = true)
    public List<TransaccionDTO> listarPorCuenta(Long cuentaId) {
        return transaccionRepository.findByCuentaOrigenIdOrCuentaDestinoIdOrderByIdDesc(cuentaId, cuentaId).stream()
                .map(this::aDTO)
                .toList();
    }

    private String revisarDatos(TransaccionEntity transaccion) {
        Set<String> permitidas = OPERACIONES_POR_CANAL.get(transaccion.getCanal());
        if (permitidas == null) {
            return "SOLICITUD_INVALIDA: canal desconocido " + transaccion.getCanal();
        }
        if (transaccion.getTipo() == null || !permitidas.contains(transaccion.getTipo())) {
            return "OPERACION_NO_PERMITIDA_EN_CANAL: el canal " + transaccion.getCanal() + " solo permite " + permitidas;
        }
        if (transaccion.getMonto() == null || transaccion.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            return "SOLICITUD_INVALIDA: el monto debe ser mayor que cero";
        }
        if (TRANSFERENCIA.equals(transaccion.getTipo())
                && (transaccion.getCuentaDestinoId() == null
                || transaccion.getCuentaDestinoId().equals(transaccion.getCuentaOrigenId()))) {
            return "SOLICITUD_INVALIDA: la transferencia necesita una cuenta de destino distinta a la de origen";
        }
        if (PAGO.equals(transaccion.getTipo())
                && (transaccion.getReferenciaPago() == null || transaccion.getReferenciaPago().isBlank())) {
            return "SOLICITUD_INVALIDA: el pago necesita una referencia (por ejemplo el numero de la boleta)";
        }
        return null;
    }

    private void rechazar(TransaccionEntity transaccion, String motivo) throws Exception {
        transaccion.setEstado(EstadoTransaccion.RECHAZADA);
        transaccion.setMotivo(motivo);
        transaccion.setFechaResolucion(LocalDateTime.now());
        TransaccionEntity guardada = transaccionRepository.save(transaccion);
        publicarCompletada(guardada);
        log.info("[SAGA] Transaccion {} RECHAZADA ({})", guardada.getSolicitudId(), motivo);
    }

    private void publicarCompletada(TransaccionEntity transaccion) throws Exception {
        TransaccionCompletadaEvent completada = new TransaccionCompletadaEvent(
                UUID.randomUUID().toString(), "TRANSACCION_COMPLETADA", Instant.now(),
                transaccion.getSolicitudId(), transaccion.getId(), transaccion.getTipo(),
                transaccion.getCuentaOrigenId(), transaccion.getCuentaDestinoId(), transaccion.getClienteId(),
                transaccion.getTitular(), transaccion.getMonto(), transaccion.getCanal(),
                transaccion.getEstado().name(), transaccion.getMotivo(), transaccion.getSaldoFinal());
        eventoPublisher.publicar(topicoCompletadas, String.valueOf(transaccion.getCuentaOrigenId()), completada);
    }

    private String descripcionPara(TransaccionEntity transaccion) {
        if (transaccion.getDescripcion() != null && !transaccion.getDescripcion().isBlank()) {
            return transaccion.getDescripcion().trim();
        }
        if (PAGO.equals(transaccion.getTipo())) {
            return "Pago " + transaccion.getReferenciaPago();
        }
        return transaccion.getTipo() + " por canal " + transaccion.getCanal();
    }

    private void registrarProcesado(String eventId, String tipoEvento) {
        eventoProcesadoRepository.save(new EventoProcesadoEntity(eventId, tipoEvento, LocalDateTime.now()));
    }

    private String normalizar(String valor) {
        return valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
    }

    private String origenDe(SolicitudIngresadaEvent evento) {
        String app = evento.clienteOAuth() == null ? "desconocida" : evento.clienteOAuth();
        return evento.usuario() == null ? app : app + " / " + evento.usuario();
    }

    private TransaccionDTO aDTO(TransaccionEntity entity) {
        return new TransaccionDTO(entity.getSolicitudId(), entity.getId(), entity.getTipo(), entity.getCuentaOrigenId(),
                entity.getCuentaDestinoId(), entity.getReferenciaPago(), entity.getMonto(), entity.getCanal(),
                entity.getEstado().name(), entity.getMotivo(), entity.getSaldoFinal(), entity.getFechaRegistro(),
                entity.getFechaResolucion(), entity.getOrigen());
    }
}
