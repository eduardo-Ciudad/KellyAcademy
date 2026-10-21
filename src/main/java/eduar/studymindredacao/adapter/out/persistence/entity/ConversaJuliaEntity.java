package eduar.studymindredacao.adapter.out.persistence.entity;

import eduar.studymindredacao.domain.julia.StatusConversa;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversas_julia")
@Getter
@Setter
@NoArgsConstructor
public class ConversaJuliaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "redacao_id", nullable = false, updatable = false)
    private UUID redacaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusConversa status;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "ultima_atividade_em", nullable = false)
    private OffsetDateTime ultimaAtividadeEm;

    @Column(name = "encerrada_em")
    private OffsetDateTime encerradaEm;

    @OneToMany(mappedBy = "conversa", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    private List<MensagemJuliaEntity> mensagens = new ArrayList<>();

    public void adicionarMensagem(MensagemJuliaEntity mensagem) {
        mensagem.setConversa(this);
        mensagens.add(mensagem);
    }
}