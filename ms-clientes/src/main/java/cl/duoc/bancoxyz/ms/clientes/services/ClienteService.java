package cl.duoc.bancoxyz.ms.clientes.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.bancoxyz.ms.clientes.clients.MsCuentasClient;
import cl.duoc.bancoxyz.ms.clientes.dtos.ClienteConCuentasDTO;
import cl.duoc.bancoxyz.ms.clientes.dtos.ClienteDTO;
import cl.duoc.bancoxyz.ms.clientes.dtos.ClienteRequest;
import cl.duoc.bancoxyz.ms.clientes.dtos.core.CuentaCoreDTO;
import cl.duoc.bancoxyz.ms.clientes.entities.ClienteEntity;
import cl.duoc.bancoxyz.ms.clientes.entities.EventoProcesadoEntity;
import cl.duoc.bancoxyz.ms.clientes.entities.NivelRiesgo;
import cl.duoc.bancoxyz.ms.clientes.events.AlertaSeguridadEvent;
import cl.duoc.bancoxyz.ms.clientes.exceptions.ClienteNoEncontradoException;
import cl.duoc.bancoxyz.ms.clientes.exceptions.SolicitudInvalidaException;
import cl.duoc.bancoxyz.ms.clientes.repositories.ClienteRepository;
import cl.duoc.bancoxyz.ms.clientes.repositories.EventoProcesadoRepository;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final Pattern FORMATO_RUT = Pattern.compile("^\\d{7,8}-[\\dkK]$");
    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String NIVEL_ALTO = "ALTA";

    private final ClienteRepository clienteRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final MsCuentasClient msCuentasClient;

    @RateLimiter(name = "msClientesApi")
    @Transactional(readOnly = true)
    public List<ClienteDTO> listar() {
        return clienteRepository.findAllByOrderByClienteIdAsc().stream()
                .map(this::aDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtener(Long clienteId) {
        return aDTO(buscar(clienteId));
    }

    @Transactional(readOnly = true)
    public ClienteConCuentasDTO obtenerConCuentas(Long clienteId) {
        ClienteDTO cliente = aDTO(buscar(clienteId));
        List<CuentaCoreDTO> cuentas = msCuentasClient.cuentasDelCliente(clienteId);
        if (cuentas == null) {
            return new ClienteConCuentasDTO(cliente, List.of(),
                    "Las cuentas no estan disponibles en este momento, intenta mas tarde");
        }
        return new ClienteConCuentasDTO(cliente, cuentas, cuentas.size() + " cuenta(s) asociada(s)");
    }

    @Transactional
    public ClienteDTO crear(ClienteRequest solicitud) {
        validar(solicitud, true);
        if (clienteRepository.existsByRut(solicitud.rut().trim())) {
            throw new SolicitudInvalidaException("Ya existe un cliente con el rut " + solicitud.rut().trim());
        }
        Long nuevoId = clienteRepository.findTopByOrderByClienteIdDesc()
                .map(ultimo -> ultimo.getClienteId() + 1)
                .orElse(1L);

        ClienteEntity cliente = new ClienteEntity();
        cliente.setClienteId(nuevoId);
        cliente.setRut(solicitud.rut().trim());
        copiarDatos(solicitud, cliente);
        cliente.setNivelRiesgo(NivelRiesgo.NORMAL);
        cliente.setAlertasSeguridad(0);
        cliente.setOrigen("REGISTRO");
        cliente.setFechaRegistro(LocalDateTime.now());
        cliente.setFechaActualizacion(LocalDateTime.now());
        log.info("[CLIENTES] Cliente {} ({}) registrado", nuevoId, cliente.getNombre());
        return aDTO(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteDTO actualizar(Long clienteId, ClienteRequest solicitud) {
        validar(solicitud, false);
        ClienteEntity cliente = buscar(clienteId);
        if (solicitud.rut() != null && !solicitud.rut().isBlank()) {
            String rut = solicitud.rut().trim();
            if (!rut.equals(cliente.getRut()) && clienteRepository.existsByRut(rut)) {
                throw new SolicitudInvalidaException("Ya existe otro cliente con el rut " + rut);
            }
            cliente.setRut(rut);
        }
        copiarDatos(solicitud, cliente);
        cliente.setFechaActualizacion(LocalDateTime.now());
        log.info("[CLIENTES] Datos del cliente {} actualizados", clienteId);
        return aDTO(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteDTO cerrarRevision(Long clienteId) {
        ClienteEntity cliente = buscar(clienteId);
        cliente.setNivelRiesgo(NivelRiesgo.NORMAL);
        cliente.setFechaActualizacion(LocalDateTime.now());
        log.info("[CLIENTES] Revision de seguridad del cliente {} cerrada: vuelve a NORMAL", clienteId);
        return aDTO(clienteRepository.save(cliente));
    }

    @Transactional
    public void registrarAlerta(AlertaSeguridadEvent alerta) {
        if (eventoProcesadoRepository.existsById(alerta.eventId())) {
            log.warn("[DUPLICADO] Alerta {} ya registrada: se descarta", alerta.eventId());
            return;
        }
        eventoProcesadoRepository.save(new EventoProcesadoEntity(alerta.eventId(), alerta.tipoEvento(), LocalDateTime.now()));

        if (alerta.clienteId() == null) {
            log.info("[ALERTA] Alerta {} sin cliente asociado (cuenta {}): queda solo en el registro de eventos",
                    alerta.tipoAlerta(), alerta.cuentaId());
            return;
        }
        ClienteEntity cliente = clienteRepository.findById(alerta.clienteId()).orElse(null);
        if (cliente == null) {
            log.warn("[ALERTA] El cliente {} de la alerta no existe en ms-clientes", alerta.clienteId());
            return;
        }
        int alertas = cliente.getAlertasSeguridad() == null ? 0 : cliente.getAlertasSeguridad();
        cliente.setAlertasSeguridad(alertas + 1);
        cliente.setUltimaAlerta(alerta.tipoAlerta() + " (" + alerta.nivel() + ") en cuenta " + alerta.cuentaId()
                + ": " + alerta.detalle());
        if (NIVEL_ALTO.equals(alerta.nivel())) {
            cliente.setNivelRiesgo(NivelRiesgo.EN_OBSERVACION);
        }
        cliente.setFechaActualizacion(LocalDateTime.now());
        clienteRepository.save(cliente);
        log.warn("[ALERTA] Cliente {} con {} alerta(s) de seguridad, nivel de riesgo {}",
                cliente.getClienteId(), cliente.getAlertasSeguridad(), cliente.getNivelRiesgo());
    }

    private void validar(ClienteRequest solicitud, boolean esNuevo) {
        if (solicitud == null) {
            throw new SolicitudInvalidaException("El cuerpo de la solicitud es obligatorio");
        }
        if (esNuevo && (solicitud.rut() == null || !FORMATO_RUT.matcher(solicitud.rut().trim()).matches())) {
            throw new SolicitudInvalidaException("rut es obligatorio y debe tener el formato 12345678-9");
        }
        if (!esNuevo && solicitud.rut() != null && !solicitud.rut().isBlank()
                && !FORMATO_RUT.matcher(solicitud.rut().trim()).matches()) {
            throw new SolicitudInvalidaException("rut debe tener el formato 12345678-9");
        }
        if (esNuevo && (solicitud.nombre() == null || solicitud.nombre().isBlank())) {
            throw new SolicitudInvalidaException("nombre es obligatorio");
        }
        if (solicitud.email() != null && !solicitud.email().isBlank()
                && !FORMATO_EMAIL.matcher(solicitud.email().trim()).matches()) {
            throw new SolicitudInvalidaException("email no tiene un formato valido");
        }
        if (solicitud.edad() != null && (solicitud.edad() < 18 || solicitud.edad() > 100)) {
            throw new SolicitudInvalidaException("edad debe estar entre 18 y 100");
        }
    }

    private void copiarDatos(ClienteRequest solicitud, ClienteEntity cliente) {
        if (solicitud.nombre() != null && !solicitud.nombre().isBlank()) {
            cliente.setNombre(solicitud.nombre().trim());
        }
        if (solicitud.email() != null && !solicitud.email().isBlank()) {
            cliente.setEmail(solicitud.email().trim());
        }
        if (solicitud.telefono() != null && !solicitud.telefono().isBlank()) {
            cliente.setTelefono(solicitud.telefono().trim());
        }
        if (solicitud.direccion() != null && !solicitud.direccion().isBlank()) {
            cliente.setDireccion(solicitud.direccion().trim());
        }
        if (solicitud.edad() != null) {
            cliente.setEdad(solicitud.edad());
            cliente.setSegmento(SegmentoCliente.segun(solicitud.edad()));
        }
    }

    private ClienteEntity buscar(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNoEncontradoException(clienteId));
    }

    private ClienteDTO aDTO(ClienteEntity entity) {
        return new ClienteDTO(entity.getClienteId(), entity.getRut(), entity.getNombre(), entity.getEmail(),
                entity.getTelefono(), entity.getDireccion(), entity.getEdad(), entity.getSegmento(),
                entity.getNivelRiesgo().name(), entity.getAlertasSeguridad(), entity.getUltimaAlerta());
    }
}
