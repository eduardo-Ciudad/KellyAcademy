package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fake simples da porta: guarda ativos e inativos e devolve só os ativos, como o adapter real.
 * Filtra por macroeixo, problema, tipo, função e busca no nome (sem acento não é simulado aqui:
 * isso fica no teste de integração do adapter). Guarda o último filtro para o teste conferir o repasse.
 */
class RepertorioRepositoryEmMemoria implements RepertorioRepositoryPort {
    private final List<Repertorio> repertorios = new ArrayList<>();
    private FiltroRepertorio ultimoFiltro;

    void salvar(Repertorio repertorio) {
        repertorios.add(repertorio);
    }

    FiltroRepertorio ultimoFiltro() {
        return ultimoFiltro;
    }

    @Override
    public Pagina<Repertorio> buscar(FiltroRepertorio filtro) {
        ultimoFiltro = filtro;
        List<Repertorio> filtrados = repertorios.stream()
                .filter(Repertorio::ativo)
                .filter(r -> filtro.macroeixo() == null
                        || r.problemas().stream().anyMatch(p -> p.macroeixoSlug().equals(filtro.macroeixo())))
                .filter(r -> filtro.problema() == null
                        || r.problemas().stream().anyMatch(p -> p.slug().equals(filtro.problema())))
                .filter(r -> filtro.tipo() == null || r.tipoEntidade() == filtro.tipo())
                .filter(r -> filtro.funcao() == null || r.funcoesArgumentativas().contains(filtro.funcao()))
                .filter(r -> filtro.busca() == null
                        || r.nome().toLowerCase(Locale.ROOT).contains(filtro.busca().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Repertorio::nome).thenComparing(Repertorio::codigo))
                .toList();

        int inicio = Math.min(filtro.pagina() * filtro.tamanho(), filtrados.size());
        int fim = Math.min(inicio + filtro.tamanho(), filtrados.size());
        return new Pagina<>(filtrados.subList(inicio, fim), filtro.pagina(), filtro.tamanho(), filtrados.size());
    }

    @Override
    public Optional<Repertorio> buscarAtivoPorCodigo(String codigo) {
        return repertorios.stream().filter(Repertorio::ativo).filter(r -> r.codigo().equals(codigo)).findFirst();
    }
}
