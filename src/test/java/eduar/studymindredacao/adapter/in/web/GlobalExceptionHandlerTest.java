package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.TentativasExcedidasException;
import eduar.studymindredacao.domain.exception.AvaliacaoIAException;
import eduar.studymindredacao.domain.exception.CadastroFechadoException;
import eduar.studymindredacao.domain.exception.EmailNaoVerificadoException;
import eduar.studymindredacao.domain.exception.ImagemInvalidaException;
import eduar.studymindredacao.domain.exception.ImagemNaoReconhecidaException;
import eduar.studymindredacao.domain.exception.LimiteGlobalAtingidoException;
import eduar.studymindredacao.domain.exception.LimiteDiarioAtingidoException;
import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.exception.TemaNaoAdicionavelException;
import eduar.studymindredacao.domain.exception.TemaNaoEncontradoException;
import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.exception.TranscricaoIAException;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    // 22:30 em São Paulo (01:30 UTC do dia seguinte): faltam 1h30 = 5400s para a meia-noite local
    private final Clock relogio = Clock.fixed(Instant.parse("2026-09-29T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(relogio);

    @Test
    void limiteDiarioResponde429ComLimiteERetryAfterAteMeiaNoiteLocal() {
        var resposta = handler.limiteDiario(new LimiteDiarioAtingidoException(RecursoIA.CORRECAO, 3));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5400");
        assertThat(resposta.getBody().getProperties()).containsEntry("limite", 3).containsEntry("recurso", "CORRECAO");
        assertThat(resposta.getBody().getDetail()).isEqualTo("Limite diário de 3 correções atingido. Tente novamente amanhã.");
    }

    @Test
    void limiteDiarioDaTranscricaoIdentificaORecurso() {
        var resposta = handler.limiteDiario(new LimiteDiarioAtingidoException(RecursoIA.TRANSCRICAO, 3));

        assertThat(resposta.getBody().getProperties()).containsEntry("recurso", "TRANSCRICAO");
        assertThat(resposta.getBody().getDetail()).contains("3 transcrições");
    }

    @Test
    void falhaDaIAResponde502SemVazarDetalheInterno() {
        var problema = handler.falhaNaCorrecao(new AvaliacaoIAException("Gemini recusou a requisição (HTTP 403) chave=xyz"));

        assertThat(problema.getStatus()).isEqualTo(502);
        assertThat(problema.getDetail()).doesNotContain("Gemini").doesNotContain("403").doesNotContain("xyz");
    }

    @Test
    void imagemInvalidaResponde400ComOMotivo() {
        var problema = handler.imagemInvalida(new ImagemInvalidaException(
                ImagemInvalidaException.Motivo.TIPO_INVALIDO, "Formato de imagem não suportado."));

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getProperties()).containsEntry("motivo", "TIPO_INVALIDO");
    }

    @Test
    void uploadAcimaDoLimiteResponde413ComMotivoGrandeDemais() {
        var problema = handler.uploadGrandeDemais(new MaxUploadSizeExceededException(5 * 1024 * 1024));

        assertThat(problema.getStatus()).isEqualTo(413);
        assertThat(problema.getProperties()).containsEntry("motivo", "GRANDE_DEMAIS");
    }

    @Test
    void fotoSemRedacaoResponde422ComOrientacao() {
        var problema = handler.imagemNaoReconhecida(new ImagemNaoReconhecidaException());

        assertThat(problema.getStatus()).isEqualTo(422);
        assertThat(problema.getDetail()).isEqualTo(ImagemNaoReconhecidaException.MENSAGEM);
    }

    @Test
    void falhaDaIANaTranscricaoResponde502SemVazarDetalheInterno() {
        var problema = handler.falhaNaTranscricao(new TranscricaoIAException("Gemini recusou a requisição (HTTP 403) chave=xyz"));

        assertThat(problema.getStatus()).isEqualTo(502);
        assertThat(problema.getDetail()).contains("ler a foto").doesNotContain("Gemini").doesNotContain("xyz");
    }

    @Test
    void temaERedacaoInexistentesRespondem404() {
        assertThat(handler.naoEncontrado(new TemaNaoEncontradoException(UUID.randomUUID())).getStatus()).isEqualTo(404);
        assertThat(handler.naoEncontrado(new RedacaoNaoEncontradaException(UUID.randomUUID())).getStatus()).isEqualTo(404);
    }

    @Test
    void temaNaoAdicionavelResponde400() {
        assertThat(handler.temaNaoAdicionavel(new TemaNaoAdicionavelException(UUID.randomUUID())).getStatus()).isEqualTo(400);
    }

    @Test
    void tetoGlobalResponde503ComRetryAfter() {
        var resposta = handler.limiteGlobal(new LimiteGlobalAtingidoException(RecursoIA.TRANSCRICAO));

        assertThat(resposta.getStatusCode().value()).isEqualTo(503);
        assertThat(resposta.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(resposta.getBody().getProperties()).containsEntry("recurso", "TRANSCRICAO");
    }

    @Test
    void tentativasExcedidasResponde429ComOsSegundosDaJanela() {
        var resposta = handler.tentativasExcedidas(new TentativasExcedidasException(42));

        assertThat(resposta.getStatusCode().value()).isEqualTo(429);
        assertThat(resposta.getHeaders().getFirst("Retry-After")).isEqualTo("42");
    }

    @Test
    void cadastroFechadoResponde403() {
        assertThat(handler.cadastroFechado(new CadastroFechadoException()).getStatus()).isEqualTo(403);
    }

    @Test
    void retryAfterNuncaEZero() {
        var quaseMeiaNoite = Clock.fixed(Instant.parse("2026-09-29T02:59:59.900Z"), ZoneId.of("America/Sao_Paulo"));

        assertThat(new GlobalExceptionHandler(quaseMeiaNoite).segundosAteAmanha()).isEqualTo(1);
    }

    @Test
    void emailNaoVerificadoResponde403ComCodigoProprio() {
        var problema = handler.emailNaoVerificado(new EmailNaoVerificadoException());

        assertThat(problema.getStatus()).isEqualTo(403);
        assertThat(problema.getProperties()).containsEntry("codigo", "EMAIL_NAO_VERIFICADO");
        assertThat(problema.getDetail()).contains("Confirme seu e-mail");
    }

    @Test
    void cadastroFechadoContinua403SemOCodigoDoEmail() {
        var problema = handler.cadastroFechado(new CadastroFechadoException());

        assertThat(problema.getStatus()).isEqualTo(403);
        assertThat(problema.getProperties() == null ? java.util.Map.of() : problema.getProperties())
                .doesNotContainKey("codigo");
    }

    @Test
    void tokenInvalidoResponde400ComMensagemUnica() {
        var problema = handler.tokenInvalido(new TokenInvalidoException());

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getDetail()).isEqualTo("Link inválido ou expirado. Peça um novo link.");
        assertThat(problema.getProperties()).containsEntry("codigo", "TOKEN_INVALIDO");
    }
}
