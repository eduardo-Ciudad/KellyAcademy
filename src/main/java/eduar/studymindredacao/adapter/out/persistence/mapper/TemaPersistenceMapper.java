package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.adapter.out.persistence.entity.TemaEntity;
import eduar.studymindredacao.domain.model.Tema;

public final class TemaPersistenceMapper {
    private TemaPersistenceMapper() {
    }

    public static Tema toDomain(TemaEntity entity) {
        return new Tema(
                entity.getId(),
                entity.getTitulo(),
                entity.getTextosMotivadores(),
                entity.getOrigem(),
                entity.getAno(),
                entity.getAtivo(),
                entity.getCriadoEm(),
                entity.getCriadoPorId()
        );
    }

    public static TemaEntity toEntity(Tema domain) {
        var entity = new TemaEntity();
        entity.setId(domain.id());
        entity.setTitulo(domain.titulo());
        entity.setTextosMotivadores(domain.textosMotivadores());
        entity.setOrigem(domain.origem());
        entity.setAno(domain.ano());
        entity.setAtivo(domain.ativo());
        entity.setCriadoEm(domain.criadoEm());
        entity.setCriadoPorId(domain.criadoPorId());
        return entity;
    }
}
