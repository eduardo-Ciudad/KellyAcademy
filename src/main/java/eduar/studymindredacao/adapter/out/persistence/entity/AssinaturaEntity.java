package eduar.studymindredacao.adapter.out.persistence.entity;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assinaturas")
@Getter
@Setter
@NoArgsConstructor
public class AssinaturaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "stripe_subscription_id", nullable = false, updatable = false, length = 255)
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CicloAssinatura ciclo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusAssinatura status;

    @Column(name = "periodo_atual_fim", nullable = false)
    private OffsetDateTime periodoAtualFim;

    @Column(name = "cancelar_no_fim_do_periodo", nullable = false)
    private boolean cancelarNoFimDoPeriodo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
}