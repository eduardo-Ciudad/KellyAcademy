package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.StatusConversa;
import eduar.studymindredacao.domain.port.ConversaJuliaRepositoryPort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Imita o adapter real: gera os UUIDs no salvar, devolve cópias (como objetos lidos do banco)
 * e recusa uma segunda conversa ATIVA para o mesmo aluno e redação (índice único parcial da V23).
 */
class ConversaJuliaRepositoryEmMemoria implements ConversaJuliaRepositoryPort {
    private final Map<UUID, ConversaJulia> conversas = new LinkedHashMap<>();
    private RuntimeException falhaAoSalvar;

    void falharAoSalvar(RuntimeException falha) {
        this.falhaAoSalvar = falha;
    }

    List<ConversaJulia> todas() {
        return conversas.values().stream().map(ConversaJuliaRepositoryEmMemoria::copia).toList();
    }

    @Override
    public ConversaJulia salvar(ConversaJulia conversa) {
        if (falhaAoSalvar != null) {
            throw falhaAoSalvar;
        }
        UUID id = conversa.getId() != null ? conversa.getId() : UUID.randomUUID();
        boolean outraAtiva = conversa.getStatus() == StatusConversa.ATIVA && conversas.values().stream()
                .anyMatch(c -> !c.getId().equals(id)
                        && c.getStatus() == StatusConversa.ATIVA
                        && c.getUsuarioId().equals(conversa.getUsuarioId())
                        && c.getRedacaoId().equals(conversa.getRedacaoId()));
        if (outraAtiva) {
            throw new IllegalStateException("violaria uk_conversas_julia_ativa");
        }
        List<MensagemJulia> mensagens = conversa.getMensagens().stream()
                .map(m -> m.id() != null ? m
                        : new MensagemJulia(UUID.randomUUID(), m.papel(), m.conteudo(), m.tokensEntrada(),
                        m.tokensSaida(), m.criadaEm()))
                .toList();
        var salva = ConversaJulia.reconstituir(id, conversa.getUsuarioId(), conversa.getRedacaoId(),
                conversa.getStatus(), mensagens, conversa.getCriadaEm(), conversa.getUltimaAtividadeEm(),
                conversa.getEncerradaEm());
        conversas.put(id, salva);
        return copia(salva);
    }

    @Override
    public Optional<ConversaJulia> buscarAtiva(UUID usuarioId, UUID redacaoId) {
        return conversas.values().stream()
                .filter(c -> c.getStatus() == StatusConversa.ATIVA)
                .filter(c -> c.getUsuarioId().equals(usuarioId) && c.getRedacaoId().equals(redacaoId))
                .findFirst()
                .map(ConversaJuliaRepositoryEmMemoria::copia);
    }

    @Override
    public int encerrarAtivasDoUsuario(UUID usuarioId, Instant agora) {
        int encerradas = 0;
        for (var entrada : conversas.entrySet()) {
            ConversaJulia conversa = entrada.getValue();
            if (conversa.getStatus() == StatusConversa.ATIVA && conversa.getUsuarioId().equals(usuarioId)) {
                ConversaJulia encerrada = copia(conversa);
                encerrada.encerrar(agora);
                entrada.setValue(encerrada);
                encerradas++;
            }
        }
        return encerradas;
    }

    private static ConversaJulia copia(ConversaJulia c) {
        return ConversaJulia.reconstituir(c.getId(), c.getUsuarioId(), c.getRedacaoId(), c.getStatus(),
                c.getMensagens(), c.getCriadaEm(), c.getUltimaAtividadeEm(), c.getEncerradaEm());
    }
}
