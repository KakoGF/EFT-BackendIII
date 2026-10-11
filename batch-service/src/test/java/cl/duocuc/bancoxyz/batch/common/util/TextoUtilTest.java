package cl.duocuc.bancoxyz.batch.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoUtilTest {

    @Test
    void dejaIntactoUnTextoCortoquesNoSuperaElLimite() {
        assertThat(TextoUtil.truncar("mensaje corto", 300)).isEqualTo("mensaje corto");
    }

    @Test
    void truncaUnTextoLargoRespetandoElLimiteTotal() {
        String textoLargo = "x".repeat(500);
        String resultado = TextoUtil.truncar(textoLargo, 300);

        assertThat(resultado).hasSize(300);
        assertThat(resultado).endsWith("...");
    }

    @Test
    void devuelveNullSiElTextoEsNull() {
        assertThat(TextoUtil.truncar(null, 300)).isNull();
    }
}
