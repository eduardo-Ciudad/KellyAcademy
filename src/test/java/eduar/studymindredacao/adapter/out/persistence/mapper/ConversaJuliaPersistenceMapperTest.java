package eduar.studymindredacao.adapter.out.persistence.mapper;

import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.LimitesJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.PapelMensagem;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.julia.StatusConversa;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversaJuliaPersistenceMapperTest {

    private static final Instant AGORA = Instant.parse("2026-10-21T20:00:00Z");
    private static final LimitesJulia LIMITES = new LimitesJulia(10, 500, 6, Duration.ofHours(24));

    @Test
    void toEntityDeveNumerarMensagensPelaPosicaoELigarAConversa() {
        var conversa = conversaComDuasTrocas();

        var entity = ConversaJuliaPersistenceMapper.toEntity(conversa);

        assertThat(entity.getMensagens()).extracting(m -> m.getOrdem().intValue()).containsExactly(0, 1, 2, 3);
        assertThat(entity.getMensagens()).allMatch(m -> m.getConversa() == entity);
        assertThat(entity.getMensagens().get(0).getPapel()).isEqualTo(PapelMensagem.USUARIO);
        assertThat(entity.getCriadaEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(entity.getEncerradaEm()).isNull();
    }

    @Test
    void idaEVoltaDevePreservarConversaEMensagens() {
        var conversa = conversaComDuasTrocas();
        conversa.encerrar(AGORA.plusSeconds(60));

        var volta = ConversaJuliaPersistenceMapper.toDomain(ConversaJuliaPersistenceMapper.toEntity(conversa));

        assertThat(volta.getUsuarioId()).isEqualTo(conversa.getUsuarioId());
        assertThat(volta.getRedacaoId()).isEqualTo(conversa.getRedacaoId());
        assertThat(volta.getStatus()).isEqualTo(StatusConversa.ENCERRADA);
        assertThat(volta.getEncerradaEm()).isEqualTo(AGORA.plusSeconds(60));
        assertThat(volta.getUltimaAtividadeEm()).isEqualTo(conversa.getUltimaAtividadeEm());
        assertThat(volta.getMensagens()).isEqualTo(conversa.getMensagens());
    }

    @Test
    void toDomainDeveOrdenarPelaOrdemMesmoComListaForaDeOrdem() {
        var entity = ConversaJuliaPersistenceMapper.toEntity(conversaComDuasTrocas());
        java.util.Collections.reverse(entity.getMensagens());

        var conversa = ConversaJuliaPersistenceMapper.toDomain(entity);

        assertThat(conversa.getMensagens()).extracting(MensagemJulia::conteudo)
                .containsExactly("pergunta 1", "resposta 1", "pergunta 2", "resposta 2");
    }

    private static ConversaJulia conversaComDuasTrocas() {
        var conversa = ConversaJulia.iniciar(UUID.randomUUID(), UUID.randomUUID(), AGORA);
        for (int i = 1; i <= 2; i++) {
            conversa.registrarTroca(
                    MensagemJulia.doUsuario("pergunta " + i, 500, AGORA),
                    MensagemJulia.daJulia(new RespostaJulia("resposta " + i, 900, 120), AGORA),
                    LIMITES, AGORA.plusSeconds(i));
        }
        return conversa;
    }
}