package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.security.SecurityConfig;
import eduar.studymindredacao.application.usecase.ProcessarWebhookService;
import eduar.studymindredacao.application.usecase.ResultadoWebhook;
import eduar.studymindredacao.application.usecase.ValidarSessaoService;
import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.exception.WebhookInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /webhooks/stripe com a SecurityConfig real: rota pública, protegida pela assinatura do Stripe. */
@WebMvcTest(WebhookStripeController.class)
@Import({SecurityConfig.class, WebhookStripeControllerTest.Relogio.class})
class WebhookStripeControllerTest {
    // Espaços e quebra de linha de propósito: o HMAC depende dos bytes exatos
    private static final String CORPO = "{\n  \"id\": \"evt_1\",\n  \"object\": \"event\"\n}";
    private static final String ASSINATURA = "t=1792540800,v1=abc123";

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
    private ProcessarWebhookService processarWebhook;
    @MockitoBean
    private ValidarSessaoService validarSessao;

    @Test
    void recebeSemJwtERepassaOCorpoExatoEOHeader() throws Exception {
        when(processarWebhook.processar(CORPO, ASSINATURA)).thenReturn(ResultadoWebhook.PROCESSADO);

        mvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", ASSINATURA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isOk());

        verify(processarWebhook).processar(CORPO, ASSINATURA);
    }

    @Test
    void eventoIgnoradoTambemResponde200() throws Exception {
        when(processarWebhook.processar(CORPO, ASSINATURA)).thenReturn(ResultadoWebhook.IGNORADO);

        mvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", ASSINATURA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isOk());
    }

    @Test
    void assinaturaInvalidaResponde400() throws Exception {
        when(processarWebhook.processar(CORPO, ASSINATURA))
                .thenThrow(new WebhookInvalidoException("Assinatura do webhook inválida", null));

        mvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", ASSINATURA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semOHeaderChegaNuloEResponde400() throws Exception {
        when(processarWebhook.processar(anyString(), isNull()))
                .thenThrow(new WebhookInvalidoException("Webhook sem corpo ou sem assinatura", null));

        mvc.perform(post("/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isBadRequest());

        verify(processarWebhook).processar(CORPO, null);
    }

    @Test
    void stripeForaDoArResponde503ParaOStripeRepetir() throws Exception {
        when(processarWebhook.processar(CORPO, ASSINATURA))
                .thenThrow(new PagamentoIndisponivelException("timeout", null));

        mvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", ASSINATURA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isServiceUnavailable());
    }
}
