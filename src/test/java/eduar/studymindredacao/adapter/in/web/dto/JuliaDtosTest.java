package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.ConversaJuliaDetalhada;
import eduar.studymindredacao.application.usecase.TrocaJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.PapelMensagem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JuliaDtosTest {
    private static final Instant AGORA = Instant.parse("2026-10-22T18:00:00Z");
    private final MensagemJulia pergunta = new MensagemJulia(UUID.randomUUID(), PapelMensagem.USUARIO, "oi", null, null, AGORA);
    private final MensagemJulia resposta = new MensagemJulia(UUID.randomUUID(), PapelMensagem.JULIA, "olá", 900, 120, AGORA);

    @Test
    void mensagemCopiaIdPapelConteudoEData() {
        var dto = MensagemJuliaResponse.de(resposta);

        assertThat(dto.id()).isEqualTo(resposta.id());
        assertThat(dto.papel()).isEqualTo(PapelMensagem.JULIA);
        assertThat(dto.conteudo()).isEqualTo("olá");
        assertThat(dto.criadaEm()).isEqualTo(AGORA);
    }

    @Test
    void mensagemNaoTemCamposDeTokens() {
        List<String> campos = Arrays.stream(MensagemJuliaResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(campos).containsExactly("id", "papel", "conteudo", "criadaEm");
    }

    @Test
    void conversaMantemAOrdemDasMensagensEOsRestantes() {
        var dto = ConversaJuliaResponse.de(new ConversaJuliaDetalhada(List.of(pergunta, resposta), 9, 19));

        assertThat(dto.mensagens()).extracting(MensagemJuliaResponse::id).containsExactly(pergunta.id(), resposta.id());
        assertThat(dto.restantesConversa()).isEqualTo(9);
        assertThat(dto.restantesHoje()).isEqualTo(19);
    }

    @Test
    void trocaLevaPerguntaRespostaERestantes() {
        var dto = TrocaJuliaResponse.de(new TrocaJulia(pergunta, resposta, 8, 18));

        assertThat(dto.pergunta().id()).isEqualTo(pergunta.id());
        assertThat(dto.resposta().id()).isEqualTo(resposta.id());
        assertThat(dto.restantesConversa()).isEqualTo(8);
        assertThat(dto.restantesHoje()).isEqualTo(18);
    }
}
