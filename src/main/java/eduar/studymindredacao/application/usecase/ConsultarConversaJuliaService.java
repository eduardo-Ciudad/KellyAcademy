package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.port.ConversaJuliaRepositoryPort;
import eduar.studymindredacao.domain.port.RedacaoRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversa da Júlia que o aluno vê ao abrir o chat numa redação.
 * Só lê: uma conversa expirada aparece vazia, e quem a encerra de fato é o próximo envio.
 */
@Service
public class ConsultarConversaJuliaService {
    private final RedacaoRepositoryPort redacaoRepository;
    private final ConversaJuliaRepositoryPort conversaRepository;
    private final UsoIADiarioRepositoryPort usoRepository;
    private final PoliticaJulia politica;
    private final Clock clock;

    public ConsultarConversaJuliaService(
            RedacaoRepositoryPort redacaoRepository,
            ConversaJuliaRepositoryPort conversaRepository,
            UsoIADiarioRepositoryPort usoRepository,
            PoliticaJulia politica,
            Clock clock
    ) {
        this.redacaoRepository = redacaoRepository;
        this.conversaRepository = conversaRepository;
        this.usoRepository = usoRepository;
        this.politica = politica;
        this.clock = clock;
    }

    public ConversaJuliaDetalhada consultar(UUID usuarioId, UUID redacaoId) {
        redacaoRepository.buscarPorId(redacaoId)
                .filter(r -> r.usuarioId().equals(usuarioId))
                .orElseThrow(() -> new RedacaoNaoEncontradaException(redacaoId));

        int restantesHoje = politica.restantesHoje(usoRepository.contarMensagensJulia(usuarioId, LocalDate.now(clock)));
        Optional<ConversaJulia> conversa = conversaRepository.buscarAtiva(usuarioId, redacaoId)
                .filter(c -> !c.expirada(clock.instant(), politica.limites().inatividadeMaxima()));

        if (conversa.isEmpty()) {
            return new ConversaJuliaDetalhada(List.of(), politica.limites().limitePorConversa(), restantesHoje);
        }
        return new ConversaJuliaDetalhada(
                conversa.get().getMensagens(),
                conversa.get().perguntasRestantes(politica.limites()),
                restantesHoje
        );
    }
}
