package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.UsoIAGlobalRepositoryPort;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Imita a reserva atômica do adapter real. Métodos synchronized para os testes com threads;
 * a concorrência de verdade é provada contra o Postgres no UsoIAGlobalIntegrationTest.
 */
class UsoIAGlobalEmMemoria implements UsoIAGlobalRepositoryPort {
    private record Chave(RecursoIA recurso, LocalDate data) {
    }

    private final Map<Chave, Integer> quantidades = new HashMap<>();
    private boolean falharAoLiberar;

    @Override
    public synchronized boolean reservar(RecursoIA recurso, LocalDate data, int teto) {
        var chave = new Chave(recurso, data);
        int atual = quantidades.getOrDefault(chave, 0);
        if (teto <= 0 || atual >= teto) {
            return false;
        }
        quantidades.put(chave, atual + 1);
        return true;
    }

    @Override
    public synchronized void liberar(RecursoIA recurso, LocalDate data) {
        if (falharAoLiberar) {
            throw new IllegalStateException("banco indisponível ao devolver a vaga global");
        }
        quantidades.computeIfPresent(new Chave(recurso, data), (chave, qtd) -> Math.max(qtd - 1, 0));
    }

    @Override
    public synchronized int quantidadeDoDia(RecursoIA recurso, LocalDate data) {
        return quantidades.getOrDefault(new Chave(recurso, data), 0);
    }

    synchronized void falharAoLiberar() {
        this.falharAoLiberar = true;
    }
}
