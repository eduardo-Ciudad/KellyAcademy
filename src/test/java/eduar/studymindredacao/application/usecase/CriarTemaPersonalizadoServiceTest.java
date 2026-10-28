package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.LimiteTemasPersonalizadosException;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriarTemaPersonalizadoServiceTest {
    private static final int LIMITE = 10;
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    // 15:00 UTC = 12:00 em São Paulo, dia 28/10
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), SAO_PAULO);
    private static final OffsetDateTime MEIA_NOITE_SP = OffsetDateTime.parse("2026-10-28T00:00:00-03:00");
    private static final String TITULO = "Os desafios da mobilidade urbana no Brasil";

    private final UUID aluno = UUID.randomUUID();
    private final UUID outroAluno = UUID.randomUUID();
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final CriarTemaPersonalizadoService service = new CriarTemaPersonalizadoService(temas, RELOGIO, LIMITE);

    /** Salva um personalizado já com a data de criação escolhida. */
    private Tema personalizadoCriadoEm(UUID dono, String titulo, OffsetDateTime quando) {
        return temas.salvar(new Tema(null, titulo, null, OrigemTema.PERSONALIZADO, null, true, quando, dono));
    }

    private void criarHoje(int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            personalizadoCriadoEm(aluno, "Tema número " + i + " de hoje", MEIA_NOITE_SP.plusHours(1));
        }
    }

    // ---------- criar ----------

    @Test
    void criaTemaNormalizadoComDono() {
        var resultado = service.criar(aluno, "  Os desafios   da mobilidade urbana no Brasil ");

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.tema().id()).isNotNull();
        assertThat(resultado.tema().titulo()).isEqualTo(TITULO);
        assertThat(resultado.tema().origem()).isEqualTo(OrigemTema.PERSONALIZADO);
        assertThat(resultado.tema().criadoPorId()).isEqualTo(aluno);
        assertThat(temas.listarPersonalizadosAtivosDo(aluno)).hasSize(1);
    }

    @Test
    void tituloInvalidoNaoSalvaNada() {
        assertThatThrownBy(() -> service.criar(aluno, "curto"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(temas.listarPersonalizadosAtivosDo(aluno)).isEmpty();
    }

    // ---------- título repetido ----------

    @Test
    void tituloRepetidoComOutraCaixaDevolveOExistenteSemCriar() {
        var primeiro = service.criar(aluno, TITULO).tema();

        var segundo = service.criar(aluno, "OS DESAFIOS da mobilidade URBANA no brasil");

        assertThat(segundo.criado()).isFalse();
        assertThat(segundo.tema().id()).isEqualTo(primeiro.id());
        assertThat(temas.listarPersonalizadosAtivosDo(aluno)).hasSize(1);
    }

    @Test
    void mesmoTituloDeOutroAlunoNaoContaComoRepetido() {
        var doOutro = service.criar(outroAluno, TITULO).tema();

        var meu = service.criar(aluno, TITULO);

        assertThat(meu.criado()).isTrue();
        assertThat(meu.tema().id()).isNotEqualTo(doOutro.id());
        assertThat(meu.tema().criadoPorId()).isEqualTo(aluno);
    }

    // ---------- limite diário ----------

    @Test
    void criaOUltimoTemaPermitidoDoDia() {
        criarHoje(LIMITE - 1);

        assertThat(service.criar(aluno, TITULO).criado()).isTrue();
    }

    @Test
    void recusaQuandoJaCriouOLimiteHoje() {
        criarHoje(LIMITE);

        assertThatThrownBy(() -> service.criar(aluno, TITULO))
                .isInstanceOf(LimiteTemasPersonalizadosException.class)
                .hasMessageContaining("10 temas");
        assertThat(temas.buscarPersonalizadoPorTitulo(aluno, TITULO)).isEmpty();
    }

    @Test
    void tituloRepetidoNaoGastaOLimite() {
        criarHoje(LIMITE - 1);
        var existente = personalizadoCriadoEm(aluno, TITULO, MEIA_NOITE_SP.plusHours(2));

        var resultado = service.criar(aluno, TITULO);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.tema().id()).isEqualTo(existente.id());
    }

    @Test
    void temasDeOntemNaoContamNoLimite() {
        // 23:59:59 de ontem em São Paulo
        for (int i = 0; i < LIMITE; i++) {
            personalizadoCriadoEm(aluno, "Tema número " + i + " de ontem", MEIA_NOITE_SP.minusSeconds(1));
        }

        assertThat(service.criar(aluno, TITULO).criado()).isTrue();
    }

    @Test
    void temasDeOutroAlunoNaoContamNoLimite() {
        for (int i = 0; i < LIMITE; i++) {
            personalizadoCriadoEm(outroAluno, "Tema número " + i + " do outro", MEIA_NOITE_SP.plusHours(1));
        }

        assertThat(service.criar(aluno, TITULO).criado()).isTrue();
    }

    // ---------- corrida (duplo clique) ----------

    @Test
    void corridaDevolveOTemaQueAOutraRequisicaoCriou() {
        var temasComCorrida = new TemaRepositoryEmMemoria() {
            @Override
            public Tema salvar(Tema tema) {
                if (tema.id() == null && tema.origem() == OrigemTema.PERSONALIZADO) {
                    // A outra requisição grava primeiro; esta bate no índice único
                    super.salvar(new Tema(UUID.randomUUID(), tema.titulo(), null, tema.origem(), null, true, null,
                            tema.criadoPorId()));
                    throw new DataIntegrityViolationException("uq_temas_personalizado_titulo");
                }
                return super.salvar(tema);
            }
        };
        var comCorrida = new CriarTemaPersonalizadoService(temasComCorrida, RELOGIO, LIMITE);

        var resultado = comCorrida.criar(aluno, TITULO);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.tema().titulo()).isEqualTo(TITULO);
        assertThat(temasComCorrida.listarPersonalizadosAtivosDo(aluno)).hasSize(1);
    }

    @Test
    void violacaoQueNaoEhCorridaSobeOErro() {
        var temasQuebrados = new TemaRepositoryEmMemoria() {
            @Override
            public Tema salvar(Tema tema) {
                throw new DataIntegrityViolationException("outra constraint");
            }
        };
        var quebrado = new CriarTemaPersonalizadoService(temasQuebrados, RELOGIO, LIMITE);

        assertThatThrownBy(() -> quebrado.criar(aluno, TITULO))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("outra constraint");
    }
}
