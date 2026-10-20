package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.IniciarCheckoutService;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.AssinaturaExistenteException;
import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.model.SessaoCheckout;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /assinaturas/checkout com a SecurityConfig e o LimitadorDeTentativas reais. Cada teste usa um usuário
 * próprio, porque o limitador é compartilhado.
 */
@WebMvcTest(AssinaturaController.class)
@Import({SecurityConfig.class, LimitadorDeTentativas.class, AssinaturaControllerTest.Relogio.class})
class AssinaturaControllerTest {
    private static final String URL = "https://checkout.stripe.com/c/pay/cs_test_1";

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
    private IniciarCheckoutService iniciarCheckout;
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

    private MockHttpServletRequestBuilder checkout(String corpo) {
        return post("/assinaturas/checkout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
    }

    @Test
    void semLoginResponde401SemChamarOStripe() throws Exception {
        mvc.perform(post("/assinaturas/checkout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ciclo\":\"MENSAL\"}"))
                .andExpect(status().isUnauthorized());

        verify(iniciarCheckout, never()).iniciar(any(), any());
    }

    @Test
    void devolveAUrlDoCheckoutDoUsuarioLogado() throws Exception {
        when(iniciarCheckout.iniciar(usuarioId, CicloAssinatura.ANUAL))
                .thenReturn(new SessaoCheckout("cs_test_1", URL));

        mvc.perform(checkout("{\"ciclo\":\"ANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(URL));
    }

    @Test
    void cicloInvalidoResponde400() throws Exception {
        mvc.perform(checkout("{\"ciclo\":\"VITALICIO\"}"))
                .andExpect(status().isBadRequest());

        verify(iniciarCheckout, never()).iniciar(any(), any());
    }

    @Test
    void semCicloResponde400() throws Exception {
        mvc.perform(checkout("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jaAssinanteResponde409ComCodigo() throws Exception {
        when(iniciarCheckout.iniciar(usuarioId, CicloAssinatura.MENSAL))
                .thenThrow(new AssinaturaExistenteException());

        mvc.perform(checkout("{\"ciclo\":\"MENSAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ASSINATURA_EXISTENTE"));
    }

    @Test
    void stripeForaDoArResponde503SemVazarODetalhe() throws Exception {
        when(iniciarCheckout.iniciar(usuarioId, CicloAssinatura.MENSAL))
                .thenThrow(new PagamentoIndisponivelException("Preço kelly_pro_mensal não encontrado", null));

        mvc.perform(checkout("{\"ciclo\":\"MENSAL\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("PAGAMENTO_INDISPONIVEL"))
                .andExpect(jsonPath("$.detail").value(not(containsString("kelly_pro_mensal"))));
    }

    @Test
    void limitaTentativasPorUsuario() throws Exception {
        when(iniciarCheckout.iniciar(usuarioId, CicloAssinatura.MENSAL))
                .thenReturn(new SessaoCheckout("cs_test_1", URL));

        for (int i = 0; i < AssinaturaController.TENTATIVAS_POR_USUARIO; i++) {
            mvc.perform(checkout("{\"ciclo\":\"MENSAL\"}")).andExpect(status().isOk());
        }
        mvc.perform(checkout("{\"ciclo\":\"MENSAL\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
