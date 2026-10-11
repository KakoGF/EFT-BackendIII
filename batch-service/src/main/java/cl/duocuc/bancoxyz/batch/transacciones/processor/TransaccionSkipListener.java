package cl.duocuc.bancoxyz.batch.transacciones.processor;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

import cl.duocuc.bancoxyz.batch.common.util.MotivosUtil;
import cl.duocuc.bancoxyz.batch.common.util.TextoUtil;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionProcesada;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionRaw;

public class TransaccionSkipListener implements SkipListener<TransaccionRaw, TransaccionProcesada> {

    private static final Logger log = LoggerFactory.getLogger(TransaccionSkipListener.class);

    private final JdbcTemplate jdbcTemplate;

    public TransaccionSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onSkipInProcess(TransaccionRaw item, Throwable t) {
        log.warn("[SKIP] Transaccion omitida en el procesamiento: {} - motivo: {}", item, t.getMessage());
        guardar(item.getId(), item.getFecha(), item.getMonto(), item.getTipo(), t);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[SKIP] Linea omitida al leer el CSV - motivo: {}", t.getMessage());
        guardar(null, null, null, null, t);
    }

    @Override
    public void onSkipInWrite(TransaccionProcesada item, Throwable t) {
        log.warn("[SKIP] Transaccion omitida al escribir: {} - motivo: {}", item.getId(), t.getMessage());
        guardar(String.valueOf(item.getId()), String.valueOf(item.getFecha()), String.valueOf(item.getMonto()),
                item.getTipo(), t);
    }

    private void guardar(String id, String fecha, String monto, String tipo, Throwable t) {
        jdbcTemplate.update(
                "INSERT INTO transaccion_error_lectura (id_origen, fecha_origen, monto_origen, tipo_origen, motivo, fecha_deteccion) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                TextoUtil.truncar(id, 50), TextoUtil.truncar(fecha, 50), TextoUtil.truncar(monto, 50),
                TextoUtil.truncar(tipo, 50), TextoUtil.truncar(MotivosUtil.motivoDeOmision(t), 300), LocalDate.now());
    }
}
