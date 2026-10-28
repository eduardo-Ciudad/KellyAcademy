package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.OrigemTema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemaTest {
    private static final UUID ALUNO = UUID.randomUUID();
    private static final UUID OUTRO_ALUNO = UUID.randomUUID();
    private static final String TITULO = "Os desafios da mobilidade urbana no Brasil";

    // ---------- personalizado ----------

    @Test
    void personalizadoPreencheOsCamposENaoTemId() {
        var tema = Tema.personalizado(TITULO, ALUNO);

        assertThat(tema.id()).isNull();
        assertThat(tema.titulo()).isEqualTo(TITULO);
        assertThat(tema.origem()).isEqualTo(OrigemTema.PERSONALIZADO);
        assertThat(tema.criadoPorId()).isEqualTo(ALUNO);
        assertThat(tema.ativo()).isTrue();
        assertThat(tema.ano()).isNull();
        assertThat(tema.textosMotivadores()).isNull();
    }

    @Test
    void personalizadoTiraAsPontasEJuntaEspacosRepetidos() {
        var tema = Tema.personalizado("   Os desafios   da mobilidade\n\turbana no Brasil  ", ALUNO);

        assertThat(tema.titulo()).isEqualTo("Os desafios da mobilidade urbana no Brasil");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void personalizadoExigeTitulo(String titulo) {
        assertThatThrownBy(() -> Tema.personalizado(titulo, ALUNO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("titulo é obrigatório");
    }

    @Test
    void personalizadoAceitaExatamente10Caracteres() {
        assertThat(Tema.personalizado("a".repeat(10), ALUNO).titulo()).hasSize(10);
    }

    @Test
    void personalizadoRecusaMenosDe10CaracteresDepoisDeNormalizar() {
        // 12 caracteres com os espaços, 9 depois de normalizar
        assertThatThrownBy(() -> Tema.personalizado("  abcd efgh ", ALUNO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no mínimo 10");
    }

    @Test
    void personalizadoAceitaExatamente200Caracteres() {
        assertThat(Tema.personalizado("a".repeat(200), ALUNO).titulo()).hasSize(200);
    }

    @Test
    void personalizadoRecusaMaisDe200Caracteres() {
        assertThatThrownBy(() -> Tema.personalizado("a".repeat(201), ALUNO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no máximo 200");
    }

    @Test
    void personalizadoExigeDono() {
        assertThatThrownBy(() -> Tema.personalizado(TITULO, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("usuarioId");
    }

    // ---------- regra do dono ----------

    @Test
    void personalizadoSemDonoNaoExiste() {
        assertThatThrownBy(() -> new Tema(null, TITULO, null, OrigemTema.PERSONALIZADO, null, true, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dono");
    }

    @ParameterizedTest
    @EnumSource(value = OrigemTema.class, names = "PERSONALIZADO", mode = EnumSource.Mode.EXCLUDE)
    void temaDoSistemaNaoPodeTerDono(OrigemTema origem) {
        assertThatThrownBy(() -> new Tema(null, TITULO, null, origem, null, true, null, ALUNO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dono");
    }

    @Test
    void construtorSemDonoContinuaValendoParaTemasDoSistema() {
        var tema = new Tema(null, TITULO, null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null);

        assertThat(tema.criadoPorId()).isNull();
    }

    // ---------- visivelPara ----------

    @Test
    void personalizadoSoEhVisivelParaODono() {
        var tema = Tema.personalizado(TITULO, ALUNO);

        assertThat(tema.visivelPara(ALUNO)).isTrue();
        assertThat(tema.visivelPara(OUTRO_ALUNO)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrigemTema.class, names = "PERSONALIZADO", mode = EnumSource.Mode.EXCLUDE)
    void temaDoSistemaEhVisivelParaTodos(OrigemTema origem) {
        var tema = new Tema(null, TITULO, null, origem, null, true, null);

        assertThat(tema.visivelPara(ALUNO)).isTrue();
        assertThat(tema.visivelPara(OUTRO_ALUNO)).isTrue();
    }
}
