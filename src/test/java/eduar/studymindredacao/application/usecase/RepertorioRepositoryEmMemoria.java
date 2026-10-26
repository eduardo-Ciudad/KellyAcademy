package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Fake simples da porta: guarda ativos e inativos e devolve só os ativos, como o adapter real.
 * Filtra por macroeixo, problema, tipo, função e busca no nome (sem acento não é simulado aqui:
 * isso fica no teste de integração do adapter). Guarda o último filtro para o teste conferir o repasse.
 * Os liberados no Grátis seguem a mesma regra da query: por problema, REP primeiro e depois o menor código.
 */
class RepertorioRepositoryEmMemoria implements RepertorioRepositoryPort {
    private final List<Repertorio> repertorios = new ArrayList<>();
    private FiltroRepertorio ultimoFiltro;
    private Boolean ultimoSomenteGratis;

    void salvar(Repertorio repertorio) {
        repertorios.add(repertorio);
    }

    FiltroRepertorio ultimoFiltro() {
        return ultimoFiltro;
    }

    Boolean ultimoSomenteGratis() {
        return ultimoSomenteGratis;
    }

    @Override
    public Pagina<Repertorio> buscar(FiltroRepertorio filtro, boolean somenteLiberadosNoGratis) {
        ultimoFiltro = filtro;
        ultimoSomenteGratis = somenteLiberadosNoGratis;
        Set<String> liberados = codigosLiberadosNoGratis();
        List<Repertorio> filtrados = repertorios.stream()
                .filter(Repertorio::ativo)
                .filter(r -> !somenteLiberadosNoGratis || liberados.contains(r.codigo()))
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
    public boolean liberadoNoGratis(String codigo) {
        return codigosLiberadosNoGratis().contains(codigo);
    }

    private Set<String> codigosLiberadosNoGratis() {
        Comparator<Repertorio> regra = Comparator.<Repertorio, Boolean>comparing(r -> !r.codigo().startsWith("REP-"))
                .thenComparing(Repertorio::codigo);
        Set<String> problemas = new HashSet<>();
        repertorios.stream().filter(Repertorio::ativo)
                .forEach(r -> r.problemas().forEach(p -> problemas.add(p.slug())));
        Set<String> liberados = new HashSet<>();
        for (String problema : problemas) {
            repertorios.stream()
                    .filter(Repertorio::ativo)
                    .filter(r -> r.problemas().stream().anyMatch(p -> p.slug().equals(problema)))
                    .min(regra)
                    .ifPresent(r -> liberados.add(r.codigo()));
        }
        return liberados;
    }

    @Override
    public Optional<Repertorio> buscarAtivoPorCodigo(String codigo) {
        return repertorios.stream().filter(Repertorio::ativo).filter(r -> r.codigo().equals(codigo)).findFirst();
    }
}
