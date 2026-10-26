package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.RepertorioEntity;
import eduar.studymindredacao.adapter.out.persistence.mapper.RepertorioPersistenceMapper;
import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class RepertorioRepositoryAdapter implements RepertorioRepositoryPort {
    private final RepertorioJpaRepository repertorios;

    public RepertorioRepositoryAdapter(RepertorioJpaRepository repertorios) {
        this.repertorios = repertorios;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Repertorio> buscar(FiltroRepertorio filtro, boolean somenteLiberadosNoGratis) {
        String tipo = filtro.tipo() == null ? null : filtro.tipo().name();
        String funcao = filtro.funcao() == null ? null : filtro.funcao().name();
        String busca = escaparLike(filtro.busca());
        long inicio = (long) filtro.pagina() * filtro.tamanho();

        List<UUID> ids = repertorios.buscarIds(tipo, funcao, filtro.macroeixo(), filtro.problema(), busca,
                somenteLiberadosNoGratis, filtro.tamanho(), inicio);
        long total = repertorios.contar(tipo, funcao, filtro.macroeixo(), filtro.problema(), busca,
                somenteLiberadosNoGratis);

        return new Pagina<>(carregarNaOrdem(ids), filtro.pagina(), filtro.tamanho(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Repertorio> buscarAtivoPorCodigo(String codigo) {
        return repertorios.findByCodigoAndAtivoTrue(codigo).map(RepertorioPersistenceMapper::toDomain);
    }

    /**
     * Sobrescreve o default da porta só para ter a transação: o default chamaria o outro método por dentro
     * (sem passar pelo proxy) e o carregamento das fontes e problemas ficaria fora de uma sessão.
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina<Repertorio> buscar(FiltroRepertorio filtro) {
        return buscar(filtro, false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean liberadoNoGratis(String codigo) {
        return codigo != null && repertorios.liberadoNoGratis(codigo);
    }

    /** findAllById não garante ordem: reordena pela lista de ids que veio da consulta paginada. */
    private List<Repertorio> carregarNaOrdem(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, RepertorioEntity> porId = repertorios.findAllById(ids).stream()
                .collect(Collectors.toMap(RepertorioEntity::getId, Function.identity()));
        return ids.stream()
                .map(porId::get)
                .map(RepertorioPersistenceMapper::toDomain)
                .toList();
    }

    /** O texto do aluno é procurado literalmente: % e _ não viram curinga do LIKE. */
    static String escaparLike(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
