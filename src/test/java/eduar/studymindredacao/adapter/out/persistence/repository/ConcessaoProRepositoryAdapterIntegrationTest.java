package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra o Postgres local com a V25 aplicada (mesmo padrão das assinaturas). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConcessaoProRepositoryAdapter.class, UsuarioRepositoryAdapter.class})
class ConcessaoProRepositoryAdapterIntegrationTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private ConcessaoProRepositoryPort concessoes;
    @Autowired
    private UsuarioRepositoryPort usuarios;
    @Autowired
    private ConcessaoProJpaRepository jpa;

    private UUID alunoId;
    private UUID adminId;

    @BeforeEach
    void criarUsuarios() {
        alunoId = criar(Role.ALUNO);
        adminId = criar(Role.ADMIN);
    }

    private UUID criar(Role role) {
        String email = "concessao-" + UUID.randomUUID() + "@exemplo.com";
        return usuarios.salvar(new Usuario(null, "Teste", email, "hash", role, null, null)).id();
    }

    private ConcessaoPro nova(OffsetDateTime quando, int dias) {
        return ConcessaoPro.conceder(alunoId, adminId, "Testador do piloto", dias, quando, SAO_PAULO);
    }

    @Test
    void salvarGeraIdEABuscaDaAbertaDevolveOsMesmosDados() {
        var salva = concessoes.salvar(nova(AGORA, 30));

        assertThat(salva.id()).isNotNull();
        var lida = concessoes.buscarAberta(alunoId).orElseThrow();
        assertThat(lida.id()).isEqualTo(salva.id());
        assertThat(lida.concedidaPor()).isEqualTo(adminId);
        assertThat(lida.motivo()).isEqualTo("Testador do piloto");
        assertThat(lida.inicio()).isAtSameInstantAs(AGORA);
        assertThat(lida.fim()).isAtSameInstantAs(salva.fim());
        assertThat(lida.revogada()).isFalse();
    }

    @Test
    void semConcessaoNaoHaAberta() {
        assertThat(concessoes.buscarAberta(alunoId)).isEmpty();
        assertThat(concessoes.listarPorUsuario(alunoId)).isEmpty();
    }

    @Test
    void revogarGravaNaMesmaLinhaEDeixaDeSerAberta() {
        var salva = concessoes.salvar(nova(AGORA, 30));

        concessoes.salvar(salva.revogar(adminId, "Fim do piloto", AGORA.plusHours(1)));
        jpa.flush();

        assertThat(concessoes.buscarAberta(alunoId)).isEmpty();
        var historico = concessoes.listarPorUsuario(alunoId);
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).id()).isEqualTo(salva.id());
        assertThat(historico.get(0).revogadaPor()).isEqualTo(adminId);
        assertThat(historico.get(0).motivoRevogacao()).isEqualTo("Fim do piloto");
    }

    @Test
    void indiceUnicoBarraSegundaConcessaoAbertaParaOMesmoAluno() {
        concessoes.salvar(nova(AGORA, 30));
        jpa.flush();

        // O flush força o INSERT agora: sem ele o Hibernate adiaria para o commit, que o @DataJpaTest nunca faz
        assertThatThrownBy(() -> {
            concessoes.salvar(nova(AGORA.plusMinutes(1), 10));
            jpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void depoisDeRevogarANovaConcessaoPassaEOHistoricoVemDaMaisRecente() {
        var primeira = concessoes.salvar(nova(AGORA.minusDays(5), 30));
        concessoes.salvar(primeira.revogar(adminId, "Fim do piloto", AGORA.minusDays(1)));
        jpa.flush();

        var segunda = concessoes.salvar(nova(AGORA, 7));
        jpa.flush();

        assertThat(concessoes.buscarAberta(alunoId).orElseThrow().id()).isEqualTo(segunda.id());
        assertThat(concessoes.listarPorUsuario(alunoId))
                .extracting(ConcessaoPro::id)
                .containsExactly(segunda.id(), primeira.id());
    }

    @Test
    void concessaoVencidaSemRevogarContinuaAberta() {
        concessoes.salvar(nova(AGORA.minusDays(40), 30));
        jpa.flush();

        var aberta = concessoes.buscarAberta(alunoId).orElseThrow();
        assertThat(aberta.vencida(AGORA)).isTrue();
        assertThat(aberta.vigente(AGORA)).isFalse();
    }

    @Test
    void outroAlunoNaoEnxergaAConcessao() {
        concessoes.salvar(nova(AGORA, 30));
        jpa.flush();

        assertThat(concessoes.buscarAberta(criar(Role.ALUNO))).isEmpty();
    }
}
