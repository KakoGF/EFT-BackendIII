package cl.duoc.bancoxyz.ms.cuentas.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.bancoxyz.ms.cuentas.dtos.AperturaCuentaRequest;
import cl.duoc.bancoxyz.ms.cuentas.dtos.CuentaDTO;
import cl.duoc.bancoxyz.ms.cuentas.dtos.MovimientoDTO;
import cl.duoc.bancoxyz.ms.cuentas.entities.CuentaEntity;
import cl.duoc.bancoxyz.ms.cuentas.entities.EstadoCuenta;
import cl.duoc.bancoxyz.ms.cuentas.entities.EventoProcesadoEntity;
import cl.duoc.bancoxyz.ms.cuentas.entities.MovimientoEntity;
import cl.duoc.bancoxyz.ms.cuentas.events.AlertaSeguridadEvent;
import cl.duoc.bancoxyz.ms.cuentas.events.CuentaEvent;
import cl.duoc.bancoxyz.ms.cuentas.exceptions.CuentaNoEncontradaException;
import cl.duoc.bancoxyz.ms.cuentas.exceptions.OperacionCuentaInvalidaException;
import cl.duoc.bancoxyz.ms.cuentas.exceptions.SolicitudInvalidaException;
import cl.duoc.bancoxyz.ms.cuentas.messaging.EventoPublisher;
import cl.duoc.bancoxyz.ms.cuentas.repositories.CuentaRepository;
import cl.duoc.bancoxyz.ms.cuentas.repositories.EventoProcesadoRepository;
import cl.duoc.bancoxyz.ms.cuentas.repositories.MovimientoRepository;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CuentaService {

    private static final Set<String> TIPOS_CUENTA = Set.of("AHORRO", "PRESTAMO", "HIPOTECA");
    private static final String NIVEL_ALTO = "ALTA";

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final EventoPublisher eventoPublisher;
    private final String topicoCuentasEventos;

    public CuentaService(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            EventoProcesadoRepository eventoProcesadoRepository,
            EventoPublisher eventoPublisher,
            @Value("${topicos.cuentas-eventos}") String topicoCuentasEventos) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
        this.eventoPublisher = eventoPublisher;
        this.topicoCuentasEventos = topicoCuentasEventos;
    }

    @RateLimiter(name = "msCuentasApi")
    @Transactional(readOnly = true)
    public List<CuentaDTO> listarCuentas() {
        return cuentaRepository.findAllByOrderByCuentaIdAsc().stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CuentaDTO obtenerCuenta(Long cuentaId) {
        return aDTO(buscar(cuentaId));
    }

    @Transactional(readOnly = true)
    public List<CuentaDTO> listarPorCliente(Long clienteId) {
        return cuentaRepository.findByClienteIdOrderByCuentaIdAsc(clienteId).stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoDTO> listarMovimientos(Long cuentaId) {
        buscar(cuentaId);
        return movimientoRepository.findByCuentaIdOrderByFechaDescIdDesc(cuentaId).stream()
                .map(m -> new MovimientoDTO(m.getCuentaId(), m.getFecha(), m.getTipoMovimiento(), m.getMonto(),
                        m.getDescripcion(), m.getCanal()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public CuentaDTO abrir(AperturaCuentaRequest solicitud) throws Exception {
        if (solicitud == null || solicitud.clienteId() == null) {
            throw new SolicitudInvalidaException("clienteId es obligatorio para abrir una cuenta");
        }
        if (solicitud.titular() == null || solicitud.titular().isBlank()) {
            throw new SolicitudInvalidaException("titular es obligatorio para abrir una cuenta");
        }
        String tipo = solicitud.tipo() == null ? "" : solicitud.tipo().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_CUENTA.contains(tipo)) {
            throw new SolicitudInvalidaException("tipo debe ser AHORRO, PRESTAMO o HIPOTECA");
        }
        BigDecimal depositoInicial = solicitud.depositoInicial() == null ? BigDecimal.ZERO : solicitud.depositoInicial();
        if (depositoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new SolicitudInvalidaException("depositoInicial no puede ser negativo");
        }

        Long nuevoId = cuentaRepository.findTopByOrderByCuentaIdDesc()
                .map(ultima -> ultima.getCuentaId() + 1)
                .orElse(1L);

        CuentaEntity cuenta = new CuentaEntity();
        cuenta.setCuentaId(nuevoId);
        cuenta.setClienteId(solicitud.clienteId());
        cuenta.setTitular(solicitud.titular().trim());
        cuenta.setTipo(tipo);
        cuenta.setSaldo(depositoInicial);
        cuenta.setEstado(EstadoCuenta.ACTIVA);
        cuenta.setFechaApertura(LocalDate.now());
        cuenta.setOrigen("APERTURA");
        cuenta = cuentaRepository.saveAndFlush(cuenta);

        if (depositoInicial.compareTo(BigDecimal.ZERO) > 0) {
            MovimientoEntity movimiento = new MovimientoEntity();
            movimiento.setCuentaId(nuevoId);
            movimiento.setFecha(LocalDate.now());
            movimiento.setTipoMovimiento("DEPOSITO");
            movimiento.setMonto(depositoInicial);
            movimiento.setDescripcion("Deposito inicial de apertura");
            movimiento.setCanal("SUCURSAL");
            movimiento.setFechaRegistro(LocalDateTime.now());
            movimientoRepository.save(movimiento);
        }

        publicarEvento("CUENTA_ABIERTA", cuenta, "Apertura de cuenta " + tipo);
        log.info("[CUENTAS] Cuenta {} ({}) abierta para el cliente {} con saldo {}",
                nuevoId, tipo, cuenta.getClienteId(), depositoInicial);
        return aDTO(cuenta);
    }

    @Transactional(rollbackFor = Exception.class)
    public CuentaDTO bloquear(Long cuentaId, String motivo) throws Exception {
        CuentaEntity cuenta = buscar(cuentaId);
        if (cuenta.getEstado() == EstadoCuenta.CERRADA) {
            throw new OperacionCuentaInvalidaException("La cuenta " + cuentaId + " esta cerrada");
        }
        cuenta.setEstado(EstadoCuenta.BLOQUEADA);
        cuenta.setMotivoEstado(motivo == null || motivo.isBlank() ? "Bloqueo solicitado por el banco" : motivo.trim());
        cuenta = cuentaRepository.saveAndFlush(cuenta);
        publicarEvento("CUENTA_BLOQUEADA", cuenta, cuenta.getMotivoEstado());
        log.info("[CUENTAS] Cuenta {} BLOQUEADA ({})", cuentaId, cuenta.getMotivoEstado());
        return aDTO(cuenta);
    }

    @Transactional(rollbackFor = Exception.class)
    public CuentaDTO desbloquear(Long cuentaId, String motivo) throws Exception {
        CuentaEntity cuenta = buscar(cuentaId);
        if (cuenta.getEstado() != EstadoCuenta.BLOQUEADA) {
            throw new OperacionCuentaInvalidaException("La cuenta " + cuentaId + " no esta bloqueada, esta " + cuenta.getEstado());
        }
        cuenta.setEstado(EstadoCuenta.ACTIVA);
        cuenta.setMotivoEstado(motivo == null || motivo.isBlank() ? "Desbloqueo revisado por el banco" : motivo.trim());
        cuenta = cuentaRepository.saveAndFlush(cuenta);
        publicarEvento("CUENTA_DESBLOQUEADA", cuenta, cuenta.getMotivoEstado());
        log.info("[CUENTAS] Cuenta {} vuelve a estar ACTIVA", cuentaId);
        return aDTO(cuenta);
    }

    @Transactional(rollbackFor = Exception.class)
    public CuentaDTO cerrar(Long cuentaId, String motivo) throws Exception {
        CuentaEntity cuenta = buscar(cuentaId);
        if (cuenta.getEstado() == EstadoCuenta.CERRADA) {
            throw new OperacionCuentaInvalidaException("La cuenta " + cuentaId + " ya estaba cerrada");
        }
        if (cuenta.getSaldo().compareTo(BigDecimal.ZERO) != 0) {
            throw new OperacionCuentaInvalidaException("SALDO_PENDIENTE: la cuenta " + cuentaId
                    + " tiene saldo " + cuenta.getSaldo().toPlainString() + " y debe quedar en cero antes de cerrarla");
        }
        cuenta.setEstado(EstadoCuenta.CERRADA);
        cuenta.setFechaCierre(LocalDate.now());
        cuenta.setMotivoEstado(motivo == null || motivo.isBlank() ? "Cierre solicitado por el cliente" : motivo.trim());
        cuenta = cuentaRepository.saveAndFlush(cuenta);
        publicarEvento("CUENTA_CERRADA", cuenta, cuenta.getMotivoEstado());
        log.info("[CUENTAS] Cuenta {} CERRADA", cuentaId);
        return aDTO(cuenta);
    }

    @Transactional(rollbackFor = Exception.class)
    public void atenderAlerta(AlertaSeguridadEvent alerta) throws Exception {
        if (eventoProcesadoRepository.existsById(alerta.eventId())) {
            log.warn("[DUPLICADO] Alerta {} ya atendida: se descarta", alerta.eventId());
            return;
        }
        eventoProcesadoRepository.save(new EventoProcesadoEntity(alerta.eventId(), alerta.tipoEvento(), LocalDateTime.now()));

        if (!NIVEL_ALTO.equals(alerta.nivel()) || alerta.cuentaId() == null) {
            log.info("[ALERTA] Alerta {} de nivel {} registrada, no requiere bloqueo", alerta.tipoAlerta(), alerta.nivel());
            return;
        }
        CuentaEntity cuenta = cuentaRepository.findById(alerta.cuentaId()).orElse(null);
        if (cuenta == null || cuenta.getEstado() != EstadoCuenta.ACTIVA) {
            log.info("[ALERTA] La cuenta {} no esta activa o no existe: no se aplica bloqueo", alerta.cuentaId());
            return;
        }
        cuenta.setEstado(EstadoCuenta.BLOQUEADA);
        cuenta.setMotivoEstado("Bloqueo preventivo por alerta de seguridad " + alerta.tipoAlerta());
        cuenta = cuentaRepository.saveAndFlush(cuenta);
        publicarEvento("CUENTA_BLOQUEADA", cuenta, cuenta.getMotivoEstado());
        log.warn("[ALERTA] Cuenta {} BLOQUEADA en forma preventiva por {}", cuenta.getCuentaId(), alerta.tipoAlerta());
    }

    private void publicarEvento(String tipoEvento, CuentaEntity cuenta, String motivo) throws Exception {
        CuentaEvent evento = new CuentaEvent(UUID.randomUUID().toString(), tipoEvento, Instant.now(),
                cuenta.getCuentaId(), cuenta.getClienteId(), cuenta.getTitular(), cuenta.getTipo(),
                cuenta.getEstado().name(), motivo);
        eventoPublisher.publicar(topicoCuentasEventos, String.valueOf(cuenta.getCuentaId()), evento);
    }

    private CuentaEntity buscar(Long cuentaId) {
        return cuentaRepository.findById(cuentaId)
                .orElseThrow(() -> new CuentaNoEncontradaException(cuentaId));
    }

    private CuentaDTO aDTO(CuentaEntity entity) {
        return new CuentaDTO(entity.getCuentaId(), entity.getClienteId(), entity.getTitular(), entity.getTipo(),
                entity.getSaldo(), entity.getEstado().name(), entity.getFechaApertura(), entity.getMotivoEstado());
    }
}
