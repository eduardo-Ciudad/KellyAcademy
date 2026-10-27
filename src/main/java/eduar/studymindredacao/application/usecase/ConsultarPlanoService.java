package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Plano do aluno a partir do espelho local das assinaturas e das concessões manuais (sem chamar o Stripe).
 * Fica fora do JWT de propósito: o token dura horas e ficaria desatualizado depois de um pagamento, um cancelamento
 * ou de uma concessão/revogação feita pelo admin.
 */
@Service
public class ConsultarPlanoService {
    private final AssinaturaRepositoryPort assinaturaRepository;
    private final ConcessaoProRepositoryPort concessaoRepository;
    private final Clock clock;

    public ConsultarPlanoService(
            AssinaturaRepositoryPort assinaturaRepository,
            ConcessaoProRepositoryPort concessaoRepository,
            Clock clock
    ) {
        this.assinaturaRepository = assinaturaRepository;
        this.concessaoRepository = concessaoRepository;
        this.clock = clock;
    }

    /** PRO se alguma assinatura ou concessão dá acesso agora; senão GRATIS. */
    public Plano doUsuario(UUID usuarioId) {
        return detalhar(usuarioId).plano();
    }

    /**
     * Plano e o que dá o acesso, nesta ordem:
     * <ol>
     *   <li>assinatura vigente (com mais de uma, vale a que termina por último). Assinatura com pagamento atrasado
     *       (PAST_DUE) ou vencida não conta (MVP, sem aviso). Achando uma, nem consulta as concessões;</li>
     *   <li>concessão manual aberta e dentro do prazo;</li>
     *   <li>senão, GRATIS.</li>
     * </ol>
     */
    public PlanoDoUsuario detalhar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);

        Optional<Assinatura> assinatura = assinaturaRepository.listarPorUsuario(usuarioId).stream()
                .filter(a -> a.daAcessoPro(agora))
                .max(Comparator.comparing(Assinatura::periodoAtualFim));
        if (assinatura.isPresent()) {
            return PlanoDoUsuario.pro(assinatura.get());
        }

        return concessaoRepository.buscarAberta(usuarioId)
                .filter(concessao -> concessao.vigente(agora))
                .map(PlanoDoUsuario::proPorConcessao)
                .orElseGet(PlanoDoUsuario::gratis);
    }

    /** Barra o recurso quando o plano do aluno não o libera. */
    public void exigir(UUID usuarioId, RecursoPro recurso) {
        Objects.requireNonNull(recurso, "recurso é obrigatório");
        if (!doUsuario(usuarioId).libera(recurso)) {
            throw new RecursoExclusivoProException(recurso);
        }
    }
}
