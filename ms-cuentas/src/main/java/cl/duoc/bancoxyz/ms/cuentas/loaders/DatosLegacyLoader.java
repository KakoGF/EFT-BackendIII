package cl.duoc.bancoxyz.ms.cuentas.loaders;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import cl.duoc.bancoxyz.ms.cuentas.entities.CuentaEntity;
import cl.duoc.bancoxyz.ms.cuentas.entities.EstadoCuenta;
import cl.duoc.bancoxyz.ms.cuentas.entities.MovimientoEntity;
import cl.duoc.bancoxyz.ms.cuentas.repositories.CuentaRepository;
import cl.duoc.bancoxyz.ms.cuentas.repositories.MovimientoRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatosLegacyLoader implements CommandLineRunner {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));
    private static final Set<String> TIPOS_CUENTA = Set.of("AHORRO", "PRESTAMO", "HIPOTECA");
    private static final Set<String> TIPOS_MOVIMIENTO = Set.of("DEPOSITO", "RETIRO", "COMPRA", "PAGO");
    private static final String NOMBRE_DESCONOCIDO = "Unknown";

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final Path carpetaDatos;
    private final boolean cargaInicial;

    public DatosLegacyLoader(
            CuentaRepository cuentaRepository,
            MovimientoRepository movimientoRepository,
            @Value("${datos-legacy.dir}") String carpetaDatos,
            @Value("${datos-legacy.carga-inicial:true}") boolean cargaInicial) {
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.carpetaDatos = Path.of(carpetaDatos);
        this.cargaInicial = cargaInicial;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!cargaInicial) {
            log.info(">> Esta instancia no hace la carga inicial (la hace la instancia principal)");
            return;
        }
        if (cuentaRepository.count() > 0) {
            log.info(">> La base de datos ya tiene cuentas: no se repite la carga inicial");
            return;
        }

        List<String[]> filasCuentas = leer("intereses_trimestrales.csv", 5);
        List<String> clientes = nombresDeClientes(filasCuentas);
        Map<Long, CuentaEntity> cuentas = new LinkedHashMap<>();
        int descartadas = 0;
        for (String[] campos : filasCuentas) {
            CuentaEntity cuenta = parsearCuenta(campos, clientes);
            if (cuenta == null || cuentas.containsKey(cuenta.getCuentaId())) {
                descartadas++;
                continue;
            }
            cuentas.put(cuenta.getCuentaId(), cuenta);
        }
        cuentaRepository.saveAll(cuentas.values());

        List<MovimientoEntity> movimientos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        int movimientosDescartados = 0;
        for (String[] campos : leer("estados_financieros_anuales.csv", 5)) {
            MovimientoEntity movimiento = parsearMovimiento(campos, cuentas.keySet());
            if (movimiento == null || !vistos.add(movimiento.getCuentaId() + "|" + movimiento.getFecha() + "|"
                    + movimiento.getTipoMovimiento() + "|" + movimiento.getMonto())) {
                movimientosDescartados++;
                continue;
            }
            movimientos.add(movimiento);
        }
        movimientoRepository.saveAll(movimientos);

        log.info(">> Carga inicial desde {}: {} cuentas de {} clientes ({} filas descartadas o repetidas), {} movimientos ({} descartados)",
                carpetaDatos, cuentas.size(), clientes.size(), descartadas, movimientos.size(), movimientosDescartados);
    }

    private List<String> nombresDeClientes(List<String[]> filas) {
        Set<String> nombres = new TreeSet<>();
        for (String[] campos : filas) {
            if (esFilaDeCuentaValida(campos)) {
                nombres.add(campos[1].trim());
            }
        }
        return new ArrayList<>(nombres);
    }

    private boolean esFilaDeCuentaValida(String[] campos) {
        try {
            Long.parseLong(campos[0].trim());
            String nombre = campos[1].trim();
            BigDecimal saldo = new BigDecimal(campos[2].trim());
            int edad = Integer.parseInt(campos[3].trim());
            String tipo = normalizar(campos[4]);
            return !nombre.isEmpty() && !NOMBRE_DESCONOCIDO.equalsIgnoreCase(nombre)
                    && saldo.compareTo(BigDecimal.ZERO) >= 0 && edad >= 0 && edad <= 100
                    && TIPOS_CUENTA.contains(tipo);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private CuentaEntity parsearCuenta(String[] campos, List<String> clientes) {
        if (!esFilaDeCuentaValida(campos)) {
            return null;
        }
        CuentaEntity cuenta = new CuentaEntity();
        cuenta.setCuentaId(Long.parseLong(campos[0].trim()));
        cuenta.setTitular(campos[1].trim());
        cuenta.setClienteId((long) clientes.indexOf(campos[1].trim()) + 1);
        cuenta.setSaldo(new BigDecimal(campos[2].trim()));
        cuenta.setTipo(normalizar(campos[4]));
        cuenta.setEstado(EstadoCuenta.ACTIVA);
        cuenta.setOrigen("LEGACY");
        return cuenta;
    }

    private MovimientoEntity parsearMovimiento(String[] campos, Set<Long> cuentasExistentes) {
        try {
            Long cuentaId = Long.parseLong(campos[0].trim());
            LocalDate fecha = parsearFecha(campos[1]);
            String tipo = normalizar(campos[2]);
            BigDecimal monto = new BigDecimal(campos[3].trim());
            if (!cuentasExistentes.contains(cuentaId) || fecha == null || !TIPOS_MOVIMIENTO.contains(tipo)
                    || monto.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            MovimientoEntity movimiento = new MovimientoEntity();
            movimiento.setCuentaId(cuentaId);
            movimiento.setFecha(fecha);
            movimiento.setTipoMovimiento(tipo);
            movimiento.setMonto(monto);
            movimiento.setDescripcion(campos[4].isBlank() ? "Sin descripcion registrada" : campos[4].trim());
            movimiento.setCanal("LEGACY");
            movimiento.setFechaRegistro(LocalDateTime.now());
            return movimiento;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDate parsearFecha(String valor) {
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(valor.trim(), formato);
            } catch (DateTimeParseException ignorada) {
                continue;
            }
        }
        return null;
    }

    private String normalizar(String valor) {
        String sinTildes = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT);
    }

    private List<String[]> leer(String archivo, int columnas) throws Exception {
        List<String[]> filas = new ArrayList<>();
        try (BufferedReader lector = Files.newBufferedReader(carpetaDatos.resolve(archivo), StandardCharsets.UTF_8)) {
            lector.readLine();
            String linea;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(",", -1);
                if (campos.length >= columnas) {
                    filas.add(campos);
                }
            }
        }
        return filas;
    }
}
