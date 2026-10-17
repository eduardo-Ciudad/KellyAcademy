package eduar.studymindredacao.adapter.out.persistence.entity;

import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Linha de tokens_usuario (V16). O dono fica só como id, sem @ManyToOne: o token nunca precisa
 * carregar o usuário, e as operações são UPDATE/DELETE diretos por hash ou por usuário.
 */
@Entity
@Table(name = "tokens_usuario")
@Getter
@Setter
@NoArgsConstructor
public class TokenUsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoTokenUsuario tipo;

    // CHAR(64) no banco: sem o JdbcTypeCode o ddl-auto=validate espera varchar e a API não sobe
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    @Override
    public String toString() {
        return "TokenUsuarioEntity[id=" + id + ", usuarioId=" + usuarioId + ", tipo=" + tipo + ", tokenHash=***]";
    }
}
