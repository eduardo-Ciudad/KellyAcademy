package eduar.studymindredacao.domain.julia;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ConversaJulia {

    private final UUID id;
    private final UUID usuarioId;
    private final UUID redacaoId;
    private StatusConversa status;
    private final List<MensagemJulia> mensagens;
    private final Instant criadaEm;
    private Instant ultimaAtividadeEm;
    private Instant encerradaEm;

    private ConversaJulia(UUID id, UUID usuarioId, UUID redacaoId, StatusConversa status,
                          List<MensagemJulia> mensagens, Instant criadaEm,
                          Instant ultimaAtividadeEm, Instant encerradaEm) {
        this.id = id;
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        this.redacaoId = Objects.requireNonNull(redacaoId, "redacaoId é obrigatório");
        this.status = Objects.requireNonNull(status, "status é obrigatório");
        this.mensagens = new ArrayList<>(Objects.requireNonNull(mensagens, "mensagens é obrigatório"));
        this.criadaEm = Objects.requireNonNull(criadaEm, "criadaEm é obrigatório");
        this.ultimaAtividadeEm = Objects.requireNonNull(ultimaAtividadeEm, "ultimaAtividadeEm é obrigatório");
        this.encerradaEm = encerradaEm;
    }

    public static ConversaJulia iniciar(UUID usuarioId, UUID redacaoId, Instant agora) {
        return new ConversaJulia(null, usuarioId, redacaoId, StatusConversa.ATIVA,
                List.of(), agora, agora, null);
    }

    public static ConversaJulia reconstituir(UUID id, UUID usuarioId, UUID redacaoId,
                                             StatusConversa status, List<MensagemJulia> mensagens,
                                             Instant criadaEm, Instant ultimaAtividadeEm,
                                             Instant encerradaEm) {
        return new ConversaJulia(id, usuarioId, redacaoId, status, mensagens,
                criadaEm, ultimaAtividadeEm, encerradaEm);
    }

    public void garantirQuePodeReceberPergunta(LimitesJulia limites) {
        Objects.requireNonNull(limites, "limites é obrigatório");
        if (status != StatusConversa.ATIVA) {
            throw new IllegalStateException("A conversa já foi encerrada.");
        }
        if (perguntasFeitas() >= limites.limitePorConversa()) {
            throw new LimiteJuliaExcedidoException(TipoLimiteJulia.CONVERSA);
        }
    }

    public void registrarTroca(MensagemJulia pergunta, MensagemJulia resposta,
                               LimitesJulia limites, Instant agora) {
        Objects.requireNonNull(pergunta, "pergunta é obrigatória");
        Objects.requireNonNull(resposta, "resposta é obrigatória");
        Objects.requireNonNull(agora, "agora é obrigatório");
        if (!pergunta.ehDoUsuario()) {
            throw new IllegalArgumentException("a pergunta precisa ser do usuário");
        }
        if (resposta.ehDoUsuario()) {
            throw new IllegalArgumentException("a resposta precisa ser da Júlia");
        }
        garantirQuePodeReceberPergunta(limites);

        mensagens.add(pergunta);
        mensagens.add(resposta);
        ultimaAtividadeEm = agora;
    }

    public int perguntasRestantes(LimitesJulia limites) {
        return Math.max(0, limites.limitePorConversa() - perguntasFeitas());
    }

    public boolean expirada(Instant agora, Duration inatividadeMaxima) {
        return agora.isAfter(ultimaAtividadeEm.plus(inatividadeMaxima));
    }

    public List<MensagemJulia> historicoRecente(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        int inicio = Math.max(0, mensagens.size() - quantidade);
        return List.copyOf(mensagens.subList(inicio, mensagens.size()));
    }

    public void encerrar(Instant agora) {
        if (status == StatusConversa.ENCERRADA) {
            return;
        }
        status = StatusConversa.ENCERRADA;
        encerradaEm = Objects.requireNonNull(agora, "agora é obrigatório");
    }

    private int perguntasFeitas() {
        return (int) mensagens.stream().filter(MensagemJulia::ehDoUsuario).count();
    }

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public UUID getRedacaoId() { return redacaoId; }
    public StatusConversa getStatus() { return status; }
    public List<MensagemJulia> getMensagens() { return Collections.unmodifiableList(mensagens); }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getUltimaAtividadeEm() { return ultimaAtividadeEm; }
    public Instant getEncerradaEm() { return encerradaEm; }
}