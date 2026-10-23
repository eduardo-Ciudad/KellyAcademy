package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiExceptionTest {

    @Test
    void repetivelPermiteNovaTentativa() {
        var excecao = GeminiException.repetivel("resposta sem candidatos");

        assertThat(excecao.podeRepetir()).isTrue();
        assertThat(excecao).hasMessage("resposta sem candidatos").hasNoCause();
    }

    @Test
    void repetivelGuardaACausa() {
        var causa = new IOException("timeout");

        var excecao = GeminiException.repetivel("Gemini indisponível", causa);

        assertThat(excecao.podeRepetir()).isTrue();
        assertThat(excecao).hasCause(causa);
    }

    @Test
    void definitivaNaoPermiteNovaTentativa() {
        var causa = new IllegalStateException("HTTP 429");

        var excecao = GeminiException.definitiva("Gemini recusou a requisição (HTTP 429)", causa);

        assertThat(excecao.podeRepetir()).isFalse();
        assertThat(excecao).hasMessageContaining("HTTP 429").hasCause(causa);
    }

    @Test
    void semCustoInformadoValeIncerta() {
        assertThat(GeminiException.repetivel("x").custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(GeminiException.definitiva("x", null).custo()).isEqualTo(CustoFalhaIA.INCERTA);
    }

    @Test
    void comCustoMantemMensagemCausaERegraDeRepeticao() {
        var causa = new IOException("timeout");
        var original = GeminiException.repetivel("Gemini indisponível", causa);

        var comCusto = original.comCusto(CustoFalhaIA.COBRADA);

        assertThat(comCusto.custo()).isEqualTo(CustoFalhaIA.COBRADA);
        assertThat(comCusto).hasMessage("Gemini indisponível").hasCause(causa);
        assertThat(comCusto.podeRepetir()).isTrue();
        assertThat(comCusto.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(original.custo()).isEqualTo(CustoFalhaIA.INCERTA);
    }
}