package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Token de uso único ligado a um usuário (ex.: verificação de e-mail). Guarda só o hash SHA-256:
 * se o banco vazar, os links enviados não podem ser reconstruídos.
 */
public record TokenUsuario(
        UUID id,
        UUID usuarioId,
        TipoTokenUsuario tipo,
        String tokenHash,
        OffsetDateTime criadoEm,
        OffsetDateTime expiraEm,
        OffsetDateTime usadoEm
) {
    public TokenUsuario {
        Validacoes.requererNaoNulo(usuarioId, "usuarioId");
        Validacoes.requererNaoNulo(tipo, "tipo");
        Validacoes.validarHashSha256(tokenHash, "tokenHash");
        Validacoes.requererNaoNulo(criadoEm, "criadoEm");
        Validacoes.requererNaoNulo(expiraEm, "expiraEm");
        if (!expiraEm.isAfter(criadoEm)) {
            throw new IllegalArgumentException("expiraEm deve ser depois de criadoEm");
        }
    }

    /** Token novo, ainda não usado, válido por {@code validade} a partir de {@code agora}. */
    public static TokenUsuario novo(
            UUID usuarioId,
            TipoTokenUsuario tipo,
            String tokenHash,
            OffsetDateTime agora,
            Duration validade
    ) {
        Validacoes.requererNaoNulo(agora, "agora");
        Validacoes.requererNaoNulo(validade, "validade");
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }
        return new TokenUsuario(null, usuarioId, tipo, tokenHash, agora, agora.plus(validade), null);
    }

    /** Ainda pode ser usado: nunca foi consumido e não chegou em {@code expiraEm} (o próprio instante já expira). */
    public boolean estaValido(OffsetDateTime agora) {
        Validacoes.requererNaoNulo(agora, "agora");
        return usadoEm == null && agora.isBefore(expiraEm);
    }

    @Override
    public String toString() {
        return "TokenUsuario[id=" + id + ", usuarioId=" + usuarioId + ", tipo=" + tipo
                + ", expiraEm=" + expiraEm + ", usadoEm=" + usadoEm + ", tokenHash=***]";
    }
}
