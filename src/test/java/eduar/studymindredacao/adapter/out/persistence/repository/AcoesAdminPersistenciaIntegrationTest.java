package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** O que as ações do admin gravam no banco, contra o Postgres local. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UsoIADiarioRepositoryAdapter.class, TokenUsuarioRepositoryAdapter.class, ConcessaoProRepositoryAdapter.class,
        UsuarioRepositoryAdapter.class})
class AcoesAdminPersistenciaIntegrationTest {
    private static final LocalDate DIA = LocalDate.of(2091, 3, 10);
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private UsoIADiarioRepositoryPort usoDiario;
    @Autowired
    private TokenUsuarioRepositoryPort tokens;
    @Autowired
    private ConcessaoProRepositoryPort concessoes;
    @Autowired
    private UsuarioRepositoryPort usuarios;
    @Autowired
    private EntityManager em;

    private UUID alunoId;
    private UUID adminId;

    @BeforeEach
    void criarUsuarios() {
        alunoId = criar(Role.ALUNO);
        adminId = criar(Role.ADMIN);
    }

    private UUID criar(Role role) {
        String email = "acoes-admin-" + UUID.randomUUID() + "@exemplo.com";
        return usuarios.salvar(new Usuario(null, "Teste", email, "hash", role, null, null)).id();
    }

    private Object[] linhaDoDia(UUID usuarioId, LocalDate dia) {
        return (Object[]) em.createNativeQuery("""
                        SELECT qtd_correcoes, qtd_transcricoes, qtd_mensagens_julia, tokens_entrada, tokens_saida
                          FROM uso_ia_diario WHERE usuario_id = :usuario AND data = :dia
                        """)
                .setParameter("usuario", usuarioId)
                .setParameter("dia", dia)
                .getSingleResult();
    }

    // ---------- zerar cota ----------

    @Test
    void zerarContadoresZeraOs3ContadoresEMantemTokens() {
        em.createNativeQuery("""
                        INSERT INTO uso_ia_diario (usuario_id, data, qtd_correcoes, qtd_transcricoes,
                                                   qtd_mensagens_julia, tokens_entrada, tokens_saida)
                        VALUES (:usuario, :dia, 3, 2, 9, 5000, 700)
                        """)
                .setParameter("usuario", alunoId)
                .setParameter("dia", DIA)
                .executeUpdate();

        assertThat(usoDiario.zerarContadores(alunoId, DIA)).isTrue();

        Object[] linha = linhaDoDia(alunoId, DIA);
        assertThat(((Number) linha[0]).intValue()).isZero();
        assertThat(((Number) linha[1]).intValue()).isZero();
        assertThat(((Number) linha[2]).intValue()).isZero();
        assertThat(((Number) linha[3]).intValue()).isEqualTo(5000);
        assertThat(((Number) linha[4]).intValue()).isEqualTo(700);
    }

    @Test
    void zerarSemLinhaNoDiaNaoCriaNadaEDevolveFalso() {
        assertThat(usoDiario.zerarContadores(alunoId, DIA)).isFalse();

        Number linhas = (Number) em.createNativeQuery(
                        "SELECT count(*) FROM uso_ia_diario WHERE usuario_id = :usuario")
                .setParameter("usuario", alunoId)
                .getSingleResult();
        assertThat(linhas.intValue()).isZero();
    }

    @Test
    void zerarNaoMexeEmOutroDiaNemNoContadorGlobal() {
        em.createNativeQuery("""
                        INSERT INTO uso_ia_diario (usuario_id, data, qtd_correcoes) VALUES (:usuario, :dia, 3),
                                                                                          (:usuario, :ontem, 3)
                        """)
                .setParameter("usuario", alunoId)
                .setParameter("dia", DIA)
                .setParameter("ontem", DIA.minusDays(1))
                .executeUpdate();
        em.createNativeQuery("INSERT INTO uso_ia_global_diario (data, recurso, quantidade) VALUES (:dia, 'CORRECAO', 3)")
                .setParameter("dia", DIA)
                .executeUpdate();

        usoDiario.zerarContadores(alunoId, DIA);

        assertThat(((Number) linhaDoDia(alunoId, DIA.minusDays(1))[0]).intValue()).isEqualTo(3);
        Number global = (Number) em.createNativeQuery(
                        "SELECT quantidade FROM uso_ia_global_diario WHERE data = :dia AND recurso = 'CORRECAO'")
                .setParameter("dia", DIA)
                .getSingleResult();
        assertThat(global.intValue()).isEqualTo(3);
    }

    // ---------- última emissão de token ----------

    @Test
    void ultimaEmissaoDevolveOMaisRecenteDoTipo() {
        tokens.salvar(TokenUsuario.novo(alunoId, TipoTokenUsuario.VERIFICACAO_EMAIL, "a".repeat(64),
                AGORA.minusHours(2), Duration.ofHours(24)));
        tokens.salvar(TokenUsuario.novo(alunoId, TipoTokenUsuario.VERIFICACAO_EMAIL, "b".repeat(64),
                AGORA.minusMinutes(3), Duration.ofHours(24)));
        tokens.salvar(TokenUsuario.novo(alunoId, TipoTokenUsuario.REDEFINICAO_SENHA, "c".repeat(64),
                AGORA.minusMinutes(1), Duration.ofMinutes(30)));
        em.flush();

        assertThat(tokens.ultimaEmissao(alunoId, TipoTokenUsuario.VERIFICACAO_EMAIL))
                .hasValueSatisfying(quando -> assertThat(quando).isAtSameInstantAs(AGORA.minusMinutes(3)));
    }

    @Test
    void semTokenNaoHaUltimaEmissao() {
        assertThat(tokens.ultimaEmissao(alunoId, TipoTokenUsuario.VERIFICACAO_EMAIL)).isEmpty();
    }

    // ---------- concessão: fechar a vencida e abrir a nova na mesma transação ----------

    @Test
    void fecharVencidaEInserirNovaNaMesmaTransacaoNaoEsbarraNoIndiceUnico() {
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
        var vencida = concessoes.salvar(ConcessaoPro.conceder(alunoId, adminId, "Primeira rodada", 7,
                AGORA.minusDays(30), saoPaulo));

        // Mesma ordem do AcoesUsuarioAdminService.concederPro: UPDATE da vencida e depois INSERT da nova
        concessoes.salvar(vencida.fecharVencida(adminId, AGORA));
        var nova = concessoes.salvar(ConcessaoPro.conceder(alunoId, adminId, "Segunda rodada", 30, AGORA, saoPaulo));

        assertThat(concessoes.buscarAberta(alunoId).orElseThrow().id()).isEqualTo(nova.id());
        assertThat(concessoes.listarPorUsuario(alunoId)).hasSize(2);
    }
}
