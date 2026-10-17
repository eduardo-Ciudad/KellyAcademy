package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Apaga, uma vez por dia, os tokens expirados ou usados há mais de {@link #RETENCAO}. */
@Service
public class LimparTokensExpiradosService {
    private static final Logger log = LoggerFactory.getLogger(LimparTokensExpiradosService.class);
    static final Duration RETENCAO = Duration.ofDays(7);

    private final TokenUsuarioRepositoryPort tokenRepository;
    private final Clock clock;

    public LimparTokensExpiradosService(TokenUsuarioRepositoryPort tokenRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "${app.fuso-horario}")
    public int limpar() {
        int apagados = tokenRepository.excluirAntigos(OffsetDateTime.now(clock).minus(RETENCAO));
        if (apagados > 0) {
            log.info("Limpeza de tokens: {} removido(s)", apagados);
        }
        return apagados;
    }
}
