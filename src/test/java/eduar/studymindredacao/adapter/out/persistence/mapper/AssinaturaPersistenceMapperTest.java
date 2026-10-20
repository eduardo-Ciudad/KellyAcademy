package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaPersistenceMapperTest {
    private static final OffsetDateTime CRIADO = OffsetDateTime.of(2026, 10, 20, 17, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void idaEVoltaPreservaTodosOsCampos() {
        var original = new Assinatura(UUID.randomUUID(), UUID.randomUUID(), "sub_abc", CicloAssinatura.ANUAL,
                StatusAssinatura.PAST_DUE, CRIADO.plusYears(1), true, CRIADO, CRIADO.plusDays(2));

        var voltou = AssinaturaPersistenceMapper.toDomain(AssinaturaPersistenceMapper.toEntity(original));

        assertThat(voltou).isEqualTo(original);
    }

    @Test
    void assinaturaNovaViraEntitySemId() {
        var nova = Assinatura.nova(UUID.randomUUID(), "sub_abc", CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, CRIADO.plusMonths(1), false, CRIADO);

        var entity = AssinaturaPersistenceMapper.toEntity(nova);

        assertThat(entity.getId()).as("o Hibernate gera no insert").isNull();
        assertThat(entity.getCriadoEm()).isEqualTo(CRIADO);
    }
}