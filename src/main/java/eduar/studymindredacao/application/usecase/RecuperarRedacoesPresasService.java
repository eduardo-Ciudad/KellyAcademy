package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.config.SchedulingConfig;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.port.RedacaoRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Marca como ERRO as redações presas em EM_AVALIACAO há mais de {@link #TEMPO_MAXIMO_EM_AVALIACAO}
 * (a API caiu ou travou no meio da correção) e devolve a vaga do aluno no dia do envio.
 *
 * <p>Devolve só a vaga do aluno, NUNCA a do teto global (KELLY-SEC-002). Uma redação presa provavelmente
 * chegou a chamar o Gemini, e a chamada pode ter sido cobrada; o teto global é de custo, e devolvê-lo seria
 * fingir que um custo possivelmente real não aconteceu. Se errar, erra contando a mais. Por isso este job
 * nem recebe o UsoIAGlobalRepositoryPort: não injete o ReservadorCotaIA aqui.
 *
 * <p>O tempo máximo precisa ficar bem acima do pior caso de uma correção (timeout do Gemini × tentativas),
 * senão o job marcaria ERRO numa correção ainda em andamento e devolveria a vaga dela.
 */
@Service
public class RecuperarRedacoesPresasService {
    private static final Logger log = LoggerFactory.getLogger(RecuperarRedacoesPresasService.class);
    private final RedacaoRepositoryPort redacaoRepository;
    private final Clock clock;
    private final UsoIADiarioRepositoryPort usoRepository;
    static final Duration TEMPO_MAXIMO_EM_AVALIACAO = Duration.ofMinutes(10);

    public RecuperarRedacoesPresasService(RedacaoRepositoryPort redacaoRepository, Clock clock, UsoIADiarioRepositoryPort usoRepository) {
        this.redacaoRepository = redacaoRepository;
        this.clock = clock;
        this.usoRepository = usoRepository;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public int recuperar() {
        var corte = OffsetDateTime.now(clock).minus(TEMPO_MAXIMO_EM_AVALIACAO);
        var presas = redacaoRepository.listarPorStatusEnviadasAntesDe(StatusRedacao.EM_AVALIACAO, corte);
        for (Redacao presa : presas) {
            LocalDate dia = presa.enviadaEm().atZoneSameInstant(clock.getZone()).toLocalDate();
            redacaoRepository.salvar(presa.comStatus(StatusRedacao.ERRO));
            usoRepository.liberarCorrecao(presa.usuarioId(), dia);
        }
        if (!presas.isEmpty()) {
            log.warn("{} redação(ões) presa(s) em EM_AVALIACAO marcadas como ERRO", presas.size());
        }
        return presas.size();
    }
}
