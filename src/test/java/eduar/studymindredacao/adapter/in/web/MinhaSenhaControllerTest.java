package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.AlterarSenhaService;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.SenhaAtualIncorretaException;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.TokenGerado;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /usuarios/me/senha com a SecurityConfig e o LimitadorDeTentativas reais. A sessão é aberta pelo
 * ValidarSessaoService mockado: cada teste usa um usuário próprio, porque o limitador é compartilhado.
 */
@WebMvcTest(MinhaSenhaController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, MinhaSenhaControllerTest.Relogio.class})
class MinhaSenhaControllerTest {
    private static final String CORPO = "{\"senhaAtual\":\"senhaAntiga123\",\"novaSenha\":\"senhaNova123\"}";

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
    private AlterarSenhaService alterarSenha;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    private UUID usuarioId;
    private String jwt;

    @BeforeEach
    void abrirSessao() {
        usuarioId = UUID.randomUUID();
        jwt = "jwt-" + usuarioId;
        when(validarSessao.validar(jwt))
                .thenReturn(Optional.of(new DadosToken(usuarioId, "ana@exemplo.com", Role.ALUNO, 0)));
    }

    private MockHttpServletRequestBuilder alterar(String corpo) {
        return put("/usuarios/me/senha")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    @Test
    void semLoginResponde401SemChamarOCasoDeUso() throws Exception {
        mvc.perform(put("/usuarios/me/senha").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());

        verify(alterarSenha, never()).alterar(any(), anyString(), anyString());
    }

    @Test
    void logadoTrocaEDevolveOTokenNovo() throws Exception {
        var expira = Instant.parse("2026-10-18T20:00:00Z");
        when(alterarSenha.alterar(usuarioId, "senhaAntiga123", "senhaNova123"))
                .thenReturn(new TokenGerado("jwt-novo", expira));

        mvc.perform(alterar(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-novo"))
                .andExpect(jsonPath("$.tipo").value("Bearer"));

        verify(alterarSenha).alterar(usuarioId, "senhaAntiga123", "senhaNova123");
    }

    @Test
    void senhaAtualErradaResponde400ComCodigoENao401() throws Exception {
        when(alterarSenha.alterar(usuarioId, "senhaAntiga123", "senhaNova123"))
                .thenThrow(new SenhaAtualIncorretaException());

        mvc.perform(alterar(CORPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SENHA_ATUAL_INCORRETA"))
                .andExpect(jsonPath("$.detail").value("Senha atual incorreta."));
    }

    @Test
    void novaSenhaFracaOuIgualResponde400ComAMensagem() throws Exception {
        when(alterarSenha.alterar(usuarioId, "senhaAntiga123", "senhaNova123"))
                .thenThrow(new IllegalArgumentException("a nova senha deve ser diferente da atual"));

        mvc.perform(alterar(CORPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("a nova senha deve ser diferente da atual"));
    }

    @Test
    void corpoSemSenhaAtualOuSemNovaResponde400SemChamarOCasoDeUso() throws Exception {
        mvc.perform(alterar("{\"novaSenha\":\"senhaNova123\"}")).andExpect(status().isBadRequest());
        mvc.perform(alterar("{\"senhaAtual\":\"senhaAntiga123\"}")).andExpect(status().isBadRequest());

        verify(alterarSenha, never()).alterar(any(), anyString(), anyString());
    }

    @Test
    void temLimiteDeTentativasPorConta() throws Exception {
        when(alterarSenha.alterar(any(), anyString(), anyString())).thenThrow(new SenhaAtualIncorretaException());
        for (int i = 0; i < MinhaSenhaController.TENTATIVAS_POR_USUARIO; i++) {
            mvc.perform(alterar(CORPO)).andExpect(status().isBadRequest());
        }

        mvc.perform(alterar(CORPO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }
}
