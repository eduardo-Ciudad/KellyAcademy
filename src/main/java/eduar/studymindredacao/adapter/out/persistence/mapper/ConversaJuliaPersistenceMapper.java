package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.adapter.out.persistence.entity.ConversaJuliaEntity;
import eduar.studymindredacao.adapter.out.persistence.entity.MensagemJuliaEntity;
import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

public final class ConversaJuliaPersistenceMapper {
    private ConversaJuliaPersistenceMapper() {
    }

    public static ConversaJulia toDomain(ConversaJuliaEntity entity) {
        List<MensagemJulia> mensagens = entity.getMensagens().stream()
                .sorted(Comparator.comparing(MensagemJuliaEntity::getOrdem))
                .map(ConversaJuliaPersistenceMapper::toDomain)
                .toList();

        return ConversaJulia.reconstituir(
                entity.getId(),
                entity.getUsuarioId(),
                entity.getRedacaoId(),
                entity.getStatus(),
                mensagens,
                toInstant(entity.getCriadaEm()),
                toInstant(entity.getUltimaAtividadeEm()),
                toInstant(entity.getEncerradaEm())
        );
    }

    public static ConversaJuliaEntity toEntity(ConversaJulia domain) {
        var entity = new ConversaJuliaEntity();
        entity.setId(domain.getId());
        entity.setUsuarioId(domain.getUsuarioId());
        entity.setRedacaoId(domain.getRedacaoId());
        entity.setStatus(domain.getStatus());
        entity.setCriadaEm(toOffset(domain.getCriadaEm()));
        entity.setUltimaAtividadeEm(toOffset(domain.getUltimaAtividadeEm()));
        entity.setEncerradaEm(toOffset(domain.getEncerradaEm()));

        List<MensagemJulia> mensagens = domain.getMensagens();
        for (int i = 0; i < mensagens.size(); i++) {
            entity.adicionarMensagem(toEntity(mensagens.get(i), (short) i));
        }
        return entity;
    }

    private static MensagemJulia toDomain(MensagemJuliaEntity entity) {
        return new MensagemJulia(
                entity.getId(),
                entity.getPapel(),
                entity.getConteudo(),
                entity.getTokensEntrada(),
                entity.getTokensSaida(),
                toInstant(entity.getCriadaEm())
        );
    }

    private static MensagemJuliaEntity toEntity(MensagemJulia domain, short ordem) {
        var entity = new MensagemJuliaEntity();
        entity.setId(domain.id());
        entity.setOrdem(ordem);
        entity.setPapel(domain.papel());
        entity.setConteudo(domain.conteudo());
        entity.setTokensEntrada(domain.tokensEntrada());
        entity.setTokensSaida(domain.tokensSaida());
        entity.setCriadaEm(toOffset(domain.criadaEm()));
        return entity;
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime data) {
        return data == null ? null : data.toInstant();
    }
}
