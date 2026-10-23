package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Sem custo informado, toda falha de IA assume INCERTA: o lado que nunca devolve a vaga global por engano. */
class FalhaDeIATest {
    private static final RuntimeException CAUSA = new IllegalStateException("causa");

    @Test
    void avaliacaoSemCustoAssumeIncertaEComCustoGuardaOInformado() {
        assertThat(new AvaliacaoIAException("m").custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new AvaliacaoIAException("m", CAUSA).custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new AvaliacaoIAException("m", CAUSA, CustoFalhaIA.COBRADA).custo()).isEqualTo(CustoFalhaIA.COBRADA);
        assertThat(new AvaliacaoIAException("m", CAUSA, null).custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new AvaliacaoIAException("m", CAUSA, CustoFalhaIA.NAO_COBRADA)).hasMessage("m").hasCause(CAUSA);
    }

    @Test
    void transcricaoSemCustoAssumeIncertaEComCustoGuardaOInformado() {
        assertThat(new TranscricaoIAException("m").custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new TranscricaoIAException("m", CAUSA).custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new TranscricaoIAException("m", CAUSA, CustoFalhaIA.NAO_COBRADA).custo())
                .isEqualTo(CustoFalhaIA.NAO_COBRADA);
        assertThat(new TranscricaoIAException("m", CAUSA, null).custo()).isEqualTo(CustoFalhaIA.INCERTA);
    }

    @Test
    void juliaSemCustoAssumeIncertaEComCustoGuardaOInformado() {
        assertThat(new JuliaIndisponivelException("m").custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new JuliaIndisponivelException("m", CAUSA).custo()).isEqualTo(CustoFalhaIA.INCERTA);
        assertThat(new JuliaIndisponivelException("m", CAUSA, CustoFalhaIA.COBRADA).custo())
                .isEqualTo(CustoFalhaIA.COBRADA);
        assertThat(new JuliaIndisponivelException("m", CAUSA, null).custo()).isEqualTo(CustoFalhaIA.INCERTA);
    }

    @Test
    void fotoQueNaoERedacaoESempreCobrada() {
        assertThat(new ImagemNaoReconhecidaException().custo()).isEqualTo(CustoFalhaIA.COBRADA);
    }

    @Test
    void todasSaoFalhaDeIA() {
        assertThat(new AvaliacaoIAException("m")).isInstanceOf(FalhaDeIA.class);
        assertThat(new TranscricaoIAException("m")).isInstanceOf(FalhaDeIA.class);
        assertThat(new JuliaIndisponivelException("m")).isInstanceOf(FalhaDeIA.class);
        assertThat(new ImagemNaoReconhecidaException()).isInstanceOf(FalhaDeIA.class);
    }
}
