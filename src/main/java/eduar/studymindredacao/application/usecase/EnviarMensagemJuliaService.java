package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.julia.LimiteJuliaExcedidoException;
import eduar.studymindredacao.domain.julia.LimitesJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.julia.TipoLimiteJulia;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.port.ConversaJuliaRepositoryPort;
import eduar.studymindredacao.domain.port.JuliaIAPort;
import eduar.studymindredacao.domain.port.RedacaoRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pergunta à Júlia sobre a correção de uma redação do aluno.
 * Ordem pensada para nunca gastar cota à toa: dono da redação → correção existe → pergunta válida →
 * limite da conversa → teto global → reserva a vaga do dia → IA → salva a troca.
 * Qualquer falha depois da reserva devolve a vaga: o aluno não recebeu resposta.
 * Sem @Transactional de propósito (como na transcrição): a chamada à IA não segura conexão com o banco.
 */
@Service
public class EnviarMensagemJuliaService {
    static final String MENSAGEM_INDISPONIVEL = "Júlia não conseguiu responder agora. Tente novamente em instantes.";
    private static final Logger log = LoggerFactory.getLogger(EnviarMensagemJuliaService.class);

    private final RedacaoRepositoryPort redacaoRepository;
    private final MontadorContextoJulia montadorContexto;
    private final ConversaJuliaRepositoryPort conversaRepository;
    private final UsoIADiarioRepositoryPort usoRepository;
    private final JuliaIAPort juliaIA;
    private final PoliticaJulia politica;
    private final Clock clock;

    public EnviarMensagemJuliaService(
            RedacaoRepositoryPort redacaoRepository,
            MontadorContextoJulia montadorContexto,
            ConversaJuliaRepositoryPort conversaRepository,
            UsoIADiarioRepositoryPort usoRepository,
            JuliaIAPort juliaIA,
            PoliticaJulia politica,
            Clock clock
    ) {
        this.redacaoRepository = redacaoRepository;
        this.montadorContexto = montadorContexto;
        this.conversaRepository = conversaRepository;
        this.usoRepository = usoRepository;
        this.juliaIA = juliaIA;
        this.politica = politica;
        this.clock = clock;
    }

    public TrocaJulia enviar(UUID usuarioId, UUID redacaoId, String texto) {
        Redacao redacao = redacaoRepository.buscarPorId(redacaoId)
                .filter(r -> r.usuarioId().equals(usuarioId))
                .orElseThrow(() -> new RedacaoNaoEncontradaException(redacaoId));
        // TODO plano Pro: quando existir o plano por usuário, lançar AcessoJuliaNegadoException para o plano grátis.
        ContextoJulia contexto = montadorContexto.montar(redacao);

        LimitesJulia limites = politica.limites();
        Instant agora = clock.instant();
        MensagemJulia pergunta = MensagemJulia.doUsuario(texto, limites.tamanhoMaxPergunta(), agora);
        ConversaJulia conversa = conversaAtiva(usuarioId, redacaoId, agora, limites);
        conversa.garantirQuePodeReceberPergunta(limites);

        LocalDate hoje = LocalDate.now(clock);
        reservarVagaDoDia(usuarioId, hoje);

        RespostaJulia resposta = perguntar(usuarioId, hoje, contexto, conversa, pergunta, limites);
        ConversaJulia salva = registrarESalvar(usuarioId, hoje, conversa, pergunta, resposta, limites);

        List<MensagemJulia> mensagens = salva.getMensagens();
        int restantesHoje = politica.restantesHoje(usoRepository.contarMensagensJulia(usuarioId, hoje));
        return new TrocaJulia(
                mensagens.get(mensagens.size() - 2),
                mensagens.get(mensagens.size() - 1),
                salva.perguntasRestantes(limites),
                restantesHoje
        );
    }

    /**
     * A conversa ativa da redação, ou uma nova. Uma conversa expirada (sem atividade além do limite) é encerrada
     * e salva ANTES de nascer a nova: o banco só aceita uma conversa ATIVA por aluno e redação.
     */
    private ConversaJulia conversaAtiva(UUID usuarioId, UUID redacaoId, Instant agora, LimitesJulia limites) {
        var existente = conversaRepository.buscarAtiva(usuarioId, redacaoId);
        if (existente.isPresent()) {
            ConversaJulia conversa = existente.get();
            if (!conversa.expirada(agora, limites.inatividadeMaxima())) {
                return conversa;
            }
            conversa.encerrar(agora);
            conversaRepository.salvar(conversa);
        }
        return ConversaJulia.iniciar(usuarioId, redacaoId, agora);
    }

    /** Teto do sistema antes da vaga do aluno (aproximado sob concorrência, como na correção e na transcrição). */
    private void reservarVagaDoDia(UUID usuarioId, LocalDate hoje) {
        if (usoRepository.somarMensagensJuliaDoDia(hoje) >= politica.tetoGlobal()) {
            throw new LimiteJuliaExcedidoException(TipoLimiteJulia.GLOBAL);
        }
        if (!usoRepository.reservarMensagemJulia(usuarioId, hoje, politica.limiteDiario())) {
            throw new LimiteJuliaExcedidoException(TipoLimiteJulia.DIARIO_USUARIO);
        }
    }

    private RespostaJulia perguntar(UUID usuarioId, LocalDate hoje, ContextoJulia contexto, ConversaJulia conversa,
                                    MensagemJulia pergunta, LimitesJulia limites) {
        try {
            return juliaIA.responder(contexto, conversa.historicoRecente(limites.historicoMaximo()), pergunta.conteudo());
        } catch (RuntimeException e) {
            log.warn("Júlia falhou ({}): {}", e.getClass().getSimpleName(), LogSeguro.limpar(e.getMessage()));
            usoRepository.liberarMensagemJulia(usuarioId, hoje);
            if (e instanceof JuliaIndisponivelException indisponivel) {
                throw indisponivel;
            }
            throw new JuliaIndisponivelException(MENSAGEM_INDISPONIVEL, e);
        }
    }

    private ConversaJulia registrarESalvar(UUID usuarioId, LocalDate hoje, ConversaJulia conversa,
                                           MensagemJulia pergunta, RespostaJulia resposta, LimitesJulia limites) {
        try {
            Instant respondidaEm = clock.instant();
            conversa.registrarTroca(pergunta, MensagemJulia.daJulia(resposta, respondidaEm), limites, respondidaEm);
            return conversaRepository.salvar(conversa);
        } catch (RuntimeException e) {
            log.warn("Não foi possível salvar a conversa da Júlia: {}", LogSeguro.limpar(e.getMessage()));
            usoRepository.liberarMensagemJulia(usuarioId, hoje);
            throw e;
        }
    }
}
