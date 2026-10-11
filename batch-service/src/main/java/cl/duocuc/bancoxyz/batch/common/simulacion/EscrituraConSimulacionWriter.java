package cl.duocuc.bancoxyz.batch.common.simulacion;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public class EscrituraConSimulacionWriter<T> implements ItemWriter<T> {

    private final ItemWriter<T> delegado;
    private final SimulacionFallas simulacionFallas;

    public EscrituraConSimulacionWriter(ItemWriter<T> delegado, SimulacionFallas simulacionFallas) {
        this.delegado = delegado;
        this.simulacionFallas = simulacionFallas;
    }

    @Override
    public void write(Chunk<? extends T> chunk) throws Exception {
        simulacionFallas.antesDeEscribir();
        delegado.write(chunk);
    }
}
