package cl.duocuc.bancoxyz.batch.common.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.dao.DuplicateKeyException;

import cl.duocuc.bancoxyz.batch.common.exception.DataValidationException;

public class DatosInconsistentesSkipPolicy implements SkipPolicy {

    private static final Logger log = LoggerFactory.getLogger(DatosInconsistentesSkipPolicy.class);

    private final int skipLimit;

    public DatosInconsistentesSkipPolicy(int skipLimit) {
        this.skipLimit = skipLimit;
    }

    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        boolean esErrorDeDato = t instanceof DataValidationException
                || t instanceof FlatFileParseException
                || t instanceof DuplicateKeyException;

        if (!esErrorDeDato) {
            log.error("Excepcion NO omitible (no es un error de dato): {}. El Step se detendra.", t.toString());
            return false;
        }

        if (skipCount >= skipLimit) {
            log.error("Se alcanzo el limite de omisiones permitidas por particion ({}). El Step se detendra.", skipLimit);
            throw new SkipLimitExceededException(skipLimit, t);
        }

        log.debug("Registro omitido ({}/{}) por error de dato: {}", skipCount + 1, skipLimit, t.getMessage());
        return true;
    }
}
