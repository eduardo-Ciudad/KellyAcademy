package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.CompetenciaContexto;
import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.RedacaoSemCorrecaoException;
import eduar.studymindredacao.domain.model.Avaliacao;
import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.port.AvaliacaoRepositoryPort;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Monta o que a Júlia pode saber de uma redação: tema, texto, nota, as 5 competências e o diagnóstico.
 * Só redações corrigidas e não anuladas entram (anulação fica fora da Júlia no MVP).
 * Quem chama já conferiu que a redação é do aluno.
 */
@Component
public class MontadorContextoJulia {
    static final String TEMA_NAO_INFORMADO = "Tema não informado";
    private static final int TOTAL_COMPETENCIAS = 5;

    private final AvaliacaoRepositoryPort avaliacaoRepository;
    private final TemaRepositoryPort temaRepository;

    public MontadorContextoJulia(AvaliacaoRepositoryPort avaliacaoRepository, TemaRepositoryPort temaRepository) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.temaRepository = temaRepository;
    }

    public ContextoJulia montar(Redacao redacao) {
        Objects.requireNonNull(redacao, "redacao é obrigatória");
        if (redacao.status() != StatusRedacao.AVALIADA) {
            throw new RedacaoSemCorrecaoException();
        }
        Avaliacao avaliacao = avaliacaoRepository.buscarPorRedacaoId(redacao.id())
                .orElseThrow(RedacaoSemCorrecaoException::new);
        if (avaliacao.anulada() || avaliacao.competencias().size() != TOTAL_COMPETENCIAS) {
            throw new RedacaoSemCorrecaoException();
        }

        String tema = temaRepository.buscarPorId(redacao.temaId())
                .map(Tema::titulo)
                .orElse(TEMA_NAO_INFORMADO);
        List<CompetenciaContexto> competencias = avaliacao.competencias().stream()
                .map(MontadorContextoJulia::paraContexto)
                .toList();

        return new ContextoJulia(tema, redacao.texto(), avaliacao.notaTotal(), competencias,
                avaliacao.diagnostico(), null);
    }

    private static CompetenciaContexto paraContexto(CompetenciaAvaliada competencia) {
        return new CompetenciaContexto(competencia.numero(), competencia.nota(), competencia.resumo(),
                competencia.problemasIdentificados());
    }
}
