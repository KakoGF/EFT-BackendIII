package cl.duocuc.bancoxyz.batch.common.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

public class ReintentoListener implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(ReintentoListener.class);

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("[RETRY] Intento {} fallo por un error temporal ({}). Spring Batch reintenta el chunk.",
                context.getRetryCount(), throwable.getMessage());
    }

    @Override
    public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
        if (context.getRetryCount() > 0) {
            log.info("[RETRY] El chunk se escribio bien despues de {} reintento(s).", context.getRetryCount());
        }
    }
}
