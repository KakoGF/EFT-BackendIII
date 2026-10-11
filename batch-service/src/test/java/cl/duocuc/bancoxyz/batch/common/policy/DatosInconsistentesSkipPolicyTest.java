package cl.duocuc.bancoxyz.batch.common.policy;

import cl.duocuc.bancoxyz.batch.common.exception.DataValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatosInconsistentesSkipPolicyTest {

    @Test
    void omiteErroresDeDatoDentroDelLimite() throws Exception {
        DatosInconsistentesSkipPolicy policy = new DatosInconsistentesSkipPolicy(3);

        assertThat(policy.shouldSkip(new DataValidationException("dato corrupto"), 0)).isTrue();
        assertThat(policy.shouldSkip(new FlatFileParseException("línea mal formada", "raw"), 1)).isTrue();
    }

    @Test
    void omiteRegistrosDuplicadosDetectadosPorLaBaseDeDatos() throws Exception {
        DatosInconsistentesSkipPolicy policy = new DatosInconsistentesSkipPolicy(3);

        assertThat(policy.shouldSkip(new DuplicateKeyException("llave de negocio duplicada"), 0)).isTrue();
    }

    @Test
    void noOmiteExcepcionesQueNoSonDeDato() throws Exception {
        DatosInconsistentesSkipPolicy policy = new DatosInconsistentesSkipPolicy(3);

        assertThat(policy.shouldSkip(new NullPointerException("bug real de código"), 0)).isFalse();
    }

    @Test
    void detieneElStepAlAlcanzarElLimite() {
        DatosInconsistentesSkipPolicy policy = new DatosInconsistentesSkipPolicy(2);

        assertThatThrownBy(() -> policy.shouldSkip(new DataValidationException("dato corrupto"), 2))
                .isInstanceOf(SkipLimitExceededException.class);
    }
}
