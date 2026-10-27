package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.exception.ConcessaoProConflitoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcessaoProTest {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 27, 17, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final UUID ALUNO = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String MOTIVO = "Testador do piloto";

    private static ConcessaoPro concessao30Dias() {
        return ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 30, AGORA, SAO_PAULO);
    }

    // ---------- conceder ----------

    @Test
    void concederPreencheOsCamposENaoTemId() {
        var concessao = concessao30Dias();

        assertThat(concessao.id()).isNull();
        assertThat(concessao.usuarioId()).isEqualTo(ALUNO);
        assertThat(concessao.concedidaPor()).isEqualTo(ADMIN);
        assertThat(concessao.motivo()).isEqualTo(MOTIVO);
        assertThat(concessao.inicio()).isEqualTo(AGORA);
        assertThat(concessao.revogada()).isFalse();
    }

    @Test
    void fimEhOFimDoDiaHojeMaisDiasNoFusoDeSaoPaulo() {
        // 27/10 + 30 dias = 26/11; vale até 23h59 de 26/11, ou seja, termina em 27/11 00:00 (-03)
        var concessao = concessao30Dias();

        assertThat(concessao.fim())
                .isEqualTo(OffsetDateTime.of(2026, 11, 27, 0, 0, 0, 0, ZoneOffset.ofHours(-3)));
    }

    @Test
    void umDiaValeAteOFimDeAmanha() {
        var concessao = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 1, AGORA, SAO_PAULO);

        assertThat(concessao.fim())
                .isEqualTo(OffsetDateTime.of(2026, 10, 29, 0, 0, 0, 0, ZoneOffset.ofHours(-3)));
    }

    @Test
    void diaDeReferenciaEhODeSaoPauloMesmoComAgoraEmUtc() {
        // 01h30 UTC de 28/10 ainda é 22h30 de 27/10 em São Paulo: o dia de referência é 27/10
        var agoraUtc = OffsetDateTime.of(2026, 10, 28, 1, 30, 0, 0, ZoneOffset.UTC);

        var concessao = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 1, agoraUtc, SAO_PAULO);

        assertThat(concessao.fim().toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 10, 29, 0, 0, 0, 0, ZoneOffset.ofHours(-3)).toInstant());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 365})
    void aceitaPrazoNosLimites(int dias) {
        var concessao = ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, dias, AGORA, SAO_PAULO);

        assertThat(concessao.fim()).isAfter(AGORA);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 366, 10_000})
    void recusaPrazoForaDosLimites(int dias) {
        assertThatThrownBy(() -> ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, dias, AGORA, SAO_PAULO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dias");
    }

    @Test
    void permiteConcederParaAPropriaConta() {
        var concessao = ConcessaoPro.conceder(ADMIN, ADMIN, MOTIVO, 7, AGORA, SAO_PAULO);

        assertThat(concessao.usuarioId()).isEqualTo(concessao.concedidaPor());
    }

    // ---------- motivo ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abcd", "  abcd  "})
    void recusaMotivoVazioOuCurto(String motivo) {
        assertThatThrownBy(() -> ConcessaoPro.conceder(ALUNO, ADMIN, motivo, 30, AGORA, SAO_PAULO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void recusaMotivoAcimaDe300Caracteres() {
        String longo = "a".repeat(301);

        assertThatThrownBy(() -> ConcessaoPro.conceder(ALUNO, ADMIN, longo, 30, AGORA, SAO_PAULO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void aceitaMotivoCom5E300CaracteresDepoisDoTrim() {
        assertThat(ConcessaoPro.conceder(ALUNO, ADMIN, "abcde", 30, AGORA, SAO_PAULO).motivo())
                .isEqualTo("abcde");
        assertThat(ConcessaoPro.conceder(ALUNO, ADMIN, "a".repeat(300), 30, AGORA, SAO_PAULO).motivo())
                .hasSize(300);
    }

    @Test
    void guardaOMotivoSemEspacosNasPontas() {
        var concessao = ConcessaoPro.conceder(ALUNO, ADMIN, "   " + MOTIVO + "  ", 30, AGORA, SAO_PAULO);

        assertThat(concessao.motivo()).isEqualTo(MOTIVO);
    }

    // ---------- obrigatórios ----------

    @Test
    void recusaCamposObrigatoriosNulos() {
        assertThatThrownBy(() -> ConcessaoPro.conceder(null, ADMIN, MOTIVO, 30, AGORA, SAO_PAULO))
                .hasMessageContaining("usuarioId");
        assertThatThrownBy(() -> ConcessaoPro.conceder(ALUNO, null, MOTIVO, 30, AGORA, SAO_PAULO))
                .hasMessageContaining("concedidaPor");
        assertThatThrownBy(() -> ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 30, null, SAO_PAULO))
                .hasMessageContaining("agora");
        assertThatThrownBy(() -> ConcessaoPro.conceder(ALUNO, ADMIN, MOTIVO, 30, AGORA, null))
                .hasMessageContaining("fuso");
    }

    @Test
    void recusaFimIgualOuAntesDoInicio() {
        assertThatThrownBy(() -> new ConcessaoPro(null, ALUNO, ADMIN, MOTIVO, AGORA, AGORA, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fim");
        assertThatThrownBy(() -> new ConcessaoPro(null, ALUNO, ADMIN, MOTIVO, AGORA, AGORA.minusDays(1),
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaRevogacaoIncompleta() {
        var fim = AGORA.plusDays(30);

        assertThatThrownBy(() -> new ConcessaoPro(null, ALUNO, ADMIN, MOTIVO, AGORA, fim,
                AGORA.plusDays(1), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("juntos");
        assertThatThrownBy(() -> new ConcessaoPro(null, ALUNO, ADMIN, MOTIVO, AGORA, fim,
                null, ADMIN, "Fim do teste"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaRevogacaoAntesDoInicio() {
        assertThatThrownBy(() -> new ConcessaoPro(null, ALUNO, ADMIN, MOTIVO, AGORA, AGORA.plusDays(30),
                AGORA.minusSeconds(1), ADMIN, "Fim do teste"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revogadaEm");
    }

    // ---------- vigência ----------

    @Test
    void naoEhVigenteAntesDoInicio() {
        assertThat(concessao30Dias().vigente(AGORA.minusSeconds(1))).isFalse();
    }

    @Test
    void ehVigenteNoInicioEDuranteOPrazo() {
        var concessao = concessao30Dias();

        assertThat(concessao.vigente(AGORA)).isTrue();
        assertThat(concessao.vigente(concessao.fim().minusNanos(1))).isTrue();
    }

    @Test
    void deixaDeSerVigenteNoInstanteDoFim() {
        var concessao = concessao30Dias();

        assertThat(concessao.vigente(concessao.fim())).isFalse();
        assertThat(concessao.vencida(concessao.fim())).isTrue();
        assertThat(concessao.vigente(concessao.fim().plusDays(1))).isFalse();
    }

    @Test
    void naoEstaVencidaDuranteOPrazo() {
        assertThat(concessao30Dias().vencida(AGORA.plusDays(1))).isFalse();
    }

    // ---------- revogação ----------

    @Test
    void revogarMarcaQuemQuandoEPorQueEMantemOResto() {
        var concessao = concessao30Dias();
        var quando = AGORA.plusDays(2);

        var revogada = concessao.revogar(ADMIN, "  Fim do piloto  ", quando);

        assertThat(revogada.revogada()).isTrue();
        assertThat(revogada.revogadaEm()).isEqualTo(quando);
        assertThat(revogada.revogadaPor()).isEqualTo(ADMIN);
        assertThat(revogada.motivoRevogacao()).isEqualTo("Fim do piloto");
        assertThat(revogada.motivo()).isEqualTo(MOTIVO);
        assertThat(revogada.fim()).isEqualTo(concessao.fim());
        assertThat(concessao.revogada()).as("o original não muda").isFalse();
    }

    @Test
    void revogadaDeixaDeSerVigenteENaoContaComoVencida() {
        var revogada = concessao30Dias().revogar(ADMIN, "Fim do piloto", AGORA.plusDays(2));

        assertThat(revogada.vigente(AGORA.plusDays(3))).isFalse();
        assertThat(revogada.vencida(revogada.fim().plusDays(1))).isFalse();
    }

    @Test
    void revogarDuasVezesEhConflito() {
        var revogada = concessao30Dias().revogar(ADMIN, "Fim do piloto", AGORA.plusDays(2));

        assertThatThrownBy(() -> revogada.revogar(ADMIN, "De novo aqui", AGORA.plusDays(3)))
                .isInstanceOf(ConcessaoProConflitoException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc"})
    void revogarExigeMotivoValido(String motivo) {
        var concessao = concessao30Dias();

        assertThatThrownBy(() -> concessao.revogar(ADMIN, motivo, AGORA.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivoRevogacao");
    }

    @Test
    void revogarExigeAdminEQuando() {
        var concessao = concessao30Dias();

        assertThatThrownBy(() -> concessao.revogar(null, "Fim do piloto", AGORA.plusDays(1)))
                .hasMessageContaining("adminId");
        assertThatThrownBy(() -> concessao.revogar(ADMIN, "Fim do piloto", null))
                .hasMessageContaining("quando");
    }

    // ---------- fechar vencida ----------

    @Test
    void fecharVencidaRegistraOAdminEOMotivoExpirada() {
        var concessao = concessao30Dias();
        var depoisDoFim = concessao.fim().plusDays(5);
        var outroAdmin = UUID.randomUUID();

        var fechada = concessao.fecharVencida(outroAdmin, depoisDoFim);

        assertThat(fechada.revogadaEm()).isEqualTo(depoisDoFim);
        assertThat(fechada.revogadaPor()).isEqualTo(outroAdmin);
        assertThat(fechada.motivoRevogacao()).isEqualTo(ConcessaoPro.MOTIVO_EXPIRADA);
    }

    @Test
    void naoFechaConcessaoAindaVigente() {
        var concessao = concessao30Dias();

        assertThatThrownBy(() -> concessao.fecharVencida(ADMIN, AGORA.plusDays(1)))
                .isInstanceOf(ConcessaoProConflitoException.class);
    }

    @Test
    void naoFechaConcessaoJaRevogada() {
        var revogada = concessao30Dias().revogar(ADMIN, "Fim do piloto", AGORA.plusDays(1));

        assertThatThrownBy(() -> revogada.fecharVencida(ADMIN, revogada.fim().plusDays(1)))
                .isInstanceOf(ConcessaoProConflitoException.class);
    }

    // ---------- log ----------

    @Test
    void toStringNaoExpoeOsMotivos() {
        var revogada = concessao30Dias().revogar(ADMIN, "Motivo sigiloso", AGORA.plusDays(1));

        assertThat(revogada.toString())
                .doesNotContain(MOTIVO)
                .doesNotContain("Motivo sigiloso")
                .contains(ALUNO.toString());
    }
}
