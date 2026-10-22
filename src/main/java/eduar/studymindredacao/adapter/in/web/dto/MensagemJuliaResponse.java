package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.PapelMensagem;

import java.time.Instant;
import java.util.UUID;

/** Sem os tokens: o custo de cada resposta fica só no backend. */
public record MensagemJuliaResponse(UUID id, PapelMensagem papel, String conteudo, Instant criadaEm) {
    public static MensagemJuliaResponse de(MensagemJulia mensagem) {
        return new MensagemJuliaResponse(mensagem.id(), mensagem.papel(), mensagem.conteudo(), mensagem.criadaEm());
    }
}
