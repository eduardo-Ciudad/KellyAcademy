package eduar.studymindredacao.domain.model.admin;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tudo o que o admin vê de um aluno.
 *
 * @param melhorNota           maior nota total entre as correções; null sem correção
 * @param competenciaMaisFraca menor média entre as 5 competências (empate: a de menor número); null sem correção
 * @param assinatura           a assinatura mais recente (qualquer status); null se nunca assinou
 * @param stripeCustomerId     cliente no Stripe, para o admin achar no painel do Stripe; null se nunca iniciou checkout
 * @param concessaoAberta      concessão manual não revogada (vigente ou vencida); null se não há
 * @param concessoes           histórico das concessões, da mais recente para a mais antiga
 * @param redacoes             as últimas redações, da mais recente para a mais antiga
 */
public record UsuarioDetalheAdmin(
        UsuarioResumoAdmin usuario,
        Integer melhorNota,
        CompetenciaMaisFraca competenciaMaisFraca,
        Assinatura assinatura,
        String stripeCustomerId,
        ConcessaoPro concessaoAberta,
        List<ConcessaoPro> concessoes,
        List<UsoHoje> usoHoje,
        AtividadeJulia julia,
        List<RedacaoAdmin> redacoes
) {
    public UsuarioDetalheAdmin {
        Objects.requireNonNull(usuario, "usuario é obrigatório");
        Objects.requireNonNull(julia, "julia é obrigatório");
        concessoes = List.copyOf(concessoes);
        usoHoje = List.copyOf(usoHoje);
        redacoes = List.copyOf(redacoes);
    }

    public record CompetenciaMaisFraca(int competencia, double media) {
    }

    public record UsoHoje(RecursoIA recurso, int usados, int limite) {
    }

    public record AtividadeJulia(long conversas, long mensagens) {
    }

    /** {@code nota} é null enquanto a redação não foi corrigida (ou deu erro). */
    public record RedacaoAdmin(UUID id, OffsetDateTime enviadaEm, String tema, OrigemRedacao origem,
                               StatusRedacao status, Integer nota) {
    }
}
