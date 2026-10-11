package cl.duoc.bancoxyz.ms.pagos.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.bancoxyz.ms.pagos.dtos.TransaccionDTO;
import cl.duoc.bancoxyz.ms.pagos.services.TransaccionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/pagos")
@RequiredArgsConstructor
public class TransaccionController {

    private final TransaccionService transaccionService;

    @GetMapping("/transacciones/{solicitudId}")
    public TransaccionDTO obtener(@PathVariable String solicitudId) {
        return transaccionService.obtener(solicitudId);
    }

    @GetMapping("/cuentas/{cuentaId}/transacciones")
    public List<TransaccionDTO> listarPorCuenta(@PathVariable Long cuentaId) {
        return transaccionService.listarPorCuenta(cuentaId);
    }
}
