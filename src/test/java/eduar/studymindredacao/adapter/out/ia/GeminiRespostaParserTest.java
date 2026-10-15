package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.AVALIACAO_ANULADA;
import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.AVALIACAO_V3;
import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.AVALIACAO_VALIDA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiRespostaParserTest {
    private final GeminiRespostaParser parser = new GeminiRespostaParser(JsonMapper.builder().build());

    /** Resposta já desembrulhada pelo envelope, como o GeminiClient entrega. */
    private static GeminiResposta resposta(String textoGerado) {
        return new GeminiResposta(textoGerado, "gemini-teste-001", 2100, 650);
    }

    @Test
    void interpretaAvaliacaoCompleta() {
        var resultado = parser.interpretar(resposta(AVALIACAO_VALIDA));

        assertThat(resultado.anulada()).isFalse();
        assertThat(resultado.notaTotal()).isEqualTo(640);
        assertThat(resultado.competencias()).extracting(CompetenciaAvaliada::numero).containsExactly(1, 2, 3, 4, 5);
        assertThat(resultado.pontosFortes()).containsExactly("tese explícita", "boa coesão");
        assertThat(resultado.diagnostico()).isEqualTo("A maior fragilidade está na C5.");
        assertThat(resultado.respostaBrutaJson()).startsWith("{").contains("\"diagnostico\"");
    }

    @Test
    void repassaModeloETokensDaResposta() {
        var resultado = parser.interpretar(resposta(AVALIACAO_VALIDA));

        assertThat(resultado.modelo()).isEqualTo("gemini-teste-001");
        assertThat(resultado.tokensEntrada()).isEqualTo(2100);
        assertThat(resultado.tokensSaida()).isEqualTo(650);
    }

    @Test
    void agrupaProblemasPorCompetencia() {
        var resultado = parser.interpretar(resposta(AVALIACAO_VALIDA));

        var c5 = resultado.competencias().get(4);
        var c2 = resultado.competencias().get(1);
        var c1 = resultado.competencias().get(0);
        assertThat(c5.problemasIdentificados()).containsExactly("não define quem executa a ação", "sem meio de execução");
        assertThat(c2.problemasIdentificados()).containsExactly("cita Bauman sem relacionar ao tema");
        assertThat(c1.problemasIdentificados()).isEmpty();
    }

    @Test
    void formatoV3LeProblemasDeDentroDeCadaCompetencia() {
        var resultado = parser.interpretar(resposta(AVALIACAO_V3));

        assertThat(resultado.notaTotal()).isEqualTo(880);
        assertThat(resultado.competencias().get(1).problemasIdentificados()).containsExactly(
                "repertório citado sem conceito nem ligação com o tema — "
                        + "\"Pierre Bourdieu estudou como as desigualdades podem ser reproduzidas\"");
        assertThat(resultado.competencias().get(0).problemasIdentificados()).isEmpty();
    }

    @Test
    void formatoV3ProblemaSemTrechoGuardaSoADescricao() {
        var resultado = parser.interpretar(resposta(AVALIACAO_V3));

        assertThat(resultado.competencias().get(3).problemasIdentificados()).containsExactly(
                "expressão repetida nos dois desenvolvimentos — \"cabe destacar\"",
                "falta conectivo entre os desenvolvimentos");
    }

    @Test
    void formatoV3MantemAnaliseNaRespostaBruta() {
        var resultado = parser.interpretar(resposta(AVALIACAO_V3));

        assertThat(resultado.respostaBrutaJson()).contains("\"analise\"").contains("Repertório só mencionado");
    }

    @Test
    void interpretaRedacaoAnulada() {
        var resultado = parser.interpretar(resposta(AVALIACAO_ANULADA));

        assertThat(resultado.anulada()).isTrue();
        assertThat(resultado.motivoAnulacao()).isEqualTo("Fuga total ao tema");
        assertThat(resultado.notaTotal()).isZero();
    }

    @Test
    void motivoNuloNaoViraTextoNull() {
        var resultado = parser.interpretar(resposta(AVALIACAO_VALIDA));

        assertThat(resultado.motivoAnulacao()).isNull();
    }

    @Test
    void rejeitaNotaForaDosNiveisOficiais() {
        String notaInvalida = AVALIACAO_VALIDA.replace("\"nota\": 160, \"nivel_referencia\": \"poucos desvios\"",
                "\"nota\": 150, \"nivel_referencia\": \"poucos desvios\"");

        assertThatThrownBy(() -> parser.interpretar(resposta(notaInvalida)))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("inconsistente");
    }

    @Test
    void rejeitaAvaliacaoComMenosDeCincoCompetencias() {
        String quatro = AVALIACAO_VALIDA.replace(
                ",\n    {\"competencia\": \"C5\", \"nota\": 80, \"nivel_referencia\": \"proposta insuficiente\", \"resumo\": \"Falta agente.\"}", "");

        assertThatThrownBy(() -> parser.interpretar(resposta(quatro)))
                .isInstanceOf(GeminiException.class);
    }

    @Test
    void rejeitaTextoGeradoQueNaoEJson() {
        assertThatThrownBy(() -> parser.interpretar(resposta("Aqui está a avaliação: nota 800")))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("não é JSON");
    }

    @Test
    void falhasDeConteudoPodemSerRepetidas() {
        assertThatThrownBy(() -> parser.interpretar(resposta("nota 800")))
                .isInstanceOfSatisfying(GeminiException.class, e -> assertThat(e.podeRepetir()).isTrue());
    }
}