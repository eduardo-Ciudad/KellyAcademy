package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.enums.Plano;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarUsoDiarioServiceTest {
    private static final int LIMITE = 3; // Plano.GRATIS
    private static final int LIMITE_TRANSCRICOES = 2; // Plano.GRATIS
    // 01:30 UTC do dia 29 = 22:30 do dia 28 em São Paulo
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-29T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate HOJE_EM_SP = LocalDate.of(2026, 9, 28);

    private final UUID alunoId = UUID.randomUUID();
    private final UsoIADiarioEmMemoria uso = new UsoIADiarioEmMemoria();
    private final ConsultarPlanoService planos = mock(ConsultarPlanoService.class);

    {
        when(planos.doUsuario(any())).thenReturn(Plano.GRATIS);
    }

    private final ConsultarUsoDiarioService service = new ConsultarUsoDiarioService(uso, planos, RELOGIO);

    @Test
    void semUsoNoDia_retornaLimiteInteiro() {
        var resultado = service.consultar(alunoId);

        assertThat(resultado.usadas()).isZero();
        assertThat(resultado.limite()).isEqualTo(LIMITE);
        assertThat(resultado.restantes()).isEqualTo(LIMITE);
    }

    @Test
    void contaAsVagasReservadasNoDiaDeSaoPaulo() {
        uso.reservarCorrecao(alunoId, HOJE_EM_SP, LIMITE);
        uso.reservarCorrecao(alunoId, HOJE_EM_SP, LIMITE);
        // uso do dia seguinte em UTC não pode entrar na conta
        uso.reservarCorrecao(alunoId, HOJE_EM_SP.plusDays(1), LIMITE);

        var resultado = service.consultar(alunoId);

        assertThat(resultado.usadas()).isEqualTo(2);
        assertThat(resultado.restantes()).isEqualTo(1);
    }

    @Test
    void restantesNuncaFicaNegativo_seOAlunoVoltarParaOGratis() {
        // Usou 4 como Pro e a assinatura acabou no mesmo dia: o limite cai para 3
        for (int i = 0; i < 4; i++) {
            uso.reservarCorrecao(alunoId, HOJE_EM_SP, 10);
        }

        var resultado = service.consultar(alunoId);

        assertThat(resultado.usadas()).isEqualTo(4);
        assertThat(resultado.limite()).isEqualTo(LIMITE);
        assertThat(resultado.restantes()).isZero();
    }

    @Test
    void saldoDeTranscricoesUsaContadorELimitePropriosSemMisturarComCorrecoes() {
        uso.reservarCorrecao(alunoId, HOJE_EM_SP, LIMITE);
        uso.reservarTranscricao(alunoId, HOJE_EM_SP, LIMITE_TRANSCRICOES);

        var transcricoes = service.consultarTranscricoes(alunoId);
        var correcoes = service.consultar(alunoId);

        assertThat(transcricoes.usadas()).isEqualTo(1);
        assertThat(transcricoes.limite()).isEqualTo(LIMITE_TRANSCRICOES);
        assertThat(transcricoes.restantes()).isEqualTo(1);
        assertThat(correcoes.usadas()).isEqualTo(1);
        assertThat(correcoes.restantes()).isEqualTo(LIMITE - 1);
    }

    @Test
    void renovaNaMeiaNoiteDeSaoPaulo() {
        var resultado = service.consultar(alunoId);

        assertThat(resultado.renovaEm())
                .isEqualTo(OffsetDateTime.of(2026, 9, 29, 0, 0, 0, 0, ZoneOffset.ofHours(-3)));
    }

    // ---------- limite por plano ----------

    @Test
    void gratisMostraTresCorrecoesEDuasTranscricoes() {
        assertThat(service.consultar(alunoId).limite()).isEqualTo(3);
        assertThat(service.consultarTranscricoes(alunoId).limite()).isEqualTo(2);
    }

    @Test
    void proMostraDezCorrecoesEDezTranscricoes() {
        when(planos.doUsuario(alunoId)).thenReturn(Plano.PRO);

        assertThat(service.consultar(alunoId).limite()).isEqualTo(10);
        assertThat(service.consultarTranscricoes(alunoId).limite()).isEqualTo(10);
    }

    @Test
    void proComQuatroCorrecoesUsadasAindaTemSeis() {
        when(planos.doUsuario(alunoId)).thenReturn(Plano.PRO);
        for (int i = 0; i < 4; i++) {
            uso.reservarCorrecao(alunoId, HOJE_EM_SP, 10);
        }

        var resultado = service.consultar(alunoId);

        assertThat(resultado.usadas()).isEqualTo(4);
        assertThat(resultado.restantes()).isEqualTo(6);
    }
}
