package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssinaturaTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 20, 17, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime FIM_DO_PERIODO = AGORA.plusMonths(1);
    private static final UUID USUARIO = UUID.randomUUID();
    private static final String SUB = "sub_123abc";

    private static Assinatura ativa() {
        return Assinatura.nova(USUARIO, SUB, CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
                FIM_DO_PERIODO, false, AGORA);
    }

    @Test
    void novaNaoTemIdECriadoEmIgualAAtualizadoEm() {
        var assinatura = ativa();

        assertThat(assinatura.id()).isNull();
        assertThat(assinatura.criadoEm()).isEqualTo(AGORA);
        assertThat(assinatura.atualizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void ativaDentroDoPeriodoDaAcessoPro() {
        assertThat(ativa().daAcessoPro(AGORA)).isTrue();
    }

    @Test
    void ativaComPeriodoVencidoNaoDaAcesso() {
        // Webhook atrasado: o status ainda diz ACTIVE, mas o período pago já acabou
        assertThat(ativa().daAcessoPro(FIM_DO_PERIODO.plusSeconds(1))).isFalse();
    }

    @Test
    void noInstanteExatoDoFimDoPeriodoNaoDaMaisAcesso() {
        assertThat(ativa().daAcessoPro(FIM_DO_PERIODO)).isFalse();
    }

    @Test
    void canceladaNoFimDoPeriodoContinuaDandoAcessoAteAData() {
        var cancelando = ativa().comEstadoDoStripe(CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
                FIM_DO_PERIODO, true, AGORA.plusDays(3));

        assertThat(cancelando.cancelarNoFimDoPeriodo()).isTrue();
        assertThat(cancelando.daAcessoPro(AGORA.plusDays(10))).isTrue();
    }

    @Test
    void pastDueNaoDaAcessoMesmoDentroDoPeriodo() {
        var atrasada = ativa().comEstadoDoStripe(CicloAssinatura.MENSAL, StatusAssinatura.PAST_DUE,
                FIM_DO_PERIODO, false, AGORA.plusDays(1));

        assertThat(atrasada.daAcessoPro(AGORA.plusDays(2))).isFalse();
    }

    @Test
    void comEstadoDoStripeMantemIdentidadeEAtualizaOResto() {
        var persistida = new Assinatura(UUID.randomUUID(), USUARIO, SUB, CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, FIM_DO_PERIODO, false, AGORA, AGORA);
        var quando = AGORA.plusDays(5);
        var novoFim = AGORA.plusYears(1);

        var atualizada = persistida.comEstadoDoStripe(CicloAssinatura.ANUAL, StatusAssinatura.ACTIVE,
                novoFim, false, quando);

        assertThat(atualizada.id()).isEqualTo(persistida.id());
        assertThat(atualizada.usuarioId()).isEqualTo(USUARIO);
        assertThat(atualizada.stripeSubscriptionId()).isEqualTo(SUB);
        assertThat(atualizada.criadoEm()).isEqualTo(AGORA);
        assertThat(atualizada.ciclo()).isEqualTo(CicloAssinatura.ANUAL);
        assertThat(atualizada.periodoAtualFim()).isEqualTo(novoFim);
        assertThat(atualizada.atualizadoEm()).isEqualTo(quando);
        assertThat(persistida.ciclo()).as("o original não muda").isEqualTo(CicloAssinatura.MENSAL);
    }

    @Test
    void rejeitaIdQueNaoEDeSubscription() {
        assertThatThrownBy(() -> Assinatura.nova(USUARIO, "cus_123", CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, FIM_DO_PERIODO, false, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sub_");
    }

    @Test
    void exigeUsuario() {
        assertThatThrownBy(() -> Assinatura.nova(null, SUB, CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, FIM_DO_PERIODO, false, AGORA))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("usuarioId");
    }

    @Test
    void exigePeriodo() {
        assertThatThrownBy(() -> Assinatura.nova(USUARIO, SUB, CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, null, false, AGORA))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("periodoAtualFim");
    }

    @Test
    void atualizadoEmNaoPodeSerAntesDeCriadoEm() {
        assertThatThrownBy(() -> new Assinatura(null, USUARIO, SUB, CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, FIM_DO_PERIODO, false, AGORA, AGORA.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atualizadoEm");
    }

    @Test
    void daAcessoProExigeOInstante() {
        assertThatThrownBy(() -> ativa().daAcessoPro(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("agora");
    }
}