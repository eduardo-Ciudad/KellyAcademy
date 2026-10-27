package eduar.studymindredacao.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Só as colunas da revogação podem mudar depois do INSERT: o que foi concedido não se reescreve. */
@Entity
@Table(name = "concessoes_pro")
@Getter
@Setter
@NoArgsConstructor
public class ConcessaoProEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "concedida_por", nullable = false, updatable = false)
    private UUID concedidaPor;

    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime inicio;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime fim;

    @Column(name = "revogada_em")
    private OffsetDateTime revogadaEm;

    @Column(name = "revogada_por")
    private UUID revogadaPor;

    @Column(name = "motivo_revogacao", length = 300)
    private String motivoRevogacao;
}
