package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.Plano;

import java.util.Objects;

/**
 * Plano do aluno e a assinatura que o sustenta.
 * assinaturaVigente é null no GRATIS e nunca é null no PRO.
 */
public record PlanoDoUsuario(Plano plano, Assinatura assinaturaVigente) {
    public PlanoDoUsuario {
        Objects.requireNonNull(plano, "plano é obrigatório");
        if ((plano == Plano.PRO) != (assinaturaVigente != null)) {
            throw new IllegalArgumentException("PRO exige assinatura vigente e GRATIS não tem assinatura vigente");
        }
    }

    public static PlanoDoUsuario gratis() {
        return new PlanoDoUsuario(Plano.GRATIS, null);
    }

    public static PlanoDoUsuario pro(Assinatura assinaturaVigente) {
        return new PlanoDoUsuario(Plano.PRO, assinaturaVigente);
    }
}
