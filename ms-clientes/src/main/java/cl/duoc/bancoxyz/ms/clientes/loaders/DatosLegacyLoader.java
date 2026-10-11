package cl.duoc.bancoxyz.ms.clientes.loaders;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import cl.duoc.bancoxyz.ms.clientes.entities.ClienteEntity;
import cl.duoc.bancoxyz.ms.clientes.entities.NivelRiesgo;
import cl.duoc.bancoxyz.ms.clientes.repositories.ClienteRepository;
import cl.duoc.bancoxyz.ms.clientes.services.SegmentoCliente;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatosLegacyLoader implements CommandLineRunner {

    private static final Set<String> TIPOS_CUENTA = Set.of("AHORRO", "PRESTAMO", "HIPOTECA");
    private static final String NOMBRE_DESCONOCIDO = "Unknown";

    private final ClienteRepository clienteRepository;
    private final Path carpetaDatos;
    private final boolean cargaInicial;

    public DatosLegacyLoader(
            ClienteRepository clienteRepository,
            @Value("${datos-legacy.dir}") String carpetaDatos,
            @Value("${datos-legacy.carga-inicial:true}") boolean cargaInicial) {
        this.clienteRepository = clienteRepository;
        this.carpetaDatos = Path.of(carpetaDatos);
        this.cargaInicial = cargaInicial;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!cargaInicial || clienteRepository.count() > 0) {
            log.info(">> No se hace carga inicial de clientes (desactivada o la base ya tiene datos)");
            return;
        }

        Map<String, Integer> edadPorNombre = new TreeMap<>();
        for (String[] campos : leer("intereses_trimestrales.csv")) {
            if (esFilaValida(campos)) {
                edadPorNombre.putIfAbsent(campos[1].trim(), Integer.parseInt(campos[3].trim()));
            }
        }

        List<ClienteEntity> clientes = new ArrayList<>();
        long clienteId = 1;
        for (Map.Entry<String, Integer> entrada : edadPorNombre.entrySet()) {
            ClienteEntity cliente = new ClienteEntity();
            cliente.setClienteId(clienteId++);
            cliente.setNombre(entrada.getKey());
            cliente.setEmail(entrada.getKey().toLowerCase(Locale.ROOT).replace(' ', '.') + "@correo.cl");
            cliente.setEdad(entrada.getValue());
            cliente.setSegmento(SegmentoCliente.segun(entrada.getValue()));
            cliente.setNivelRiesgo(NivelRiesgo.NORMAL);
            cliente.setAlertasSeguridad(0);
            cliente.setOrigen("LEGACY");
            cliente.setFechaRegistro(LocalDateTime.now());
            cliente.setFechaActualizacion(LocalDateTime.now());
            clientes.add(cliente);
        }
        clienteRepository.saveAll(clientes);
        log.info(">> Carga inicial desde {}: {} clientes migrados del archivo legacy", carpetaDatos, clientes.size());
    }

    private boolean esFilaValida(String[] campos) {
        try {
            Long.parseLong(campos[0].trim());
            String nombre = campos[1].trim();
            BigDecimal saldo = new BigDecimal(campos[2].trim());
            int edad = Integer.parseInt(campos[3].trim());
            String tipo = Normalizer.normalize(campos[4].trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
            return !nombre.isEmpty() && !NOMBRE_DESCONOCIDO.equalsIgnoreCase(nombre)
                    && saldo.compareTo(BigDecimal.ZERO) >= 0 && edad >= 0 && edad <= 100
                    && TIPOS_CUENTA.contains(tipo);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private List<String[]> leer(String archivo) throws Exception {
        List<String[]> filas = new ArrayList<>();
        try (BufferedReader lector = Files.newBufferedReader(carpetaDatos.resolve(archivo), StandardCharsets.UTF_8)) {
            lector.readLine();
            String linea;
            while ((linea = lector.readLine()) != null) {
                String[] campos = linea.split(",", -1);
                if (!linea.isBlank() && campos.length >= 5) {
                    filas.add(campos);
                }
            }
        }
        return filas;
    }
}
