package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.AssinaturaExistenteException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.SessaoCheckout;
import eduar.studymindredacao.domain.model.SolicitacaoCheckout;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IniciarCheckoutServiceTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 20, 18, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID ID = UUID.randomUUID();
    private static final Usuario ALUNO =
            new Usuario(ID, "Ana", "ana@exemplo.com", "hash", Role.ALUNO, AGORA, AGORA);
    private static final SessaoCheckout SESSAO =
            new SessaoCheckout("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1");

    @Mock
    private UsuarioRepositoryPort usuarios;
    @Mock
    private AssinaturaRepositoryPort assinaturas;
    @Mock
    private PagamentoGatewayPort gateway;

    private IniciarCheckoutService service;

    @BeforeEach
    void criar() {
        service = new IniciarCheckoutService(usuarios, assinaturas, gateway);
    }

    private static Assinatura assinatura(StatusAssinatura status) {
        return Assinatura.nova(ID, "sub_1", CicloAssinatura.MENSAL, status, AGORA.plusMonths(1), false, AGORA);
    }

    @Test
    void alunoSemClienteGanhaUmNovoEAbreOCheckout() {
        when(assinaturas.listarPorUsuario(ID)).thenReturn(List.of());
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.of(ALUNO));
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.empty());
        when(gateway.criarCliente(ID, "Ana", "ana@exemplo.com")).thenReturn("cus_novo");
        when(assinaturas.vincularStripeCustomerId(ID, "cus_novo")).thenReturn(true);
        when(gateway.criarSessaoCheckout(any())).thenReturn(SESSAO);

        var sessao = service.iniciar(ID, CicloAssinatura.ANUAL);

        assertThat(sessao).isEqualTo(SESSAO);
        var pedido = ArgumentCaptor.forClass(SolicitacaoCheckout.class);
        verify(gateway).criarSessaoCheckout(pedido.capture());
        assertThat(pedido.getValue().stripeCustomerId()).isEqualTo("cus_novo");
        assertThat(pedido.getValue().ciclo()).isEqualTo(CicloAssinatura.ANUAL);
        assertThat(pedido.getValue().usuarioId()).isEqualTo(ID);
    }

    @Test
    void alunoQueJaTemClienteReaproveitaSemCriarOutro() {
        when(assinaturas.listarPorUsuario(ID)).thenReturn(List.of());
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.of(ALUNO));
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.of("cus_antigo"));
        when(gateway.criarSessaoCheckout(any())).thenReturn(SESSAO);

        service.iniciar(ID, CicloAssinatura.MENSAL);

        verify(gateway, never()).criarCliente(any(), anyString(), anyString());
        var pedido = ArgumentCaptor.forClass(SolicitacaoCheckout.class);
        verify(gateway).criarSessaoCheckout(pedido.capture());
        assertThat(pedido.getValue().stripeCustomerId()).isEqualTo("cus_antigo");
    }

    @Test
    void duploCliqueUsaOClienteQueGanhouACorrida() {
        when(assinaturas.listarPorUsuario(ID)).thenReturn(List.of());
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.of(ALUNO));
        // 1ª leitura: vazio. Depois de perder o vínculo, a releitura acha o que a outra requisição gravou
        when(assinaturas.buscarStripeCustomerId(ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("cus_vencedor"));
        when(gateway.criarCliente(ID, "Ana", "ana@exemplo.com")).thenReturn("cus_vencedor");
        when(assinaturas.vincularStripeCustomerId(ID, "cus_vencedor")).thenReturn(false);
        when(gateway.criarSessaoCheckout(any())).thenReturn(SESSAO);

        service.iniciar(ID, CicloAssinatura.MENSAL);

        var pedido = ArgumentCaptor.forClass(SolicitacaoCheckout.class);
        verify(gateway).criarSessaoCheckout(pedido.capture());
        assertThat(pedido.getValue().stripeCustomerId()).isEqualTo("cus_vencedor");
    }

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"CANCELED", "INCOMPLETE_EXPIRED"},
            mode = EnumSource.Mode.EXCLUDE)
    void assinaturaNaoEncerradaBloqueiaNovoCheckout(StatusAssinatura status) {
        when(assinaturas.listarPorUsuario(ID)).thenReturn(List.of(assinatura(status)));

        assertThatThrownBy(() -> service.iniciar(ID, CicloAssinatura.MENSAL))
                .isInstanceOf(AssinaturaExistenteException.class);

        verify(gateway, never()).criarCliente(any(), anyString(), anyString());
        verify(gateway, never()).criarSessaoCheckout(any());
    }

    @Test
    void quemTeveAssinaturaCanceladaPodeAssinarDeNovo() {
        when(assinaturas.listarPorUsuario(ID)).thenReturn(List.of(assinatura(StatusAssinatura.CANCELED)));
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.of(ALUNO));
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.of("cus_antigo"));
        when(gateway.criarSessaoCheckout(any())).thenReturn(SESSAO);

        assertThat(service.iniciar(ID, CicloAssinatura.MENSAL)).isEqualTo(SESSAO);
    }

    @Test
    void contaInexistenteNaoChamaOStripe() {
        when(assinaturas.listarPorUsuario(ID)).thenReturn(List.of());
        when(usuarios.buscarPorId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.iniciar(ID, CicloAssinatura.MENSAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conta");

        verify(gateway, never()).criarCliente(any(), anyString(), anyString());
    }

    @Test
    void exigeCiclo() {
        assertThatThrownBy(() -> service.iniciar(ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ciclo");
    }
}
