package cl.duocuc.bancoxyz.batch.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FechaLegacyUtilTest {

    @Test
    void reconoceLosCuatroFormatosDelArchivoLegacy() {
        assertThat(FechaLegacyUtil.parsear("2024-03-22")).hasToString("2024-03-22");
        assertThat(FechaLegacyUtil.parsear("2024/03/22")).hasToString("2024-03-22");
        assertThat(FechaLegacyUtil.parsear("22-03-2024")).hasToString("2024-03-22");
        assertThat(FechaLegacyUtil.parsear("22/03/2024")).hasToString("2024-03-22");
    }

    @Test
    void rechazaFechasImposiblesOVacias() {
        assertThat(FechaLegacyUtil.parsear("2024-02-30")).isNull();
        assertThat(FechaLegacyUtil.parsear("31-13-9999")).isNull();
        assertThat(FechaLegacyUtil.parsear("")).isNull();
        assertThat(FechaLegacyUtil.parsear(null)).isNull();
    }

    @Test
    void normalizaTiposConTilde() {
        assertThat(TextoUtil.normalizar(" Depósito ")).isEqualTo("deposito");
    }
}
