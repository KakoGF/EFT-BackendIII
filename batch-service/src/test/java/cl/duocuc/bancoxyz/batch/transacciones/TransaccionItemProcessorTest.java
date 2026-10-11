package cl.duocuc.bancoxyz.batch.transacciones;

import cl.duocuc.bancoxyz.batch.common.exception.DataValidationException;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionProcesada;
import cl.duocuc.bancoxyz.batch.transacciones.model.TransaccionRaw;
import cl.duocuc.bancoxyz.batch.transacciones.processor.TransaccionItemProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransaccionItemProcessorTest {

    private TransaccionRaw raw(String id, String fecha, String monto, String tipo) {
        TransaccionRaw r = new TransaccionRaw();
        r.setId(id);
        r.setFecha(fecha);
        r.setMonto(monto);
        r.setTipo(tipo);
        return r;
    }

    @Test
    void normalizaFechaConFormatoLegacy() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();
        TransaccionProcesada resultado = processor.process(raw("1", "2024/01/15", "1000", "debito"));

        assertThat(resultado).isNotNull();
        assertThat(resultado.getFecha().toString()).isEqualTo("2024-01-15");
        assertThat(resultado.isAnomalia()).isFalse();
    }

    @Test
    void marcaComoAnomaliaMontoNegativoSinDescartarlo() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();
        TransaccionProcesada resultado = processor.process(raw("3", "2024-01-03", "-200", "debito"));

        assertThat(resultado).isNotNull();
        assertThat(resultado.isAnomalia()).isTrue();
        assertThat(resultado.getMotivoAnomalia()).isEqualTo("MONTO_NEGATIVO");
    }

    @Test
    void procesaElMismoRegistroDeFormaIdempotente() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();
        TransaccionRaw entrada = raw("6", "2024-01-05", "700", "debito");

        TransaccionProcesada primeraVez = processor.process(entrada);
        TransaccionProcesada segundaVez = processor.process(entrada);

        assertThat(primeraVez).isNotNull();
        assertThat(segundaVez).isNotNull();
        assertThat(segundaVez.getId()).isEqualTo(primeraVez.getId());
        assertThat(segundaVez.getMonto()).isEqualByComparingTo(primeraVez.getMonto());
        assertThat(segundaVez.isAnomalia()).isEqualTo(primeraVez.isAnomalia());
    }

    @Test
    void aceptaLosCuatroFormatosDeFechaDelArchivoOficial() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();

        assertThat(processor.process(raw("10", "2024-04-02", "1000", "credito")).getFecha().toString()).isEqualTo("2024-04-02");
        assertThat(processor.process(raw("11", "2024/04/02", "1000", "credito")).getFecha().toString()).isEqualTo("2024-04-02");
        assertThat(processor.process(raw("12", "02-04-2024", "1000", "credito")).getFecha().toString()).isEqualTo("2024-04-02");
        assertThat(processor.process(raw("13", "02/04/2024", "1000", "credito")).getFecha().toString()).isEqualTo("2024-04-02");
    }

    @Test
    void marcaComoAnomaliaUnTipoNoReconocido() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();
        TransaccionProcesada resultado = processor.process(raw("14", "2024-04-02", "700", "invalido"));

        assertThat(resultado.isAnomalia()).isTrue();
        assertThat(resultado.getMotivoAnomalia()).startsWith("TIPO_NO_RECONOCIDO");
    }

    @Test
    void lanzaExcepcionDeValidacionCuandoNoHayMonto() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();

        assertThatThrownBy(() -> processor.process(raw("15", "2024-04-02", "", "debito")))
                .isInstanceOf(DataValidationException.class)
                .hasMessageStartingWith("MONTO_VACIO");
    }

    @Test
    void lanzaExcepcionDeValidacionParaFechaIrrecuperable() {
        TransaccionItemProcessor processor = new TransaccionItemProcessor();

        assertThatThrownBy(() -> processor.process(raw("9", "15-ENERO-2024", "1000", "debito")))
                .isInstanceOf(DataValidationException.class);
    }
}
