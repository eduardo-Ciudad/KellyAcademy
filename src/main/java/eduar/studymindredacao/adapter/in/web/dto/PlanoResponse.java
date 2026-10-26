package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.PlanoDoUsuario;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.RecursoPro;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Plano do aluno para o front travar e destravar telas (quem decide de verdade continua sendo o backend).
 * Não expõe ids do Stripe nem o status cru da assinatura.
 */
public record PlanoResponse(String plano, Limites limites, List<String> recursos, AssinaturaResumo assinatura) {

    public record Limites(int correcoesPorDia, int transcricoesPorDia) {
    }

    public record AssinaturaResumo(CicloAssinatura ciclo, OffsetDateTime periodoAtualFim,
                                   boolean cancelarNoFimDoPeriodo) {
        static AssinaturaResumo de(Assinatura assinatura) {
            if (assinatura == null) {
                return null;
            }
            return new AssinaturaResumo(assinatura.ciclo(), assinatura.periodoAtualFim(),
                    assinatura.cancelarNoFimDoPeriodo());
        }
    }

    public static PlanoResponse de(PlanoDoUsuario detalhe) {
        Plano plano = detalhe.plano();
        var limites = new Limites(plano.limiteDiario(RecursoIA.CORRECAO), plano.limiteDiario(RecursoIA.TRANSCRICAO));
        List<String> recursos = Arrays.stream(RecursoPro.values())
                .filter(plano::libera)
                .map(Enum::name)
                .toList();
        return new PlanoResponse(plano.name(), limites, recursos, AssinaturaResumo.de(detalhe.assinaturaVigente()));
    }
}
