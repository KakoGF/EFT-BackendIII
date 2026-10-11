package cl.duocuc.bancoxyz.batch.transacciones.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import cl.duocuc.bancoxyz.batch.common.exception.DataValidationException;
import cl.duocuc.bancoxyz.batch.common.util.FechaLegacyUtil;
import cl.duocuc.bancoxyz.batch.common.util.TextoUtil;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionProcesada;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionRaw;

@Component
@StepScope
public class TransaccionItemProcessor implements ItemProcessor<TransaccionRaw, TransaccionProcesada> {

    private static final Set<String> TIPOS_VALIDOS = Set.of("debito", "credito");

    @Override
    public TransaccionProcesada process(TransaccionRaw raw) {
        Long id = parsearId(raw);
        LocalDate fecha = parsearFecha(raw);
        BigDecimal monto = parsearMonto(raw);
        String tipo = TextoUtil.normalizar(raw.getTipo());

        TransaccionProcesada procesada = new TransaccionProcesada();
        procesada.setId(id);
        procesada.setFecha(fecha);
        procesada.setMonto(monto);
        procesada.setTipo(tipo);

        if (monto.compareTo(BigDecimal.ZERO) < 0) {
            procesada.setAnomalia(true);
            procesada.setMotivoAnomalia("MONTO_NEGATIVO");
        } else if (monto.compareTo(BigDecimal.ZERO) == 0) {
            procesada.setAnomalia(true);
            procesada.setMotivoAnomalia("MONTO_CERO");
        } else if (!TIPOS_VALIDOS.contains(tipo)) {
            procesada.setAnomalia(true);
            procesada.setMotivoAnomalia("TIPO_NO_RECONOCIDO: " + raw.getTipo());
        }

        return procesada;
    }

    private Long parsearId(TransaccionRaw raw) {
        try {
            return Long.parseLong(raw.getId().trim());
        } catch (RuntimeException e) {
            throw new DataValidationException("ID_INVALIDO: id de transaccion invalido en registro " + raw, e);
        }
    }

    private LocalDate parsearFecha(TransaccionRaw raw) {
        LocalDate fecha = FechaLegacyUtil.parsear(raw.getFecha());
        if (fecha == null) {
            throw new DataValidationException("FECHA_INVALIDA: fecha vacia o con formato no reconocido en registro " + raw);
        }
        return fecha;
    }

    private BigDecimal parsearMonto(TransaccionRaw raw) {
        if (raw.getMonto() == null || raw.getMonto().isBlank()) {
            throw new DataValidationException("MONTO_VACIO: el registro no trae monto " + raw);
        }
        try {
            return new BigDecimal(raw.getMonto().trim());
        } catch (NumberFormatException e) {
            throw new DataValidationException("MONTO_NO_NUMERICO: monto no numerico en registro " + raw, e);
        }
    }
}
