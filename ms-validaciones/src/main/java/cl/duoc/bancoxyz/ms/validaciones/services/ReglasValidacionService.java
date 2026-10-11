package cl.duoc.bancoxyz.ms.validaciones.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.duoc.bancoxyz.ms.validaciones.events.TransaccionSolicitadaEvent;

@Service
public class ReglasValidacionService {

    private static final String RETIRO = "RETIRO";
    private static final String CAJERO = "CAJERO";
    private static final String TIPO_CUENTA_DESCONOCIDO = "DESCONOCIDO";
    private static final Set<String> OPERACIONES_DE_CARGO = Set.of("RETIRO", "PAGO", "TRANSFERENCIA");

    private final Map<String, BigDecimal> limitePorCanal;
    private final BigDecimal limiteModoDegradado;
    private final BigDecimal multiploRetiroCajero;
    private final Set<String> tiposSinCargos;
    private final int maxSolicitudesVentana;
    private final long ventanaSegundos;
    private final BigDecimal umbralMontoInusual;
    private final Map<Long, Deque<Instant>> solicitudesPorCuenta = new HashMap<>();

    public ReglasValidacionService(
            @Value("${validaciones.limite.web}") BigDecimal limiteWeb,
            @Value("${validaciones.limite.movil}") BigDecimal limiteMovil,
            @Value("${validaciones.limite.cajero}") BigDecimal limiteCajero,
            @Value("${validaciones.limite.modo-degradado}") BigDecimal limiteModoDegradado,
            @Value("${validaciones.cajero.multiplo-retiro}") BigDecimal multiploRetiroCajero,
            @Value("${validaciones.tipos-cuenta-sin-cargos}") String tiposSinCargos,
            @Value("${validaciones.antifraude.max-solicitudes}") int maxSolicitudesVentana,
            @Value("${validaciones.antifraude.ventana-segundos}") long ventanaSegundos,
            @Value("${validaciones.alerta.monto-inusual}") BigDecimal umbralMontoInusual) {
        this.limitePorCanal = Map.of("WEB", limiteWeb, "MOVIL", limiteMovil, CAJERO, limiteCajero);
        this.limiteModoDegradado = limiteModoDegradado;
        this.multiploRetiroCajero = multiploRetiroCajero;
        this.tiposSinCargos = Arrays.stream(tiposSinCargos.split(","))
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.maxSolicitudesVentana = maxSolicitudesVentana;
        this.ventanaSegundos = ventanaSegundos;
        this.umbralMontoInusual = umbralMontoInusual;
    }

    public ResultadoValidacion evaluar(TransaccionSolicitadaEvent evento) {
        String canal = evento.canal().toUpperCase(Locale.ROOT);
        String tipo = evento.tipoTransaccion().toUpperCase(Locale.ROOT);
        String tipoCuenta = evento.tipoCuenta() == null ? TIPO_CUENTA_DESCONOCIDO : evento.tipoCuenta().toUpperCase(Locale.ROOT);

        int solicitudesRecientes = registrarYContar(evento.cuentaId());
        if (solicitudesRecientes > maxSolicitudesVentana) {
            return ResultadoValidacion.rechazarConAlerta("ACTIVIDAD_SOSPECHOSA",
                    solicitudesRecientes + " solicitudes en " + ventanaSegundos + " segundos para la cuenta " + evento.cuentaId(),
                    "ALTA");
        }

        if (TIPO_CUENTA_DESCONOCIDO.equals(tipoCuenta) && evento.monto().compareTo(limiteModoDegradado) > 0) {
            return ResultadoValidacion.rechazar("MONTO_EXCEDE_LIMITE_MODO_DEGRADADO",
                    "sin datos de la cuenta solo se aceptan operaciones de hasta " + limiteModoDegradado.toPlainString());
        }

        BigDecimal limite = limitePorCanal.get(canal);
        if (limite != null && evento.monto().compareTo(limite) > 0) {
            return ResultadoValidacion.rechazar("MONTO_EXCEDE_LIMITE_CANAL",
                    "el canal " + canal + " permite operaciones de hasta " + limite.toPlainString());
        }

        if (OPERACIONES_DE_CARGO.contains(tipo) && tiposSinCargos.contains(tipoCuenta)) {
            return ResultadoValidacion.rechazar("TIPO_CUENTA_NO_PERMITE_CARGOS",
                    "las cuentas de tipo " + tipoCuenta + " no permiten " + tipo.toLowerCase(Locale.ROOT));
        }

        if (CAJERO.equals(canal) && RETIRO.equals(tipo)
                && evento.monto().remainder(multiploRetiroCajero).compareTo(BigDecimal.ZERO) != 0) {
            return ResultadoValidacion.rechazar("MONTO_NO_MULTIPLO_DE_BILLETES",
                    "el cajero solo entrega multiplos de " + multiploRetiroCajero.toPlainString());
        }

        if (evento.monto().compareTo(umbralMontoInusual) >= 0) {
            return ResultadoValidacion.aprobarConAlerta("MONTO_INUSUAL", "MEDIA",
                    tipo + " de " + evento.monto().toPlainString() + " por canal " + canal + " supera el umbral de "
                            + umbralMontoInusual.toPlainString());
        }

        return ResultadoValidacion.aprobar();
    }

    private synchronized int registrarYContar(Long cuentaId) {
        Instant ahora = Instant.now();
        Instant limiteVentana = ahora.minusSeconds(ventanaSegundos);
        Deque<Instant> historial = solicitudesPorCuenta.computeIfAbsent(cuentaId, id -> new ArrayDeque<>());
        while (!historial.isEmpty() && historial.peekFirst().isBefore(limiteVentana)) {
            historial.pollFirst();
        }
        historial.addLast(ahora);
        return historial.size();
    }
}
