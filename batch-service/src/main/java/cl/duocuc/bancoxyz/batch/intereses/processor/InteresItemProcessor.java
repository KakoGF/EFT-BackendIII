package cl.duocuc.bancoxyz.batch.intereses.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import cl.duocuc.bancoxyz.batch.common.exception.DataValidationException;
import cl.duocuc.bancoxyz.batch.common.util.TextoUtil;
import cl.duocuc.bancoxyz.batch.intereses.model.CuentaInteresProcesada;
import cl.duocuc.bancoxyz.batch.intereses.model.CuentaInteresRaw;

@Component
@StepScope
public class InteresItemProcessor implements ItemProcessor<CuentaInteresRaw, CuentaInteresProcesada> {

    private static final Map<String, BigDecimal> TASAS_MENSUALES = Map.of(
            "ahorro", new BigDecimal("0.015"),
            "prestamo", new BigDecimal("0.020"),
            "hipoteca", new BigDecimal("0.025"));

    private static final int EDAD_MINIMA = 0;
    private static final int EDAD_MAXIMA = 100;

    @Override
    public CuentaInteresProcesada process(CuentaInteresRaw raw) {
        Long cuentaId = parsearId(raw);
        Integer edad = parsearEdad(raw);
        BigDecimal saldo = parsearSaldo(raw);
        String tipo = TextoUtil.normalizar(raw.getTipo());
        String nombre = raw.getNombre() == null ? "" : raw.getNombre().trim();

        if (edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            throw new DataValidationException("EDAD_FUERA_DE_RANGO: edad %d fuera de 0-100 en %s".formatted(edad, raw));
        }

        BigDecimal tasa = TASAS_MENSUALES.get(tipo);
        if (tasa == null) {
            throw new DataValidationException("TIPO_NO_RECONOCIDO: tipo de producto '%s' sin tasa en %s"
                    .formatted(raw.getTipo(), raw));
        }

        BigDecimal interes = saldo.multiply(tasa).setScale(2, RoundingMode.HALF_UP);

        CuentaInteresProcesada procesada = new CuentaInteresProcesada();
        procesada.setCuentaId(cuentaId);
        procesada.setNombre(nombre);
        procesada.setSaldoInicial(saldo);
        procesada.setEdad(edad);
        procesada.setTipo(tipo);
        procesada.setTasaAplicada(tasa);
        procesada.setInteresMensual(interes);
        procesada.setSaldoFinal(saldo.add(interes));
        return procesada;
    }

    private Long parsearId(CuentaInteresRaw raw) {
        try {
            return Long.parseLong(raw.getCuentaId().trim());
        } catch (RuntimeException e) {
            throw new DataValidationException("CUENTA_ID_INVALIDO: cuenta_id invalido en " + raw, e);
        }
    }

    private Integer parsearEdad(CuentaInteresRaw raw) {
        if (raw.getEdad() == null || raw.getEdad().isBlank()) {
            throw new DataValidationException("EDAD_VACIA: el registro no trae edad " + raw);
        }
        try {
            return Integer.parseInt(raw.getEdad().trim());
        } catch (NumberFormatException e) {
            throw new DataValidationException("EDAD_NO_NUMERICA: edad no numerica en " + raw, e);
        }
    }

    private BigDecimal parsearSaldo(CuentaInteresRaw raw) {
        if (raw.getSaldo() == null || raw.getSaldo().isBlank()) {
            throw new DataValidationException("SALDO_VACIO: no se puede calcular interes sin saldo " + raw);
        }
        BigDecimal saldo;
        try {
            saldo = new BigDecimal(raw.getSaldo().trim());
        } catch (NumberFormatException e) {
            throw new DataValidationException("SALDO_NO_NUMERICO: saldo no numerico en " + raw, e);
        }
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new DataValidationException("SALDO_NEGATIVO: saldo inconsistente en " + raw);
        }
        return saldo;
    }
}
