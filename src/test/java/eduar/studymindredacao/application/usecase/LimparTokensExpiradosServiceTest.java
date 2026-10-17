package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LimparTokensExpiradosServiceTest {
    private final RelogioAjustavel relogio = RelogioAjustavel.em("2026-10-17T06:17:00Z");
    private final TokenUsuarioRepositoryEmMemoria tokens = new TokenUsuarioRepositoryEmMemoria();
    private final LimparTokensExpiradosService service = new LimparTokensExpiradosService(tokens, relogio);

    private TokenUsuario token(OffsetDateTime criadoEm, Duration validade) {
        String hash = new GeradorTokenFake().hash(UUID.randomUUID().toString());
        return tokens.salvar(TokenUsuario.novo(UUID.randomUUID(), TipoTokenUsuario.VERIFICACAO_EMAIL, hash, criadoEm, validade));
    }

    @Test
    void apagaSoOQueExpirouOuFoiUsadoHaMaisDeSeteDias() {
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        token(agora.minusDays(10), Duration.ofHours(24));                        // expirou há 9 dias: sai
        var expirouOntem = token(agora.minusDays(2), Duration.ofHours(24));      // expirou há 1 dia: fica
        var ativo = token(agora.minusHours(1), Duration.ofHours(24));            // ainda vale: fica
        var usadoAntigo = token(agora.minusDays(9), Duration.ofDays(30));
        tokens.consumirValido(usadoAntigo.tokenHash(), TipoTokenUsuario.VERIFICACAO_EMAIL, agora.minusDays(9)); // usado há 9 dias: sai

        int apagados = service.limpar();

        assertThat(apagados).isEqualTo(2);
        assertThat(tokens.todos().stream().map(TokenUsuario::id).toList())
                .containsExactlyInAnyOrder(expirouOntem.id(), ativo.id());
    }

    @Test
    void semNadaParaApagarDevolveZero() {
        assertThat(service.limpar()).isZero();
    }

    @Test
    void retencaoEhDeSeteDias() {
        assertThat(LimparTokensExpiradosService.RETENCAO).isEqualTo(Duration.ofDays(7));
    }
}
