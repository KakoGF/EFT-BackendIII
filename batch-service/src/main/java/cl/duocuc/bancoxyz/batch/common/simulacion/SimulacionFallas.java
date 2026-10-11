package cl.duocuc.bancoxyz.batch.common.simulacion;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;

@Component
public class SimulacionFallas {

    private static final Logger log = LoggerFactory.getLogger(SimulacionFallas.class);

    private final boolean fallaTransitoria;
    private final boolean fallaCritica;
    private final AtomicBoolean transitoriaLanzada = new AtomicBoolean(false);
    private final AtomicBoolean criticaLanzada = new AtomicBoolean(false);

    public SimulacionFallas(
            @Value("${app.batch.simulacion.falla-transitoria:false}") boolean fallaTransitoria,
            @Value("${app.batch.simulacion.falla-critica:false}") boolean fallaCritica) {
        this.fallaTransitoria = fallaTransitoria;
        this.fallaCritica = fallaCritica;
    }

    public void antesDeEscribir() {
        if (fallaTransitoria && transitoriaLanzada.compareAndSet(false, true)) {
            log.warn("[SIMULACION] Se corta un momento la conexion con la base de datos al escribir un chunk");
            throw new TransientDataAccessResourceException(
                    "Simulacion: la conexion con la base de datos se corto un momento");
        }
        if (fallaCritica && criticaLanzada.compareAndSet(false, true)) {
            log.error("[SIMULACION] Falla critica en una particion (como si se cayera el proceso a mitad del job)");
            throw new IllegalStateException(
                    "Simulacion: falla critica del proceso a mitad de la escritura");
        }
    }
}
