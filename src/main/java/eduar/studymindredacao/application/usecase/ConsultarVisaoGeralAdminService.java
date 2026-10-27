package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.Alertas;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.FaixaNota;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.MediaCompetencia;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.PontoDiario;
import eduar.studymindredacao.domain.model.admin.VisaoGeralAdmin.UsoIA;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.PainelAdminQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Monta a "Visão geral" do painel admin. O SQL só conta e agrupa; preencher os dias vazios da série, montar as
 * faixas de nota e decidir as datas no fuso certo fica aqui, onde dá para testar sem banco.
 */
@Service
public class ConsultarVisaoGeralAdminService {
    static final Set<Integer> PERIODOS_PERMITIDOS = Set.of(7, 30, 90);
    static final int ULTIMOS_CADASTROS = 5;
    static final Duration JANELA_ERROS = Duration.ofHours(24);
    static final Duration JANELA_SEM_VERIFICACAO = Duration.ofHours(48);
    /** Faixas fechadas de nota total. A última vai até 1000. */
    static final List<int[]> FAIXAS = List.of(
            new int[]{0, 199}, new int[]{200, 399}, new int[]{400, 599}, new int[]{600, 799}, new int[]{800, 1000});

    private final PainelAdminQueryPort painel;
    private final Clock clock;
    private final int tetoCorrecoes;
    private final int tetoTranscricoes;
    private final int tetoJulia;
    private final Set<StatusAssinatura> statusComAcesso;

    public ConsultarVisaoGeralAdminService(
            PainelAdminQueryPort painel,
            PoliticaJulia politicaJulia,
            @Value("${app.limites.correcoes-diarias-globais}") int tetoCorrecoes,
            @Value("${app.limites.transcricoes-diarias-globais}") int tetoTranscricoes,
            Clock clock
    ) {
        this.painel = painel;
        this.clock = clock;
        this.tetoCorrecoes = tetoCorrecoes;
        this.tetoTranscricoes = tetoTranscricoes;
        this.tetoJulia = politicaJulia.tetoGlobal();
        // Vem do enum: se um status passar a dar (ou deixar de dar) acesso, o painel acompanha sozinho
        this.statusComAcesso = Arrays.stream(StatusAssinatura.values())
                .filter(StatusAssinatura::concedeAcesso)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StatusAssinatura.class)));
    }

    /**
     * @param periodo 7, 30 ou 90 dias, contando hoje (no fuso do sistema)
     * @throws IllegalArgumentException período fora dos permitidos (vira 400)
     */
    @Transactional(readOnly = true)
    public VisaoGeralAdmin consultar(int periodo) {
        if (!PERIODOS_PERMITIDOS.contains(periodo)) {
            throw new IllegalArgumentException("periodo deve ser 7, 30 ou 90");
        }
        ZoneId fuso = clock.getZone();
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = agora.atZoneSameInstant(fuso).toLocalDate();
        LocalDate primeiroDia = hoje.minusDays(periodo - 1L);
        OffsetDateTime de = inicioDoDia(primeiroDia, fuso);
        OffsetDateTime inicioDeHoje = inicioDoDia(hoje, fuso);
        OffsetDateTime inicioDeAmanha = inicioDoDia(hoje.plusDays(1), fuso);

        return new VisaoGeralAdmin(
                periodo,
                agora,
                painel.contarUsuarios(),
                painel.contarCadastros(de, agora),
                painel.contarAlunosAtivos(de, agora),
                painel.contarCorrecoes(de, agora),
                painel.mediaNotas(de, agora).orElse(null),
                painel.contarAssinantesPro(agora, statusComAcesso),
                serieDiaria(primeiroDia, hoje, painel.cadastrosPorDia(de, agora, fuso),
                        painel.correcoesPorDia(de, agora, fuso)),
                usoIaHoje(painel.usoGlobalDoDia(hoje)),
                painel.tokensDoDia(hoje, inicioDeHoje, inicioDeAmanha),
                painel.contarNoLimiteDeCorrecoes(hoje, Plano.GRATIS.limiteDiario(RecursoIA.CORRECAO),
                        Plano.PRO.limiteDiario(RecursoIA.CORRECAO), agora, statusComAcesso),
                distribuicao(painel.contagemPorNota(de, agora)),
                mediasPorCompetencia(painel.mediasPorCompetencia(de, agora)),
                painel.ultimosCadastros(ULTIMOS_CADASTROS, agora, statusComAcesso),
                new Alertas(
                        painel.contarCorrecoesComErroDesde(agora.minus(JANELA_ERROS)),
                        painel.contarNaoVerificadasCriadasAntesDe(agora.minus(JANELA_SEM_VERIFICACAO)),
                        painel.ultimoEventoStripe().orElse(null)
                )
        );
    }

    private static OffsetDateTime inicioDoDia(LocalDate dia, ZoneId fuso) {
        return dia.atStartOfDay(fuso).toOffsetDateTime();
    }

    /** Um ponto por dia do período, em ordem; dia sem nada entra com zero. */
    static List<PontoDiario> serieDiaria(LocalDate primeiroDia, LocalDate ultimoDia,
                                         Map<LocalDate, Long> cadastros, Map<LocalDate, Long> correcoes) {
        var serie = new ArrayList<PontoDiario>();
        for (LocalDate dia = primeiroDia; !dia.isAfter(ultimoDia); dia = dia.plusDays(1)) {
            serie.add(new PontoDiario(dia, cadastros.getOrDefault(dia, 0L), correcoes.getOrDefault(dia, 0L)));
        }
        return serie;
    }

    /** Sempre os 3 recursos, na ordem do enum, mesmo sem uso no dia. */
    private List<UsoIA> usoIaHoje(Map<RecursoIA, Integer> usados) {
        return Arrays.stream(RecursoIA.values())
                .map(recurso -> new UsoIA(recurso, usados.getOrDefault(recurso, 0), teto(recurso)))
                .toList();
    }

    /** Switch expression: recurso novo no enum sem teto aqui não compila. */
    private int teto(RecursoIA recurso) {
        return switch (recurso) {
            case CORRECAO -> tetoCorrecoes;
            case TRANSCRICAO -> tetoTranscricoes;
            case JULIA -> tetoJulia;
        };
    }

    /** Todas as faixas aparecem, mesmo vazias. Nota fora de 0–1000 (não deveria existir) não entra em nenhuma. */
    static List<FaixaNota> distribuicao(Map<Integer, Long> contagemPorNota) {
        return FAIXAS.stream()
                .map(faixa -> new FaixaNota(faixa[0], faixa[1], contagemPorNota.entrySet().stream()
                        .filter(e -> e.getKey() >= faixa[0] && e.getKey() <= faixa[1])
                        .mapToLong(Map.Entry::getValue)
                        .sum()))
                .toList();
    }

    /** As 5 competências sempre presentes; sem dado, média nula. */
    static List<MediaCompetencia> mediasPorCompetencia(Map<Integer, Double> medias) {
        var lista = new ArrayList<MediaCompetencia>();
        for (int competencia = 1; competencia <= 5; competencia++) {
            lista.add(new MediaCompetencia(competencia, medias.get(competencia)));
        }
        return lista;
    }
}
