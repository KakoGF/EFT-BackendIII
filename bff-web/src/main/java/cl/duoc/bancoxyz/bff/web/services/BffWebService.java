package cl.duoc.bancoxyz.bff.web.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Service;

import cl.duoc.bancoxyz.bff.web.clients.MsClientesClient;
import cl.duoc.bancoxyz.bff.web.clients.MsCuentasClient;
import cl.duoc.bancoxyz.bff.web.dtos.ClienteWebDTO;
import cl.duoc.bancoxyz.bff.web.dtos.CuentaWebDetalleDTO;
import cl.duoc.bancoxyz.bff.web.dtos.CuentaWebResumenDTO;
import cl.duoc.bancoxyz.bff.web.dtos.MovimientoWebDTO;
import cl.duoc.bancoxyz.bff.web.dtos.ResumenMovimientosDTO;
import cl.duoc.bancoxyz.bff.web.dtos.core.ClienteCoreDTO;
import cl.duoc.bancoxyz.bff.web.dtos.core.CuentaCoreDTO;
import cl.duoc.bancoxyz.bff.web.dtos.core.MovimientoCoreDTO;
import cl.duoc.bancoxyz.bff.web.exceptions.CuentaNoEncontradaException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BffWebService {

    private static final String TEXTO_NO_DISPONIBLE = "Informacion no disponible temporalmente";
    private static final Set<String> ABONOS = Set.of("DEPOSITO", "TRANSFERENCIA_RECIBIDA");
    private static final ResumenMovimientosDTO RESUMEN_NO_DISPONIBLE = new ResumenMovimientosDTO(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

    private final MsCuentasClient msCuentasClient;
    private final MsClientesClient msClientesClient;
    private final Executor bffWebTaskExecutor;

    @CircuitBreaker(name = "msCuentas", fallbackMethod = "listarCuentasFallback")
    public List<CuentaWebResumenDTO> listarCuentas() {
        return msCuentasClient.obtenerCuentas().stream()
                .map(c -> new CuentaWebResumenDTO(c.cuentaId(), c.clienteId(), c.titular(), c.saldo(), c.tipo(), c.estado()))
                .toList();
    }

    @CircuitBreaker(name = "msCuentas", fallbackMethod = "obtenerDetalleFallback")
    public CuentaWebDetalleDTO obtenerDetalle(Long cuentaId) {
        CompletableFuture<CuentaCoreDTO> cuentaFuture = CompletableFuture.supplyAsync(
                () -> msCuentasClient.obtenerCuenta(cuentaId), bffWebTaskExecutor);
        CompletableFuture<List<MovimientoCoreDTO>> movimientosFuture = CompletableFuture.supplyAsync(
                () -> msCuentasClient.obtenerMovimientos(cuentaId), bffWebTaskExecutor);

        CuentaCoreDTO cuenta = esperar(cuentaFuture);
        CompletableFuture<ClienteCoreDTO> clienteFuture = CompletableFuture.supplyAsync(
                () -> cuenta.clienteId() == null ? null : msClientesClient.obtenerCliente(cuenta.clienteId()),
                bffWebTaskExecutor);
        List<MovimientoCoreDTO> movimientos = esperar(movimientosFuture);
        ClienteCoreDTO cliente = esperar(clienteFuture);

        List<MovimientoWebDTO> movimientosWeb = movimientos.stream()
                .map(m -> new MovimientoWebDTO(m.fecha(), m.tipoMovimiento(), m.monto(), m.descripcion(), m.canal()))
                .toList();

        return new CuentaWebDetalleDTO(
                cuenta.cuentaId(), cuenta.titular(), cuenta.saldo(), cuenta.tipo(), cuenta.estado(),
                cuenta.fechaApertura(), aClienteWeb(cliente), movimientosWeb, calcularResumen(movimientos));
    }

    @SuppressWarnings("unused")
    private List<CuentaWebResumenDTO> listarCuentasFallback(Throwable error) {
        propagarSiEsNoEncontrada(error);
        log.warn("[FALLBACK] Listado de cuentas no disponible: se responde listado vacio. Causa: {}", error.toString());
        return List.of();
    }

    @SuppressWarnings("unused")
    private CuentaWebDetalleDTO obtenerDetalleFallback(Long cuentaId, Throwable error) {
        propagarSiEsNoEncontrada(error);
        log.warn("[FALLBACK] Detalle de la cuenta {} en version degradada. Causa: {}", cuentaId, error.toString());
        return new CuentaWebDetalleDTO(cuentaId, TEXTO_NO_DISPONIBLE, null, TEXTO_NO_DISPONIBLE, TEXTO_NO_DISPONIBLE,
                null, null, List.of(), RESUMEN_NO_DISPONIBLE);
    }

    private ClienteWebDTO aClienteWeb(ClienteCoreDTO cliente) {
        if (cliente == null) {
            return null;
        }
        return new ClienteWebDTO(cliente.clienteId(), cliente.nombre(), cliente.email(), cliente.telefono(),
                cliente.edad(), cliente.segmento(), cliente.nivelRiesgo());
    }

    private void propagarSiEsNoEncontrada(Throwable error) {
        Throwable causa = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        if (causa instanceof CuentaNoEncontradaException noEncontrada) {
            throw noEncontrada;
        }
    }

    private <T> T esperar(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw ex;
        }
    }

    private ResumenMovimientosDTO calcularResumen(List<MovimientoCoreDTO> movimientos) {
        BigDecimal abonos = movimientos.stream()
                .filter(m -> ABONOS.contains(m.tipoMovimiento()))
                .map(MovimientoCoreDTO::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cargos = movimientos.stream()
                .filter(m -> !ABONOS.contains(m.tipoMovimiento()))
                .map(MovimientoCoreDTO::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenMovimientosDTO(abonos, cargos, abonos.subtract(cargos), movimientos.size());
    }
}
