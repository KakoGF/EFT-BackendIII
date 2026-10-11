package cl.duocuc.bancoxyz.batch.intereses.processor;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;

import cl.duocuc.bancoxyz.batch.common.util.MotivosUtil;
import cl.duocuc.bancoxyz.batch.common.util.TextoUtil;
import cl.duocuc.bancoxyz.batch.intereses.model.CuentaInteresProcesada;
import cl.duocuc.bancoxyz.batch.intereses.model.CuentaInteresRaw;

public class InteresSkipListener implements SkipListener<CuentaInteresRaw, CuentaInteresProcesada> {

    private static final Logger log = LoggerFactory.getLogger(InteresSkipListener.class);

    private final JdbcTemplate jdbcTemplate;

    public InteresSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onSkipInProcess(CuentaInteresRaw item, Throwable t) {
        log.warn("[SKIP] Cuenta omitida en el calculo de interes: {} - motivo: {}", item, t.getMessage());
        guardar(item.getCuentaId(), item.getNombre(), item.getSaldo(), item.getEdad(), item.getTipo(), t);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[SKIP] Linea omitida al leer el CSV de intereses - motivo: {}", t.getMessage());
        guardar(null, null, null, null, null, t);
    }

    @Override
    public void onSkipInWrite(CuentaInteresProcesada item, Throwable t) {
        log.warn("[SKIP] Cuenta omitida al escribir: {} - motivo: {}", item, MotivosUtil.motivoDeOmision(t));
        guardar(String.valueOf(item.getCuentaId()), item.getNombre(), String.valueOf(item.getSaldoInicial()),
                String.valueOf(item.getEdad()), item.getTipo(), t);
    }

    private void guardar(String cuentaId, String nombre, String saldo, String edad, String tipo, Throwable t) {
        jdbcTemplate.update(
                "INSERT INTO interes_anomalia (cuenta_id_origen, nombre_origen, saldo_origen, edad_origen, tipo_origen, motivo, fecha_deteccion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                TextoUtil.truncar(cuentaId, 50), TextoUtil.truncar(nombre, 150), TextoUtil.truncar(saldo, 50),
                TextoUtil.truncar(edad, 20), TextoUtil.truncar(tipo, 50),
                TextoUtil.truncar(MotivosUtil.motivoDeOmision(t), 300), LocalDate.now());
    }
}
