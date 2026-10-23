package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.TemaNaoEncontradoException;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.model.enums.TipoRedacao;
import eduar.studymindredacao.domain.port.AvaliacaoIAPort;
import eduar.studymindredacao.domain.port.RedacaoRepositoryPort;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import eduar.studymindredacao.domain.service.RegrasDaGrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fluxo síncrono de correção:
 * valida texto e tema → permissão de concorrência → reserva a cota (teto global e vaga do aluno)
 * → salva a redação EM_AVALIACAO → chama a IA → confere pela grade → conclui.
 * Se a IA (ou a conclusão) falhar, a redação vai para ERRO e as duas vagas são devolvidas.
 * A permissão (1 correção por aluno, N no sistema) só é solta depois de tudo gravado (KELLY-SEC-002).
 * Sem @Transactional de propósito: cada passo tem a sua transação curta.
 */
@Service
public class EnviarRedacaoService {
    static final int TAMANHO_MAXIMO_TEXTO = 5000;
    private static final Logger log = LoggerFactory.getLogger(EnviarRedacaoService.class);

    private final TemaRepositoryPort temaRepository;
    private final RedacaoRepositoryPort redacaoRepository;
    private final AvaliacaoIAPort avaliacaoIA;
    private final ConcluirAvaliacaoService concluirAvaliacao;
    private final ReservadorCotaIA reservador;
    private final LimitadorConcorrenciaIA limitador;
    private final Clock clock;
    private final int limiteDiario;
    private final int limiteGlobal;

    public EnviarRedacaoService(
            TemaRepositoryPort temaRepository,
            RedacaoRepositoryPort redacaoRepository,
            AvaliacaoIAPort avaliacaoIA,
            ConcluirAvaliacaoService concluirAvaliacao,
            ReservadorCotaIA reservador,
            LimitadorConcorrenciaIA limitador,
            Clock clock,
            @Value("${app.limites.correcoes-diarias-free}") int limiteDiario,
            @Value("${app.limites.correcoes-diarias-globais}") int limiteGlobal
    ) {
        this.temaRepository = temaRepository;
        this.redacaoRepository = redacaoRepository;
        this.avaliacaoIA = avaliacaoIA;
        this.concluirAvaliacao = concluirAvaliacao;
        this.reservador = reservador;
        this.limitador = limitador;
        this.clock = clock;
        this.limiteDiario = limiteDiario;
        this.limiteGlobal = limiteGlobal;
    }

    public RedacaoDetalhada enviar(UUID usuarioId, UUID temaId, TipoRedacao tipo, String texto, OrigemRedacao origem) {
        validarTexto(texto);
        var tema = temaRepository.buscarPorId(temaId)
                .filter(t -> Boolean.TRUE.equals(t.ativo()))
                .orElseThrow(() -> new TemaNaoEncontradoException(temaId));

        try (var permissao = limitador.adquirir(usuarioId, RecursoIA.CORRECAO)) {
            LocalDate hoje = LocalDate.now(clock);
            ReservaCota reserva = reservador.reservar(usuarioId, RecursoIA.CORRECAO, hoje, limiteDiario, limiteGlobal);

            Redacao redacao;
            try {
                redacao = redacaoRepository.salvar(
                        new Redacao(null, usuarioId, temaId, tipo, texto, StatusRedacao.EM_AVALIACAO, null, origem)
                );
            } catch (RuntimeException e) {
                reserva.liberar();
                throw e;
            }

            try {
                var resultado = avaliacaoIA.avaliar(new SolicitacaoAvaliacao(tema.titulo(), texto));
                var conferido = RegrasDaGrade.aplicar(resultado);
                registrarConferencia(redacao.id(), conferido);
                return concluirAvaliacao.concluir(redacao, tema, conferido.avaliacao(), hoje);
            } catch (RuntimeException e) {
                log.warn("Correção da redação {} falhou: {}", redacao.id(), LogSeguro.limpar(e.getMessage()));
                marcarComoErro(redacao, e);
                reserva.liberar();
                throw e;
            }
        }
    }

    /** Log sem texto do aluno: só id, competência, notas e a regra aplicada (#27). */
    private static void registrarConferencia(UUID redacaoId, RegrasDaGrade.Resultado conferido) {
        for (RegrasDaGrade.Ajuste ajuste : conferido.ajustes()) {
            log.info("Grade limitou C{} da redação {}: {} -> {} ({})", ajuste.competencia(), redacaoId,
                    ajuste.notaIA(), ajuste.notaFinal(), LogSeguro.limpar(ajuste.motivo()));
        }
        for (String incoerencia : conferido.incoerencias()) {
            log.warn("Correção incoerente na redação {}: {}", redacaoId, LogSeguro.limpar(incoerencia));
        }
    }

    private void marcarComoErro(Redacao redacao, RuntimeException causa) {
        try {
            redacaoRepository.salvar(redacao.comStatus(StatusRedacao.ERRO));
        } catch (RuntimeException falhaAoMarcar) {
            causa.addSuppressed(falhaAoMarcar);
        }
    }

    private static void validarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("texto da redação é obrigatório");
        }
        if (texto.length() > TAMANHO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException(
                    "texto da redação deve ter no máximo " + TAMANHO_MAXIMO_TEXTO + " caracteres"
            );
        }
    }
}
