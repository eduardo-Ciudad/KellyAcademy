package eduar.studymindredacao.domain.model.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusAssinaturaTest {

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"ACTIVE", "TRIALING"})
    void statusQueLiberamOPro(StatusAssinatura status) {
        assertThat(status.concedeAcesso()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"ACTIVE", "TRIALING"}, mode = EnumSource.Mode.EXCLUDE)
    void demaisStatusNaoLiberamOPro(StatusAssinatura status) {
        // PAST_DUE sem tolerância: decisão de produto. Mudou a decisão, muda o enum e este teste junto.
        assertThat(status.concedeAcesso()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"CANCELED", "INCOMPLETE_EXPIRED"})
    void statusEncerrados(StatusAssinatura status) {
        assertThat(status.encerrada()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAssinatura.class, names = {"CANCELED", "INCOMPLETE_EXPIRED"},
            mode = EnumSource.Mode.EXCLUDE)
    void demaisStatusAindaEstaoVivos(StatusAssinatura status) {
        // PAST_DUE, UNPAID, PAUSED, INCOMPLETE: a assinatura pode voltar a cobrar; não deixa abrir outra
        assertThat(status.encerrada()).isFalse();
    }
}