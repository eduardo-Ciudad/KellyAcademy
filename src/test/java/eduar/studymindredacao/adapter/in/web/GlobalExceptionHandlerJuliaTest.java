package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.julia.LimiteJuliaExcedidoException;
import eduar.studymindredacao.domain.julia.PerguntaInvalidaException;
import eduar.studymindredacao.domain.julia.RedacaoSemCorrecaoException;
import eduar.studymindredacao.domain.julia.TipoLimiteJulia;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerJuliaTest {
    // 22:30 em São Paulo (01:30 UTC do dia seguinte): faltam 1h30 = 5400s para a meia-noite local
    private final Clock relogio = Clock.fixed(Instant.parse("2026-09-29T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(relogio);

    @Test
    void perguntaInvalidaResponde400ComAMensagemDoDominio() {
        var problema = handler.perguntaInvalida(new PerguntaInvalidaException("A pergunta não pode ficar vazia."));

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getDetail()).isEqualTo("A pergunta não pode ficar vazia.");
    }

    @Test
    void redacaoSemCorrecaoResponde409ComCodigo() {
        var problema = handler.redacaoSemCorrecao(new RedacaoSemCorrecaoException());

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getProperties()).containsEntry("codigo", "JULIA_SEM_CORRECAO");
    }

    @Test
    void limiteDaConversaResponde429SemRetryAfter() {
        var resposta = handler.limiteJulia(new LimiteJuliaExcedidoException(TipoLimiteJulia.CONVERSA));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resposta.getHeaders().containsHeader(HttpHeaders.RETRY_AFTER)).isFalse();
        assertThat(resposta.getBody().getProperties()).containsEntry("codigo", "JULIA_LIMITE_CONVERSA");
        assertThat(resposta.getBody().getDetail()).contains("conversa");
    }

    @Test
    void limiteDiarioResponde429ComRetryAfterAteMeiaNoiteLocal() {
        var resposta = handler.limiteJulia(new LimiteJuliaExcedidoException(TipoLimiteJulia.DIARIO_USUARIO));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5400");
        assertThat(resposta.getBody().getProperties()).containsEntry("codigo", "JULIA_LIMITE_DIARIO");
    }

    @Test
    void tetoGlobalResponde503ComRetryAfter() {
        var resposta = handler.limiteJulia(new LimiteJuliaExcedidoException(TipoLimiteJulia.GLOBAL));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5400");
        assertThat(resposta.getBody().getProperties()).containsEntry("codigo", "JULIA_LIMITE_GLOBAL");
    }

    @Test
    void juliaIndisponivelResponde503ComMensagemFixaSemVazarDetalhe() {
        var problema = handler.juliaIndisponivel(
                new JuliaIndisponivelException("Gemini recusou a requisição (HTTP 403) chave=xyz"));

        assertThat(problema.getStatus()).isEqualTo(503);
        assertThat(problema.getProperties()).containsEntry("codigo", "JULIA_INDISPONIVEL");
        assertThat(problema.getDetail())
                .isEqualTo(GlobalExceptionHandler.MENSAGEM_JULIA_INDISPONIVEL)
                .doesNotContain("Gemini").doesNotContain("403").doesNotContain("xyz");
    }
}
