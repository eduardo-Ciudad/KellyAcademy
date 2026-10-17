package eduar.studymindredacao.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio de teste que dá para avançar (ex.: fazer um token expirar). */
class RelogioAjustavel extends Clock {
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private Instant agora;

    RelogioAjustavel(Instant inicio) {
        this.agora = inicio;
    }

    static RelogioAjustavel em(String instante) {
        return new RelogioAjustavel(Instant.parse(instante));
    }

    void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return FUSO;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(agora, zone);
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
