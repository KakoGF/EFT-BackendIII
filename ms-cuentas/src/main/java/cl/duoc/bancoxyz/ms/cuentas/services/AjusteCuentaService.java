package cl.duoc.bancoxyz.ms.cuentas.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.bancoxyz.ms.cuentas.entities.CuentaEntity;
import cl.duoc.bancoxyz.ms.cuentas.entities.EstadoCuenta;
import cl.duoc.bancoxyz.ms.cuentas.entities.EventoProcesadoEntity;
import cl.duoc.bancoxyz.ms.cuentas.entities.MovimientoEntity;
import cl.duoc.bancoxyz.ms.cuentas.events.AjusteCuentaResultadoEvent;
import cl.duoc.bancoxyz.ms.cuentas.events.AjusteCuentaSolicitadoEvent;
import cl.duoc.bancoxyz.ms.cuentas.messaging.EventoPublisher;
import cl.duoc.bancoxyz.ms.cuentas.repositories.CuentaRepository;
import cl.duoc.bancoxyz.ms.cuentas.repositories.EventoProcesadoRepository;
import cl.duoc.bancoxyz.ms.cuentas.repositories.MovimientoRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AjusteCuentaService {

    public static final String DEPOSITO = "DEPOSITO";
    public static final String RETIRO = "RETIRO";
    public static final String PAGO = "PAGO";
    public static final String TRANSFERENCIA = "TRANSFERENCIA";
    private static final String APLICADO = "APLICADO";
    private static final String RECHAZADO = "RECHAZADO";

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final EventoPublisher eventoPublisher;
    private final String topicoResultados;

    public AjusteCuentaService(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            EventoProcesadoRepository eventoProcesadoRepository,
            EventoPublisher eventoPublisher,
            @Value("${topicos.cuentas-ajustes-resultado}") String topicoResultados) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
        this.eventoPublisher = eventoPublisher;
        this.topicoResultados = topicoResultados;
    }

    @Transactional(rollbackFor = Exception.class)
    public void aplicar(AjusteCuentaSolicitadoEvent evento) throws Exception {
        if (eventoProcesadoRepository.existsById(evento.eventId())) {
            log.warn("[DUPLICADO] Ajuste {} (solicitud {}) ya aplicado antes: se descarta",
                    evento.eventId(), evento.solicitudId());
            return;
        }

        String tipo = evento.tipoTransaccion().trim().toUpperCase(Locale.ROOT);
        BigDecimal monto = evento.monto();
        CuentaEntity origen = cuentaRepository.findById(evento.cuentaOrigenId()).orElse(null);
        CuentaEntity destino = null;

        String motivo = null;
        if (origen == null) {
            motivo = "CUENTA_NO_EXISTE: la cuenta " + evento.cuentaOrigenId() + " no existe";
        } else {
            motivo = revisarEstado(origen, "CUENTA");
        }

        if (motivo == null && TRANSFERENCIA.equals(tipo)) {
            if (evento.cuentaDestinoId() == null || evento.cuentaDestinoId().equals(evento.cuentaOrigenId())) {
                motivo = "SOLICITUD_INVALIDA: la transferencia necesita una cuenta de destino distinta a la de origen";
            } else {
                destino = cuentaRepository.findById(evento.cuentaDestinoId()).orElse(null);
                motivo = destino == null
                        ? "CUENTA_DESTINO_NO_EXISTE: la cuenta " + evento.cuentaDestinoId() + " no existe"
                        : revisarEstado(destino, "CUENTA_DESTINO");
            }
        }

        boolean esCargo = RETIRO.equals(tipo) || PAGO.equals(tipo) || TRANSFERENCIA.equals(tipo);
        if (motivo == null && !esCargo && !DEPOSITO.equals(tipo)) {
            motivo = "TIPO_NO_SOPORTADO: " + tipo;
        }
        if (motivo == null && monto.compareTo(BigDecimal.ZERO) <= 0) {
            motivo = "SOLICITUD_INVALIDA: el monto debe ser mayor que cero";
        }
        if (motivo == null && esCargo && origen.getSaldo().compareTo(monto) < 0) {
            motivo = "SALDO_INSUFICIENTE: el saldo disponible es " + origen.getSaldo().toPlainString();
        }

        String resultado;
        if (motivo == null) {
            aplicarMovimientos(evento, tipo, monto, origen, destino);
            resultado = APLICADO;
            log.info("[SAGA] Paso 3: {} de {} aplicado en la cuenta {}{} | saldo origen {}",
                    tipo, monto, origen.getCuentaId(),
                    destino == null ? "" : " hacia la cuenta " + destino.getCuentaId(), origen.getSaldo());
        } else {
            resultado = RECHAZADO;
            log.info("[SAGA] Paso 3: solicitud {} RECHAZADA por ms-cuentas ({})", evento.solicitudId(), motivo);
        }

        eventoProcesadoRepository.saveAndFlush(new EventoProcesadoEntity(evento.eventId(), evento.tipoEvento(), LocalDateTime.now()));

        AjusteCuentaResultadoEvent respuesta = new AjusteCuentaResultadoEvent(
                UUID.randomUUID().toString(), "AJUSTE_CUENTA_RESULTADO", Instant.now(),
                evento.solicitudId(), evento.transaccionId(), resultado, motivo,
                evento.cuentaOrigenId(), origen == null ? null : origen.getTitular(),
                origen == null ? null : origen.getSaldo(),
                evento.cuentaDestinoId(), destino == null ? null : destino.getSaldo());
        eventoPublisher.publicar(topicoResultados, String.valueOf(evento.cuentaOrigenId()), respuesta);
    }

    private void aplicarMovimientos(AjusteCuentaSolicitadoEvent evento, String tipo, BigDecimal monto,
            CuentaEntity origen, CuentaEntity destino) {
        List<MovimientoEntity> movimientos = new ArrayList<>();
        String descripcion = evento.descripcion() == null || evento.descripcion().isBlank()
                ? tipo + " por canal " + evento.canal()
                : evento.descripcion().trim();

        switch (tipo) {
            case DEPOSITO -> {
                origen.setSaldo(origen.getSaldo().add(monto));
                movimientos.add(movimiento(origen.getCuentaId(), DEPOSITO, monto, descripcion, evento));
            }
            case RETIRO, PAGO -> {
                origen.setSaldo(origen.getSaldo().subtract(monto));
                movimientos.add(movimiento(origen.getCuentaId(), tipo, monto, descripcion, evento));
            }
            default -> {
                origen.setSaldo(origen.getSaldo().subtract(monto));
                destino.setSaldo(destino.getSaldo().add(monto));
                movimientos.add(movimiento(origen.getCuentaId(), "TRANSFERENCIA_ENVIADA", monto,
                        descripcion + " (a cuenta " + destino.getCuentaId() + ")", evento));
                movimientos.add(movimiento(destino.getCuentaId(), "TRANSFERENCIA_RECIBIDA", monto,
                        descripcion + " (desde cuenta " + origen.getCuentaId() + ")", evento));
            }
        }

        cuentaRepository.saveAndFlush(origen);
        if (destino != null) {
            cuentaRepository.saveAndFlush(destino);
        }
        movimientoRepository.saveAll(movimientos);
    }

    private MovimientoEntity movimiento(Long cuentaId, String tipo, BigDecimal monto, String descripcion,
            AjusteCuentaSolicitadoEvent evento) {
        MovimientoEntity movimiento = new MovimientoEntity();
        movimiento.setCuentaId(cuentaId);
        movimiento.setFecha(LocalDate.now());
        movimiento.setTipoMovimiento(tipo);
        movimiento.setMonto(monto);
        movimiento.setDescripcion(descripcion);
        movimiento.setCanal(evento.canal());
        movimiento.setSolicitudId(evento.solicitudId());
        movimiento.setFechaRegistro(LocalDateTime.now());
        return movimiento;
    }

    private String revisarEstado(CuentaEntity cuenta, String prefijo) {
        if (cuenta.getEstado() == EstadoCuenta.CERRADA) {
            return prefijo + "_CERRADA: la cuenta " + cuenta.getCuentaId() + " esta cerrada";
        }
        if (cuenta.getEstado() == EstadoCuenta.BLOQUEADA) {
            return prefijo + "_BLOQUEADA: la cuenta " + cuenta.getCuentaId() + " esta bloqueada ("
                    + cuenta.getMotivoEstado() + ")";
        }
        return null;
    }
}
