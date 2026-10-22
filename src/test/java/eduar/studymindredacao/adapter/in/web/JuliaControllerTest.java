package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.ConsultarConversaJuliaService;
import eduar.studymindredacao.application.usecase.ConversaJuliaDetalhada;
import eduar.studymindredacao.application.usecase.EncerrarConversasJuliaService;
import eduar.studymindredacao.application.usecase.EnviarMensagemJuliaService;
import eduar.studymindredacao.application.usecase.TrocaJulia;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.julia.LimiteJuliaExcedidoException;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.PapelMensagem;
import eduar.studymindredacao.domain.julia.PerguntaInvalidaException;
import eduar.studymindredacao.domain.julia.RedacaoSemCorrecaoException;
import eduar.studymindredacao.domain.julia.TipoLimiteJulia;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints da Júlia com a SecurityConfig real e os services mockados. */
@WebMvcTest(JuliaController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, JuliaControllerTest.Relogio.class})
class JuliaControllerTest {
    private static final Instant AGORA = Instant.parse("2026-10-22T18:00:00Z");

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarConversaJuliaService consultarConversa;
    @MockitoBean
    private EnviarMensagemJuliaService enviarMensagem;
    @MockitoBean
    private EncerrarConversasJuliaService encerrarConversas;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    private UUID usuarioId;
    private UUID redacaoId;
    private String jwt;

    @BeforeEach
    void abrirSessao() {
        usuarioId = UUID.randomUUID();
        redacaoId = UUID.randomUUID();
        jwt = "jwt-" + usuarioId;
        when(validarSessao.validar(jwt))
                .thenReturn(Optional.of(new DadosToken(usuarioId, "ana@exemplo.com", Role.ALUNO, 0)));
    }

    // ---------- GET /redacoes/{id}/julia ----------

    @Test
    void consultarSemLoginResponde401() throws Exception {
        mvc.perform(get("/redacoes/{id}/julia", redacaoId)).andExpect(status().isUnauthorized());
    }

    @Test
    void consultarDevolveAsMensagensSemExporOsTokens() throws Exception {
        var pergunta = mensagem(PapelMensagem.USUARIO, "Por que fiquei com 80 na C3?", null, null);
        var resposta = mensagem(PapelMensagem.JULIA, "Na C3 você perdeu pontos porque...", 900, 120);
        when(consultarConversa.consultar(usuarioId, redacaoId))
                .thenReturn(new ConversaJuliaDetalhada(List.of(pergunta, resposta), 9, 19));

        mvc.perform(autenticado(get("/redacoes/{id}/julia", redacaoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagens.length()").value(2))
                .andExpect(jsonPath("$.mensagens[0].id").value(pergunta.id().toString()))
                .andExpect(jsonPath("$.mensagens[0].papel").value("USUARIO"))
                .andExpect(jsonPath("$.mensagens[1].papel").value("JULIA"))
                .andExpect(jsonPath("$.mensagens[1].conteudo").value("Na C3 você perdeu pontos porque..."))
                .andExpect(jsonPath("$.mensagens[1].criadaEm").exists())
                .andExpect(jsonPath("$.mensagens[1].tokensEntrada").doesNotExist())
                .andExpect(jsonPath("$.mensagens[1].tokensSaida").doesNotExist())
                .andExpect(jsonPath("$.restantesConversa").value(9))
                .andExpect(jsonPath("$.restantesHoje").value(19));
    }

    @Test
    void consultarRedacaoDeOutroAlunoResponde404() throws Exception {
        when(consultarConversa.consultar(usuarioId, redacaoId)).thenThrow(new RedacaoNaoEncontradaException(redacaoId));

        mvc.perform(autenticado(get("/redacoes/{id}/julia", redacaoId))).andExpect(status().isNotFound());
    }

    @Test
    void idMalformadoResponde400() throws Exception {
        mvc.perform(autenticado(get("/redacoes/abc/julia"))).andExpect(status().isBadRequest());
    }

    // ---------- POST /redacoes/{id}/julia/mensagens ----------

    @Test
    void enviarSemLoginResponde401SemChamarAIA() throws Exception {
        mvc.perform(post("/redacoes/{id}/julia/mensagens", redacaoId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isUnauthorized());

        verify(enviarMensagem, never()).enviar(any(), any(), anyString());
    }

    @Test
    void enviarDevolveAPerguntaEARespostaSalvas() throws Exception {
        var pergunta = mensagem(PapelMensagem.USUARIO, "Por que fiquei com 80 na C3?", null, null);
        var resposta = mensagem(PapelMensagem.JULIA, "Na C3 você perdeu pontos porque...", 900, 120);
        when(enviarMensagem.enviar(usuarioId, redacaoId, "Por que fiquei com 80 na C3?"))
                .thenReturn(new TrocaJulia(pergunta, resposta, 9, 19));

        mvc.perform(enviar("{\"pergunta\":\"Por que fiquei com 80 na C3?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pergunta.id").value(pergunta.id().toString()))
                .andExpect(jsonPath("$.pergunta.papel").value("USUARIO"))
                .andExpect(jsonPath("$.resposta.papel").value("JULIA"))
                .andExpect(jsonPath("$.resposta.conteudo").value("Na C3 você perdeu pontos porque..."))
                .andExpect(jsonPath("$.resposta.tokensEntrada").doesNotExist())
                .andExpect(jsonPath("$.restantesConversa").value(9))
                .andExpect(jsonPath("$.restantesHoje").value(19));
    }

    @Test
    void perguntaEmBrancoResponde400SemChamarAIA() throws Exception {
        mvc.perform(enviar("{\"pergunta\":\"   \"}")).andExpect(status().isBadRequest());

        verify(enviarMensagem, never()).enviar(any(), any(), anyString());
    }

    @Test
    void perguntaAcimaDe500CaracteresResponde400SemChamarAIA() throws Exception {
        mvc.perform(enviar("{\"pergunta\":\"" + "a".repeat(501) + "\"}")).andExpect(status().isBadRequest());

        verify(enviarMensagem, never()).enviar(any(), any(), anyString());
    }

    @Test
    void semPerguntaResponde400() throws Exception {
        mvc.perform(enviar("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void perguntaRecusadaPeloDominioResponde400() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString()))
                .thenThrow(new PerguntaInvalidaException("A pergunta pode ter no máximo 500 caracteres."));

        mvc.perform(enviar("{\"pergunta\":\"oi\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void redacaoSemCorrecaoResponde409ComCodigo() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString())).thenThrow(new RedacaoSemCorrecaoException());

        mvc.perform(enviar("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("JULIA_SEM_CORRECAO"));
    }

    @Test
    void limiteDaConversaResponde429ComCodigo() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString()))
                .thenThrow(new LimiteJuliaExcedidoException(TipoLimiteJulia.CONVERSA));

        mvc.perform(enviar("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("JULIA_LIMITE_CONVERSA"))
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void limiteDiarioResponde429ComCodigoERetryAfter() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString()))
                .thenThrow(new LimiteJuliaExcedidoException(TipoLimiteJulia.DIARIO_USUARIO));

        mvc.perform(enviar("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("JULIA_LIMITE_DIARIO"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void tetoGlobalResponde503ComCodigo() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString()))
                .thenThrow(new LimiteJuliaExcedidoException(TipoLimiteJulia.GLOBAL));

        mvc.perform(enviar("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("JULIA_LIMITE_GLOBAL"));
    }

    @Test
    void juliaIndisponivelResponde503ComCodigo() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString()))
                .thenThrow(new JuliaIndisponivelException("Gemini fora do ar"));

        mvc.perform(enviar("{\"pergunta\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("JULIA_INDISPONIVEL"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.MENSAGEM_JULIA_INDISPONIVEL));
    }

    @Test
    void enviarParaRedacaoDeOutroAlunoResponde404() throws Exception {
        when(enviarMensagem.enviar(any(), any(), anyString())).thenThrow(new RedacaoNaoEncontradaException(redacaoId));

        mvc.perform(enviar("{\"pergunta\":\"oi\"}")).andExpect(status().isNotFound());
    }

    // ---------- DELETE /julia/conversas ----------

    @Test
    void encerrarResponde204ParaOAlunoLogado() throws Exception {
        mvc.perform(autenticado(delete("/julia/conversas"))).andExpect(status().isNoContent());

        verify(encerrarConversas).encerrar(usuarioId);
    }

    @Test
    void encerrarSemLoginResponde401() throws Exception {
        mvc.perform(delete("/julia/conversas")).andExpect(status().isUnauthorized());

        verify(encerrarConversas, never()).encerrar(any());
    }

    // ---------- apoio ----------

    private MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder requisicao) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    }

    private MockHttpServletRequestBuilder enviar(String corpo) {
        return autenticado(post("/redacoes/{id}/julia/mensagens", redacaoId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    private static MensagemJulia mensagem(PapelMensagem papel, String conteudo, Integer tokensEntrada,
                                          Integer tokensSaida) {
        return new MensagemJulia(UUID.randomUUID(), papel, conteudo, tokensEntrada, tokensSaida, AGORA);
    }
}
