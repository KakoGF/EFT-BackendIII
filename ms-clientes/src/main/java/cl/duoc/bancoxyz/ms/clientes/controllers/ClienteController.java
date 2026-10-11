package cl.duoc.bancoxyz.ms.clientes.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.bancoxyz.ms.clientes.dtos.ClienteConCuentasDTO;
import cl.duoc.bancoxyz.ms.clientes.dtos.ClienteDTO;
import cl.duoc.bancoxyz.ms.clientes.dtos.ClienteRequest;
import cl.duoc.bancoxyz.ms.clientes.services.ClienteService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    public List<ClienteDTO> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{clienteId}")
    public ClienteDTO obtener(@PathVariable Long clienteId) {
        return clienteService.obtener(clienteId);
    }

    @GetMapping("/{clienteId}/cuentas")
    public ClienteConCuentasDTO obtenerConCuentas(@PathVariable Long clienteId) {
        return clienteService.obtenerConCuentas(clienteId);
    }

    @PostMapping
    public ResponseEntity<ClienteDTO> crear(@RequestBody ClienteRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(solicitud));
    }

    @PutMapping("/{clienteId}")
    public ClienteDTO actualizar(@PathVariable Long clienteId, @RequestBody ClienteRequest solicitud) {
        return clienteService.actualizar(clienteId, solicitud);
    }

    @PatchMapping("/{clienteId}/revision")
    public ClienteDTO cerrarRevision(@PathVariable Long clienteId) {
        return clienteService.cerrarRevision(clienteId);
    }
}
