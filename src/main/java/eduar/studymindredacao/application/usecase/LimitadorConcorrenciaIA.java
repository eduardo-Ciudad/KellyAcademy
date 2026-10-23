package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.IAOcupadaException;
import eduar.studymindredacao.domain.exception.IAOcupadaException.Motivo;
import eduar.studymindredacao.domain.model.enums.RecursoIA;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class LimitadorConcorrenciaIA {
    private record Chave(UUID usuarioId, RecursoIA recurso) {
    }

    private final Map<RecursoIA, Semaphore> vagasPorRecurso = new EnumMap<>(RecursoIA.class);
    private final Set<Chave> emAndamento = ConcurrentHashMap.newKeySet();

    /** Todo recurso do enum precisa de um máximo maior que zero; senão a API não sobe. */
    public LimitadorConcorrenciaIA(Map<RecursoIA, Integer> maximoPorRecurso) {
        for (RecursoIA recurso : RecursoIA.values()) {
            Integer maximo = maximoPorRecurso.get(recurso);
            if (maximo == null || maximo <= 0) {
                throw new IllegalArgumentException(
                        "limite de chamadas simultâneas de " + recurso + " deve ser maior que zero");
            }
            vagasPorRecurso.put(recurso, new Semaphore(maximo));
        }
    }

    /**
     * @throws IAOcupadaException o aluno já tem uma chamada deste recurso em andamento, ou o sistema está cheio
     */
    public PermissaoIA adquirir(UUID usuarioId, RecursoIA recurso) {
        var chave = new Chave(usuarioId, recurso);
        if (!emAndamento.add(chave)) {
            throw new IAOcupadaException(recurso, Motivo.ALUNO_EM_ANDAMENTO);
        }
        Semaphore vagas = vagasPorRecurso.get(recurso);
        if (!vagas.tryAcquire()) {
            emAndamento.remove(chave);
            throw new IAOcupadaException(recurso, Motivo.SISTEMA_OCUPADO);
        }
        return new PermissaoIA(() -> {
            vagas.release();
            emAndamento.remove(chave);
        });
    }
}
