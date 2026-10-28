package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Temas personalizados (V26) contra o Postgres local. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TemaRepositoryAdapter.class, UsuarioRepositoryAdapter.class})
class TemaRepositoryAdapterIntegrationTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private TemaRepositoryPort temas;
    @Autowired
    private UsuarioRepositoryPort usuarios;
    @Autowired
    private EntityManager em;

    private UUID aluno;
    private UUID outroAluno;

    @BeforeEach
    void criarAlunos() {
        aluno = criarAluno();
        outroAluno = criarAluno();
    }

    private UUID criarAluno() {
        String email = "tema-personalizado-" + UUID.randomUUID() + "@exemplo.com";
        return usuarios.salvar(new Usuario(null, "Teste", email, "hash", Role.ALUNO, null, null)).id();
    }

    private Tema personalizado(String titulo, UUID dono) {
        return temas.salvar(Tema.personalizado(titulo, dono));
    }

    /** O criado_em vem do @CreationTimestamp; aqui ele é ajustado à mão e o contexto é limpo para reler do banco. */
    private void mudarCriadoEm(Tema tema, OffsetDateTime quando) {
        em.flush();
        em.createNativeQuery("UPDATE temas SET criado_em = :quando WHERE id = :id")
                .setParameter("quando", quando)
                .setParameter("id", tema.id())
                .executeUpdate();
        em.clear();
    }

    // ---------- salvar ----------

    @Test
    void salvarGuardaODonoEAOrigem() {
        var salvo = personalizado("Os desafios da mobilidade urbana", aluno);

        var lido = temas.buscarPorId(salvo.id()).orElseThrow();
        assertThat(lido.origem()).isEqualTo(OrigemTema.PERSONALIZADO);
        assertThat(lido.criadoPorId()).isEqualTo(aluno);
        assertThat(lido.criadoEm()).isNotNull();
    }

    @Test
    void bancoRecusaOMesmoTituloComCaixaDiferenteParaOMesmoAluno() {
        personalizado("Os desafios da mobilidade urbana", aluno);

        assertThatThrownBy(() -> personalizado("OS DESAFIOS da mobilidade URBANA", aluno))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoAceitaOMesmoTituloParaAlunosDiferentes() {
        personalizado("Os desafios da mobilidade urbana", aluno);
        var doOutro = personalizado("Os desafios da mobilidade urbana", outroAluno);

        assertThat(doOutro.id()).isNotNull();
    }

    // ---------- listarAtivos ----------

    @Test
    void listarAtivosNuncaTrazPersonalizados() {
        var meu = personalizado("Os desafios da mobilidade urbana", aluno);

        var ativos = temas.listarAtivos();

        assertThat(ativos).extracting(Tema::origem).doesNotContain(OrigemTema.PERSONALIZADO);
        assertThat(ativos).extracting(Tema::id).doesNotContain(meu.id());
    }

    // ---------- listarPersonalizadosAtivosDo ----------

    @Test
    void listarPersonalizadosTrazSoOsDoDonoMaisRecentesPrimeiro() {
        var antigo = personalizado("Tema mais antigo do aluno", aluno);
        var recente = personalizado("Tema mais recente do aluno", aluno);
        var doOutro = personalizado("Tema que é de outro aluno", outroAluno);
        mudarCriadoEm(antigo, AGORA.minusDays(2));
        mudarCriadoEm(recente, AGORA.minusHours(1));

        var lista = temas.listarPersonalizadosAtivosDo(aluno);

        assertThat(lista).extracting(Tema::id).containsExactly(recente.id(), antigo.id());
        assertThat(lista).extracting(Tema::id).doesNotContain(doOutro.id());
    }

    @Test
    void listarPersonalizadosIgnoraInativos() {
        var ativo = personalizado("Tema ativo do aluno aqui", aluno);
        var inativo = personalizado("Tema inativo do aluno aqui", aluno);
        em.flush();
        em.createNativeQuery("UPDATE temas SET ativo = false WHERE id = :id").setParameter("id", inativo.id()).executeUpdate();
        em.clear();

        assertThat(temas.listarPersonalizadosAtivosDo(aluno)).extracting(Tema::id).containsExactly(ativo.id());
    }

    // ---------- buscarPersonalizadoPorTitulo ----------

    @Test
    void buscarPorTituloIgnoraMaiusculas() {
        var meu = personalizado("Os desafios da mobilidade urbana", aluno);

        assertThat(temas.buscarPersonalizadoPorTitulo(aluno, "os DESAFIOS da Mobilidade urbana"))
                .map(Tema::id).contains(meu.id());
    }

    @Test
    void buscarPorTituloNaoAchaOTemaDeOutroAluno() {
        personalizado("Os desafios da mobilidade urbana", outroAluno);

        assertThat(temas.buscarPersonalizadoPorTitulo(aluno, "Os desafios da mobilidade urbana")).isEmpty();
    }

    // ---------- contarPersonalizadosCriadosDesde ----------

    @Test
    void contarSoConsideraOsCriadosAPartirDoInstante() {
        var ontem = personalizado("Tema criado ontem pelo aluno", aluno);
        var hoje1 = personalizado("Primeiro tema criado hoje", aluno);
        var hoje2 = personalizado("Segundo tema criado hoje", aluno);
        personalizado("Tema de outro aluno hoje", outroAluno);
        OffsetDateTime inicioDoDia = AGORA.minusHours(3);
        mudarCriadoEm(ontem, inicioDoDia.minusSeconds(1));
        mudarCriadoEm(hoje1, inicioDoDia);
        mudarCriadoEm(hoje2, inicioDoDia.plusHours(1));

        assertThat(temas.contarPersonalizadosCriadosDesde(aluno, inicioDoDia)).isEqualTo(2);
    }
}
