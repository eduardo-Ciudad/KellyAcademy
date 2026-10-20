package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.adapter.out.persistence.entity.AssinaturaEntity;
import eduar.studymindredacao.domain.model.Assinatura;

public final class AssinaturaPersistenceMapper {
    private AssinaturaPersistenceMapper() {
    }

    public static Assinatura toDomain(AssinaturaEntity entity) {
        return new Assinatura(
                entity.getId(),
                entity.getUsuarioId(),
                entity.getStripeSubscriptionId(),
                entity.getCiclo(),
                entity.getStatus(),
                entity.getPeriodoAtualFim(),
                entity.isCancelarNoFimDoPeriodo(),
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }

    public static AssinaturaEntity toEntity(Assinatura domain) {
        var entity = new AssinaturaEntity();
        entity.setId(domain.id());
        entity.setUsuarioId(domain.usuarioId());
        entity.setStripeSubscriptionId(domain.stripeSubscriptionId());
        entity.setCiclo(domain.ciclo());
        entity.setStatus(domain.status());
        entity.setPeriodoAtualFim(domain.periodoAtualFim());
        entity.setCancelarNoFimDoPeriodo(domain.cancelarNoFimDoPeriodo());
        entity.setCriadoEm(domain.criadoEm());
        entity.setAtualizadoEm(domain.atualizadoEm());
        return entity;
    }
}