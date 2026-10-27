package eduar.studymindredacao.adapter.in.web.dto.admin;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Formato do AdminUsuarioDetalhe do front, mais {@code concessaoPro} (a aberta) e {@code concessoes} (histórico),
 * que o front passa a usar no painel de alunos.
 */
public record AdminUsuarioDetalheResponse(
        AdminUsuarioResumoResponse usuario,
        Integer melhorNota,
        CompetenciaMaisFraca competenciaMaisFraca,
        AssinaturaAdmin assinatura,
        List<UsoHoje> usoHoje,
        Julia julia,
        List<Redacao> redacoes,
        ConcessaoProResponse concessaoPro,
        List<ConcessaoProResponse> concessoes
) {
    public record CompetenciaMaisFraca(int competencia, double media) {
    }

    public record AssinaturaAdmin(StatusAssinatura status, CicloAssinatura ciclo, OffsetDateTime periodoAtualFim,
                                  boolean cancelarNoFimDoPeriodo, String stripeCustomerId) {
    }

    public record UsoHoje(RecursoIA recurso, int usados, int limite) {
    }

    public record Julia(long conversas, long mensagens) {
    }

    public record Redacao(UUID id, OffsetDateTime enviadaEm, String tema, OrigemRedacao origem, StatusRedacao status,
                          Integer nota) {
    }

    public static AdminUsuarioDetalheResponse de(UsuarioDetalheAdmin d, OffsetDateTime agora) {
        return new AdminUsuarioDetalheResponse(
                AdminUsuarioResumoResponse.de(d.usuario()),
                d.melhorNota(),
                d.competenciaMaisFraca() == null ? null : new CompetenciaMaisFraca(
                        d.competenciaMaisFraca().competencia(),
                        Arredondamento.umaCasa(d.competenciaMaisFraca().media())),
                assinatura(d.assinatura(), d.stripeCustomerId()),
                d.usoHoje().stream().map(u -> new UsoHoje(u.recurso(), u.usados(), u.limite())).toList(),
                new Julia(d.julia().conversas(), d.julia().mensagens()),
                d.redacoes().stream()
                        .map(r -> new Redacao(r.id(), r.enviadaEm(), r.tema(), r.origem(), r.status(), r.nota()))
                        .toList(),
                ConcessaoProResponse.de(d.concessaoAberta(), agora),
                d.concessoes().stream().map(c -> ConcessaoProResponse.de(c, agora)).toList()
        );
    }

    /** Sem assinatura, null (mesmo que exista stripeCustomerId de um checkout abandonado). */
    private static AssinaturaAdmin assinatura(Assinatura a, String stripeCustomerId) {
        if (a == null) {
            return null;
        }
        return new AssinaturaAdmin(a.status(), a.ciclo(), a.periodoAtualFim(), a.cancelarNoFimDoPeriodo(),
                stripeCustomerId);
    }
}
