package cl.duocuc.bancoxyz.batch.cuentasanuales.processor;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

import cl.duocuc.bancoxyz.batch.common.util.MotivosUtil;
import cl.duocuc.bancoxyz.batch.common.util.TextoUtil;
import cl.duocuc.bancoxyz.batch.cuentasanuales.model.CuentaAnualProcesada;
import cl.duocuc.bancoxyz.batch.cuentasanuales.model.CuentaAnualRaw;

public class CuentaAnualSkipListener implements SkipListener<CuentaAnualRaw, CuentaAnualProcesada> {

    private static final Logger log = LoggerFactory.getLogger(CuentaAnualSkipListener.class);

    private final JdbcTemplate jdbcTemplate;

    public CuentaAnualSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onSkipInProcess(CuentaAnualRaw item, Throwable t) {
        log.warn("[SKIP] Movimiento anual omitido: {} - motivo: {}", item, t.getMessage());
        guardar(item.getCuentaId(), item.getFecha(), item.getTransaccion(), item.getMonto(), item.getDescripcion(), t);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[SKIP] Linea omitida al leer el CSV de estados anuales - motivo: {}", t.getMessage());
        guardar(null, null, null, null, null, t);
    }

    @Override
    public void onSkipInWrite(CuentaAnualProcesada item, Throwable t) {
        log.warn("[SKIP] Movimiento anual omitido al escribir: cuenta {} fecha {} - motivo: {}",
                item.getCuentaId(), item.getFecha(), MotivosUtil.motivoDeOmision(t));
        guardar(String.valueOf(item.getCuentaId()), String.valueOf(item.getFecha()), item.getTransaccion(),
                String.valueOf(item.getMonto()), item.getDescripcion(), t);
    }

    private void guardar(String cuentaId, String fecha, String transaccion, String monto, String descripcion, Throwable t) {
        jdbcTemplate.update(
                "INSERT INTO cuenta_anual_error_lectura (cuenta_id_origen, fecha_origen, transaccion_origen, monto_origen, descripcion_origen, motivo, fecha_deteccion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                TextoUtil.truncar(cuentaId, 50), TextoUtil.truncar(fecha, 50), TextoUtil.truncar(transaccion, 50),
                TextoUtil.truncar(monto, 50), TextoUtil.truncar(descripcion, 300),
                TextoUtil.truncar(MotivosUtil.motivoDeOmision(t), 300), LocalDate.now());
    }
}
