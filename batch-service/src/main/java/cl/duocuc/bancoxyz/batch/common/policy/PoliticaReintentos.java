package cl.duocuc.bancoxyz.batch.common.policy;

import java.util.Map;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

public final class PoliticaReintentos {

    private PoliticaReintentos() {
    }

    public static RetryPolicy crear(int maxIntentos) {
        Map<Class<? extends Throwable>, Boolean> erroresTemporales = Map.of(
                TransientDataAccessException.class, true,
                DataAccessResourceFailureException.class, true);
        return new SimpleRetryPolicy(maxIntentos, erroresTemporales, true);
    }

    public static BackOffPolicy esperaCreciente() {
        ExponentialBackOffPolicy espera = new ExponentialBackOffPolicy();
        espera.setInitialInterval(500);
        espera.setMultiplier(2.0);
        espera.setMaxInterval(4000);
        return espera;
    }
}
