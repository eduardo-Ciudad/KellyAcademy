package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.application.usecase.ProcessarWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook do Stripe. Rota pública (o Stripe não manda JWT): quem protege é a verificação do header
 * Stripe-Signature com o STRIPE_WEBHOOK_SECRET. O corpo chega como String crua de propósito: o HMAC é calculado
 * sobre os bytes exatos enviados, e desserializar e serializar de novo quebraria a assinatura.
 */
@RestController
@RequestMapping("/webhooks/stripe")
public class WebhookStripeController {
    static final String HEADER_ASSINATURA = "Stripe-Signature";

    private final ProcessarWebhookService processarWebhook;

    public WebhookStripeController(ProcessarWebhookService processarWebhook) {
        this.processarWebhook = processarWebhook;
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestBody(required = false) String corpo,
            @RequestHeader(value = HEADER_ASSINATURA, required = false) String assinatura
    ) {
        processarWebhook.processar(corpo, assinatura);
        return ResponseEntity.ok().build();
    }
}
