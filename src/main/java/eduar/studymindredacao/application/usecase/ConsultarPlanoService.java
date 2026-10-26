package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano do aluno a partir do espelho local das assinaturas (sem chamar o Stripe).
 * Fica fora do JWT de propósito: o token dura horas e ficaria desatualizado depois de um pagamento ou cancelamento.
 */
@Service
public class ConsultarPlanoService {
    private final AssinaturaRepositoryPort assinaturaRepository;
    private final Clock clock;

    public ConsultarPlanoService(AssinaturaRepositoryPort assinaturaRepository, Clock clock) {
        this.assinaturaRepository = assinaturaRepository;
        this.clock = clock;
    }

    /** PRO se alguma assinatura dá acesso agora; senão GRATIS. */
    public Plano doUsuario(UUID usuarioId) {
        return detalhar(usuarioId).plano();
    }

    /**
     * Plano e a assinatura que dá o acesso. Com mais de uma válida, vale a que termina por último.
     * Assinatura com pagamento atrasado (PAST_DUE) ou vencida não aparece: o aluno fica GRATIS (MVP, sem aviso).
     */
    public PlanoDoUsuario detalhar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);
        return assinaturaRepository.listarPorUsuario(usuarioId).stream()
                .filter(a -> a.daAcessoPro(agora))
                .max(Comparator.comparing(Assinatura::periodoAtualFim))
                .map(PlanoDoUsuario::pro)
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
