package cl.duocuc.bancoxyz.batch.common.partition;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.Resource;

import cl.duocuc.bancoxyz.batch.common.util.ArchivoCsvUtil;

public class ArchivoCsvRangePartitioner implements Partitioner {

    private final Resource resource;

    public ArchivoCsvRangePartitioner(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalRegistros = ArchivoCsvUtil.contarRegistros(resource);
        Map<String, ExecutionContext> particiones = new LinkedHashMap<>();

        if (totalRegistros == 0) {
            ExecutionContext context = new ExecutionContext();
            context.putInt("startItem", 1);
            context.putInt("endItem", 0);
            context.putString("partitionName", "partition0");
            particiones.put("partition0", context);
            return particiones;
        }

        int tamanoPorParticion = (int) Math.ceil((double) totalRegistros / gridSize);
        int inicio = 1;
        int indice = 0;
        while (inicio <= totalRegistros) {
            int fin = Math.min(inicio + tamanoPorParticion - 1, totalRegistros);
            ExecutionContext context = new ExecutionContext();
            context.putInt("startItem", inicio);
            context.putInt("endItem", fin);
            context.putString("partitionName", "partition" + indice);
            particiones.put("partition" + indice, context);
            inicio = fin + 1;
            indice++;
        }
        return particiones;
    }
}
