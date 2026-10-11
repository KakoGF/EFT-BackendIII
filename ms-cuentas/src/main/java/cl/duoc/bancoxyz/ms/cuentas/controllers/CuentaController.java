package cl.duoc.bancoxyz.ms.cuentas.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.bancoxyz.ms.cuentas.dtos.AperturaCuentaRequest;
import cl.duoc.bancoxyz.ms.cuentas.dtos.CambioEstadoRequest;
import cl.duoc.bancoxyz.ms.cuentas.dtos.CuentaDTO;
import cl.duoc.bancoxyz.ms.cuentas.dtos.MovimientoDTO;
import cl.duoc.bancoxyz.ms.cuentas.services.CuentaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    @GetMapping("/cuentas")
    public List<CuentaDTO> listarCuentas() {
        return cuentaService.listarCuentas();
    }

    @GetMapping("/cuentas/{cuentaId}")
    public CuentaDTO obtenerCuenta(@PathVariable Long cuentaId) {
        return cuentaService.obtenerCuenta(cuentaId);
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public List<MovimientoDTO> listarMovimientos(@PathVariable Long cuentaId) {
        return cuentaService.listarMovimientos(cuentaId);
    }

    @GetMapping("/clientes/{clienteId}/cuentas")
    public List<CuentaDTO> listarCuentasDeCliente(@PathVariable Long clienteId) {
        return cuentaService.listarPorCliente(clienteId);
    }

    @PostMapping("/cuentas")
    public ResponseEntity<CuentaDTO> abrirCuenta(@RequestBody AperturaCuentaRequest solicitud) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaService.abrir(solicitud));
    }

    @PatchMapping("/cuentas/{cuentaId}/bloqueo")
    public CuentaDTO bloquear(@PathVariable Long cuentaId,
            @RequestBody(required = false) CambioEstadoRequest solicitud) throws Exception {
        return cuentaService.bloquear(cuentaId, solicitud == null ? null : solicitud.motivo());
    }

    @PatchMapping("/cuentas/{cuentaId}/desbloqueo")
    public CuentaDTO desbloquear(@PathVariable Long cuentaId,
            @RequestBody(required = false) CambioEstadoRequest solicitud) throws Exception {
        return cuentaService.desbloquear(cuentaId, solicitud == null ? null : solicitud.motivo());
    }

    @PatchMapping("/cuentas/{cuentaId}/cierre")
    public CuentaDTO cerrar(@PathVariable Long cuentaId,
            @RequestBody(required = false) CambioEstadoRequest solicitud) throws Exception {
        return cuentaService.cerrar(cuentaId, solicitud == null ? null : solicitud.motivo());
    }
}
