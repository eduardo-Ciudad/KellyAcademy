package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.enums.OrigemPlano;
import eduar.studymindredacao.domain.model.enums.Plano;

import java.util.Objects;

/**
 * Plano do aluno e o que o sustenta.
 * <ul>
 *   <li>GRATIS: sem assinatura vigente e sem concessão vigente;</li>
 *   <li>PRO: com pelo menos uma das duas. Com as duas, a origem é a assinatura (é o que a tela de planos precisa
 *       para mostrar "gerenciar assinatura").</li>
 * </ul>
 */
public record PlanoDoUsuario(Plano plano, Assinatura assinaturaVigente, ConcessaoPro concessaoVigente) {
    public PlanoDoUsuario {
        Objects.requireNonNull(plano, "plano é obrigatório");
        boolean temAcessoPro = assinaturaVigente != null || concessaoVigente != null;
        if ((plano == Plano.PRO) != temAcessoPro) {
            throw new IllegalArgumentException(
                    "PRO exige assinatura ou concessão vigente e GRATIS não tem nenhuma das duas");
        }
    }

    /** Sem concessão: o formato de antes da concessão manual. */
    public PlanoDoUsuario(Plano plano, Assinatura assinaturaVigente) {
        this(plano, assinaturaVigente, null);
    }

    public static PlanoDoUsuario gratis() {
        return new PlanoDoUsuario(Plano.GRATIS, null, null);
    }

    public static PlanoDoUsuario pro(Assinatura assinaturaVigente) {
        Objects.requireNonNull(assinaturaVigente, "assinaturaVigente é obrigatória");
        return new PlanoDoUsuario(Plano.PRO, assinaturaVigente, null);
    }

    public static PlanoDoUsuario proPorConcessao(ConcessaoPro concessaoVigente) {
        Objects.requireNonNull(concessaoVigente, "concessaoVigente é obrigatória");
        return new PlanoDoUsuario(Plano.PRO, null, concessaoVigente);
    }

    /** De onde vem o Pro; null no GRATIS. */
    public OrigemPlano origem() {
        if (plano == Plano.GRATIS) {
            return null;
        }
        return assinaturaVigente != null ? OrigemPlano.ASSINATURA : OrigemPlano.CONCESSAO;
    }
}
