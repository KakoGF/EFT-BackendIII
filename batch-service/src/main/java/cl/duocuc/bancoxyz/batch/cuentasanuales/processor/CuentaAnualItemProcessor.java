package cl.duocuc.bancoxyz.batch.cuentasanuales.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import cl.duocuc.bancoxyz.batch.common.exception.DataValidationException;
import cl.duocuc.bancoxyz.batch.common.util.FechaLegacyUtil;
import cl.duocuc.bancoxyz.batch.common.util.TextoUtil;
import cl.duocuc.bancoxyz.batch.cuentasanuales.model.CuentaAnualProcesada;
import cl.duocuc.bancoxyz.batch.cuentasanuales.model.CuentaAnualRaw;

@Component
@StepScope
public class CuentaAnualItemProcessor implements ItemProcessor<CuentaAnualRaw, CuentaAnualProcesada> {

    private static final String DESCRIPCION_POR_DEFECTO = "Sin descripcion registrada";
    private static final Set<String> TIPOS_VALIDOS = Set.of("deposito", "retiro", "compra", "pago");

    @Override
    public CuentaAnualProcesada process(CuentaAnualRaw raw) {
        Long cuentaId = parsearId(raw);
        LocalDate fecha = parsearFecha(raw);
        BigDecimal monto = parsearMonto(raw);
        String transaccion = TextoUtil.normalizar(raw.getTransaccion());
        String descripcion = (raw.getDescripcion() == null || raw.getDescripcion().isBlank())
                ? DESCRIPCION_POR_DEFECTO
                : raw.getDescripcion().trim();

        CuentaAnualProcesada procesada = new CuentaAnualProcesada();
        procesada.setCuentaId(cuentaId);
        procesada.setFecha(fecha);
        procesada.setTransaccion(transaccion);
        procesada.setMonto(monto);
        procesada.setDescripcion(descripcion);

        if (monto.compareTo(BigDecimal.ZERO) == 0) {
            procesada.setAnomalia(true);
            procesada.setMotivoAnomalia("MONTO_CERO");
        } else if (monto.compareTo(BigDecimal.ZERO) < 0) {
            procesada.setAnomalia(true);
            procesada.setMotivoAnomalia("MONTO_NEGATIVO");
        } else if (!TIPOS_VALIDOS.contains(transaccion)) {
            procesada.setAnomalia(true);
            procesada.setMotivoAnomalia("TIPO_NO_RECONOCIDO: " + raw.getTransaccion());
        }

        return procesada;
    }

    private Long parsearId(CuentaAnualRaw raw) {
        try {
            return Long.parseLong(raw.getCuentaId().trim());
        } catch (RuntimeException e) {
            throw new DataValidationException("CUENTA_ID_INVALIDO: cuenta_id invalido en " + raw, e);
        }
    }

    private LocalDate parsearFecha(CuentaAnualRaw raw) {
        LocalDate fecha = FechaLegacyUtil.parsear(raw.getFecha());
        if (fecha == null) {
            throw new DataValidationException("FECHA_INVALIDA: fecha vacia o con formato no reconocido en " + raw);
        }
        return fecha;
    }

    private BigDecimal parsearMonto(CuentaAnualRaw raw) {
        if (raw.getMonto() == null || raw.getMonto().isBlank()) {
            throw new DataValidationException("MONTO_VACIO: el movimiento no trae monto " + raw);
        }
        try {
            return new BigDecimal(raw.getMonto().trim());
        } catch (NumberFormatException e) {
            throw new DataValidationException("MONTO_NO_NUMERICO: monto no numerico en " + raw, e);
        }
    }
}
