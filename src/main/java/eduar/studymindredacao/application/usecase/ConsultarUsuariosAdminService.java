package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.UsuarioNaoEncontradoException;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.admin.FiltroUsuariosAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.CompetenciaMaisFraca;
import eduar.studymindredacao.domain.model.admin.UsuarioDetalheAdmin.UsoHoje;
import eduar.studymindredacao.domain.model.admin.UsuarioResumoAdmin;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import eduar.studymindredacao.domain.port.PainelAdminQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lista e detalhe de alunos no painel admin. Só leitura: as ações ficam em outro caso de uso. */
@Service
public class ConsultarUsuariosAdminService {
    static final int ULTIMAS_REDACOES = 20;

    private final PainelAdminQueryPort painel;
    private final AssinaturaRepositoryPort assinaturas;
    private final ConcessaoProRepositoryPort concessoes;
    private final ConsultarPlanoService consultarPlano;
    private final PoliticaJulia politicaJulia;
    private final Clock clock;
    private final Set<StatusAssinatura> statusComAcesso;

    public ConsultarUsuariosAdminService(
            PainelAdminQueryPort painel,
            AssinaturaRepositoryPort assinaturas,
            ConcessaoProRepositoryPort concessoes,
            ConsultarPlanoService consultarPlano,
            PoliticaJulia politicaJulia,
            Clock clock
    ) {
        this.painel = painel;
        this.assinaturas = assinaturas;
        this.concessoes = concessoes;
        this.consultarPlano = consultarPlano;
        this.politicaJulia = politicaJulia;
        this.clock = clock;
        this.statusComAcesso = Arrays.stream(StatusAssinatura.values())
                .filter(StatusAssinatura::concedeAcesso)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StatusAssinatura.class)));
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioResumoAdmin> listar(FiltroUsuariosAdmin filtro) {
        Objects.requireNonNull(filtro, "filtro é obrigatório");
        return painel.listarUsuarios(filtro, OffsetDateTime.now(clock), statusComAcesso);
    }

    /** @throws UsuarioNaoEncontradoException id que não existe (vira 404) */
    @Transactional(readOnly = true)
    public UsuarioDetalheAdmin detalhar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = agora.atZoneSameInstant(clock.getZone()).toLocalDate();

        UsuarioResumoAdmin usuario = painel.buscarResumo(usuarioId, agora, statusComAcesso)
                .orElseThrow(UsuarioNaoEncontradoException::new);

        // O limite de hoje vem da mesma regra que barra o aluno de verdade (não do SQL do resumo)
        Plano plano = consultarPlano.doUsuario(usuarioId);
        List<ConcessaoPro> historico = concessoes.listarPorUsuario(usuarioId);

        return new UsuarioDetalheAdmin(
                usuario,
                painel.melhorNota(usuarioId).orElse(null),
                competenciaMaisFraca(painel.mediasPorCompetenciaDoUsuario(usuarioId)),
                assinaturaMaisRecente(assinaturas.listarPorUsuario(usuarioId)),
                assinaturas.buscarStripeCustomerId(usuarioId).orElse(null),
                historico.stream().filter(c -> !c.revogada()).findFirst().orElse(null),
                historico,
                usoHoje(painel.usoDoUsuarioNoDia(usuarioId, hoje), plano),
                painel.atividadeJulia(usuarioId),
                painel.ultimasRedacoes(usuarioId, ULTIMAS_REDACOES)
        );
    }

    /** Menor média; empate fica com a competência de menor número. Sem nenhuma média, null. */
    static CompetenciaMaisFraca competenciaMaisFraca(Map<Integer, Double> medias) {
        return medias.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .min(Comparator.<Map.Entry<Integer, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(e -> new CompetenciaMaisFraca(e.getKey(), e.getValue()))
                .orElse(null);
    }

    /** Pelo maior criadoEm (o port já devolve nessa ordem, mas a regra não depende disso). */
    private static Assinatura assinaturaMaisRecente(List<Assinatura> lista) {
        return lista.stream().max(Comparator.comparing(Assinatura::criadoEm)).orElse(null);
    }

    /** Os 3 recursos sempre, com o limite do plano atual (a Júlia tem limite próprio na política). */
    private List<UsoHoje> usoHoje(Map<RecursoIA, Integer> usados, Plano plano) {
        return Arrays.stream(RecursoIA.values())
                .map(recurso -> new UsoHoje(recurso, usados.getOrDefault(recurso, 0), limite(recurso, plano)))
                .toList();
    }

    private int limite(RecursoIA recurso, Plano plano) {
        return switch (recurso) {
            case CORRECAO, TRANSCRICAO -> plano.limiteDiario(recurso);
            case JULIA -> politicaJulia.limiteDiario();
        };
    }
}
