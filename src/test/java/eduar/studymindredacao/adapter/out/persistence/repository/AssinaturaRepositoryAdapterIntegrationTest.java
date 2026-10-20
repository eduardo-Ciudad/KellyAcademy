package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.EventoStripeRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AssinaturaRepositoryAdapter.class, EventoStripeRepositoryAdapter.class, UsuarioRepositoryAdapter.class})
class AssinaturaRepositoryAdapterIntegrationTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private AssinaturaRepositoryPort assinaturas;
    @Autowired
    private EventoStripeRepositoryPort eventos;
    @Autowired
    private UsuarioRepositoryPort usuarios;

    @Autowired
    private AssinaturaJpaRepository jpa;

    private UUID usuarioId;

    @BeforeEach
    void criarUsuario() {
        String email = "assinatura-" + UUID.randomUUID() + "@exemplo.com";
        usuarioId = usuarios.salvar(new Usuario(null, "Teste", email, "hash", Role.ALUNO, null, null)).id();
    }

    private static String sub() {
        return "sub_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String cus() {
        return "cus_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Assinatura nova(String sub, OffsetDateTime criadaEm) {
        return Assinatura.nova(usuarioId, sub, CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
                criadaEm.plusMonths(1), false, criadaEm);
    }


    @Test
    void salvarNovaGeraIdEPermiteBuscarPelaSubscription() {
        String sub = sub();

        var salva = assinaturas.salvar(nova(sub, AGORA));

        assertThat(salva.id()).isNotNull();
        var lida = assinaturas.buscarPorStripeSubscriptionId(sub).orElseThrow();
        assertThat(lida.usuarioId()).isEqualTo(usuarioId);
        assertThat(lida.status()).isEqualTo(StatusAssinatura.ACTIVE);
        assertThat(lida.periodoAtualFim()).isAtSameInstantAs(AGORA.plusMonths(1));
    }

    @Test
    void salvarDeNovoAtualizaAMesmaLinha() {
        String sub = sub();
        var salva = assinaturas.salvar(nova(sub, AGORA));

        var cancelando = salva.comEstadoDoStripe(CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
                AGORA.plusMonths(1), true, AGORA.plusDays(3));
        assinaturas.salvar(cancelando);

        var lida = assinaturas.buscarPorStripeSubscriptionId(sub).orElseThrow();
        assertThat(lida.id()).isEqualTo(salva.id());
        assertThat(lida.cancelarNoFimDoPeriodo()).isTrue();
        assertThat(lida.atualizadoEm()).isAtSameInstantAs(AGORA.plusDays(3));
        assertThat(assinaturas.listarPorUsuario(usuarioId)).hasSize(1);
    }

    @Test
    void mesmaSubscriptionDuasVezesComoNovaViolaAUnique() {
        String sub = sub();
        assinaturas.salvar(nova(sub, AGORA));
        jpa.flush();

        // Proteção do banco para o caso de dois webhooks inserirem a mesma assinatura ao mesmo tempo.
        // O flush força o INSERT agora: sem ele o Hibernate adiaria para o commit, que o @DataJpaTest nunca faz.
        assertThatThrownBy(() -> {
            assinaturas.salvar(nova(sub, AGORA));
            jpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listarPorUsuarioVemDaMaisRecenteParaAMaisAntiga() {
        String antiga = sub();
        String recente = sub();
        assinaturas.salvar(nova(antiga, AGORA.minusMonths(3)));
        assinaturas.salvar(nova(recente, AGORA));

        var lista = assinaturas.listarPorUsuario(usuarioId);

        assertThat(lista).extracting(Assinatura::stripeSubscriptionId).containsExactly(recente, antiga);
    }

    @Test
    void buscarSubscriptionInexistenteOuVaziaDevolveVazio() {
        assertThat(assinaturas.buscarPorStripeSubscriptionId("sub_naoexiste")).isEmpty();
        assertThat(assinaturas.buscarPorStripeSubscriptionId(" ")).isEmpty();
        assertThat(assinaturas.buscarPorStripeSubscriptionId(null)).isEmpty();
    }


    @Test
    void usuarioNovoNaoTemClienteDoStripe() {
        assertThat(assinaturas.buscarStripeCustomerId(usuarioId)).isEmpty();
    }

    @Test
    void vincularClienteGravaEPermiteAcharOUsuarioPeloCliente() {
        String cus = cus();

        boolean vinculou = assinaturas.vincularStripeCustomerId(usuarioId, cus);

        assertThat(vinculou).isTrue();
        assertThat(assinaturas.buscarStripeCustomerId(usuarioId)).contains(cus);
        assertThat(assinaturas.buscarUsuarioPorStripeCustomerId(cus)).contains(usuarioId);
    }

    @Test
    void vincularDeNovoNaoSobrescreveOClienteJaGravado() {
        String primeiro = cus();
        assinaturas.vincularStripeCustomerId(usuarioId, primeiro);

        boolean vinculouDeNovo = assinaturas.vincularStripeCustomerId(usuarioId, cus());

        assertThat(vinculouDeNovo).isFalse();
        assertThat(assinaturas.buscarStripeCustomerId(usuarioId)).contains(primeiro);
    }

    @Test
    void salvarOUsuarioDepoisNaoApagaOCliente() {
        String cus = cus();
        assinaturas.vincularStripeCustomerId(usuarioId, cus);
        var usuario = usuarios.buscarPorId(usuarioId).orElseThrow();

        usuarios.salvar(usuario.comEmailVerificado(AGORA));

        assertThat(assinaturas.buscarStripeCustomerId(usuarioId)).contains(cus);
    }

    @Test
    void vincularRejeitaIdQueNaoEDeCliente() {
        assertThatThrownBy(() -> assinaturas.vincularStripeCustomerId(usuarioId, "sub_123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cus_");
    }

    @Test
    void clienteDesconhecidoNaoAchaUsuario() {
        assertThat(assinaturas.buscarUsuarioPorStripeCustomerId("cus_naoexiste")).isEmpty();
    }


    @Test
    void primeiroRegistroDoEventoEhNovoESegundoNao() {
        String evt = "evt_" + UUID.randomUUID();

        assertThat(eventos.registrarSeNovo(evt, "customer.subscription.updated", AGORA)).isTrue();
        assertThat(eventos.registrarSeNovo(evt, "customer.subscription.updated", AGORA.plusSeconds(5))).isFalse();
    }

    @Test
    void eventosDiferentesSaoIndependentes() {
        assertThat(eventos.registrarSeNovo("evt_" + UUID.randomUUID(), "invoice.paid", AGORA)).isTrue();
        assertThat(eventos.registrarSeNovo("evt_" + UUID.randomUUID(), "invoice.paid", AGORA)).isTrue();
    }
}