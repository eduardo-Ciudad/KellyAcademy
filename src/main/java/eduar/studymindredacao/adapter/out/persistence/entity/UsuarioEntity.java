package eduar.studymindredacao.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import eduar.studymindredacao.domain.model.enums.Role;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /*
     * senha_hash, versao_sessao e senha_alterada_em só são gravados no INSERT (cadastro). Depois disso mudam
     * apenas pelo UPDATE atômico de UsuarioJpaRepository.alterarSenha. Assim um salvar() de outro fluxo
     * (ex.: confirmar o e-mail) com um Usuario carregado antes da troca não regrava a senha antiga nem a
     * versão antiga por cima, o que desfaria a troca e reabriria as sessões derrubadas.
     */
    @Column(name = "senha_hash", nullable = false, length = 255, updatable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "email_verificado_em")
    private OffsetDateTime emailVerificadoEm;

    @Column(name = "versao_sessao", nullable = false, updatable = false)
    private int versaoSessao;

    @Column(name = "senha_alterada_em", updatable = false)
    private OffsetDateTime senhaAlteradaEm;
}
