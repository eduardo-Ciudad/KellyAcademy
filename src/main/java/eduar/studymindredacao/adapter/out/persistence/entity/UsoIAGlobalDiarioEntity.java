package eduar.studymindredacao.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contador global do dia por recurso (V24). Escrita só por query nativa atômica (reserva/liberação);
 * a entidade existe para o Spring Data e para o ddl validate conferir que o mapeamento bate com a migration.
 */
@Entity
@Table(name = "uso_ia_global_diario")
@Getter
@Setter
@NoArgsConstructor
public class UsoIAGlobalDiarioEntity {
    @EmbeddedId
    private UsoIAGlobalDiarioId id;

    @Column(name = "quantidade", nullable = false)
    private Integer quantidade;
}
