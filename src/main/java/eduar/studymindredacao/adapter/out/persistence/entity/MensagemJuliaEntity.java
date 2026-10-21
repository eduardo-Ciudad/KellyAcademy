package eduar.studymindredacao.adapter.out.persistence.entity;

import eduar.studymindredacao.domain.julia.PapelMensagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mensagens_julia")
@Getter
@Setter
@NoArgsConstructor
public class MensagemJuliaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false, updatable = false)
    private ConversaJuliaEntity conversa;

    @Column(nullable = false, updatable = false)
    private Short ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PapelMensagem papel;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "tokens_entrada", updatable = false)
    private Integer tokensEntrada;

    @Column(name = "tokens_saida", updatable = false)
    private Integer tokensSaida;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;
}