package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.admin.FiltroUsuariosAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.AtividadeJulia;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.RedacaoAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consultas só de leitura do painel admin. Fica separado dos ports de cada agregado porque são números de relatório
 * (contagens, médias, agrupamentos), não regras de negócio.
 * <p>
 * Intervalos são sempre {@code [de, ate)}. "Ser Pro agora" segue a mesma regra do ConsultarPlanoService: assinatura
 * com status em {@code statusComAcesso} e período não vencido, ou concessão manual aberta e dentro do prazo.
 */
public interface PainelAdminQueryPort {

    long contarUsuarios();

    long contarCadastros(OffsetDateTime de, OffsetDateTime ate);

    /** Alunos distintos que enviaram pelo menos uma redação no intervalo. */
    long contarAlunosAtivos(OffsetDateTime de, OffsetDateTime ate);

    /** Avaliações concluídas no intervalo (pela data da avaliação). */
    long contarCorrecoes(OffsetDateTime de, OffsetDateTime ate);

    /** Média da nota total das avaliações do intervalo; vazio sem avaliações. Anulada entra com a nota gravada (0). */
    Optional<Double> mediaNotas(OffsetDateTime de, OffsetDateTime ate);

    long contarAssinantesPro(OffsetDateTime agora, Set<StatusAssinatura> statusComAcesso);

    /** Cadastros por dia no fuso informado; dias sem cadastro não aparecem. */
    Map<LocalDate, Long> cadastrosPorDia(OffsetDateTime de, OffsetDateTime ate, ZoneId fuso);

    /** Correções por dia no fuso informado; dias sem correção não aparecem. */
    Map<LocalDate, Long> correcoesPorDia(OffsetDateTime de, OffsetDateTime ate, ZoneId fuso);

    /** Uso somado de todos os alunos no dia (tabela do teto global); recurso sem uso não aparece. */
    Map<RecursoIA, Integer> usoGlobalDoDia(LocalDate dia);

    /** Tokens de entrada + saída do dia: correção e transcrição (uso_ia_diario) e Júlia (mensagens no intervalo). */
    long tokensDoDia(LocalDate dia, OffsetDateTime inicioDoDia, OffsetDateTime fimDoDia);

    /** Alunos cujas correções do dia já chegaram ao limite do próprio plano. */
    long contarNoLimiteDeCorrecoes(LocalDate dia, int limiteGratis, int limitePro, OffsetDateTime agora,
                                   Set<StatusAssinatura> statusComAcesso);

    /** Quantas avaliações do intervalo tiveram cada nota total (são múltiplos de 40, então poucas chaves). */
    Map<Integer, Long> contagemPorNota(OffsetDateTime de, OffsetDateTime ate);

    /** Média de cada competência (chaves 1 a 5) no intervalo; sem avaliações, o mapa vem vazio. */
    Map<Integer, Double> mediasPorCompetencia(OffsetDateTime de, OffsetDateTime ate);

    /** Os cadastros mais recentes, do mais novo para o mais antigo. */
    List<UsuarioResumoAdmin> ultimosCadastros(int limite, OffsetDateTime agora, Set<StatusAssinatura> statusComAcesso);

    long contarCorrecoesComErroDesde(OffsetDateTime desde);

    long contarNaoVerificadasCriadasAntesDe(OffsetDateTime limite);

    Optional<OffsetDateTime> ultimoEventoStripe();

    // ---------- usuários ----------

    /** Listagem com filtros, dos cadastros mais novos para os mais antigos. */
    Pagina<UsuarioResumoAdmin> listarUsuarios(FiltroUsuariosAdmin filtro, OffsetDateTime agora,
                                              Set<StatusAssinatura> statusComAcesso);

    Optional<UsuarioResumoAdmin> buscarResumo(UUID usuarioId, OffsetDateTime agora,
                                              Set<StatusAssinatura> statusComAcesso);

    /** Maior nota total entre as correções do aluno; vazio sem correção. */
    Optional<Integer> melhorNota(UUID usuarioId);

    /** Média de cada competência (chaves 1 a 5) em todas as correções do aluno; vazio sem correção. */
    Map<Integer, Double> mediasPorCompetenciaDoUsuario(UUID usuarioId);

    /** Uso do aluno no dia (correções, transcrições, perguntas à Júlia); sem uso no dia, o mapa vem vazio. */
    Map<RecursoIA, Integer> usoDoUsuarioNoDia(UUID usuarioId, LocalDate dia);

    /** Conversas com a Júlia e perguntas feitas pelo aluno (as respostas dela não contam). */
    AtividadeJulia atividadeJulia(UUID usuarioId);

    /** As últimas redações do aluno com tema e nota, da mais recente para a mais antiga. */
    List<RedacaoAdmin> ultimasRedacoes(UUID usuarioId, int limite);
}
