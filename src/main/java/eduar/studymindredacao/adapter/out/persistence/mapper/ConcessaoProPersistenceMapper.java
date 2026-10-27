package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.adapter.out.persistence.entity.ConcessaoProEntity;
import eduar.studymindredacao.domain.model.ConcessaoPro;

public final class ConcessaoProPersistenceMapper {
    private ConcessaoProPersistenceMapper() {
    }

    public static ConcessaoPro toDomain(ConcessaoProEntity entity) {
        return new ConcessaoPro(
                entity.getId(),
                entity.getUsuarioId(),
                entity.getConcedidaPor(),
                entity.getMotivo(),
                entity.getInicio(),
                entity.getFim(),
                entity.getRevogadaEm(),
                entity.getRevogadaPor(),
                entity.getMotivoRevogacao()
        );
    }

    public static ConcessaoProEntity toEntity(ConcessaoPro domain) {
        var entity = new ConcessaoProEntity();
        entity.setId(domain.id());
        entity.setUsuarioId(domain.usuarioId());
        entity.setConcedidaPor(domain.concedidaPor());
        entity.setMotivo(domain.motivo());
        entity.setInicio(domain.inicio());
        entity.setFim(domain.fim());
        entity.setRevogadaEm(domain.revogadaEm());
        entity.setRevogadaPor(domain.revogadaPor());
        entity.setMotivoRevogacao(domain.motivoRevogacao());
        return entity;
    }
}
