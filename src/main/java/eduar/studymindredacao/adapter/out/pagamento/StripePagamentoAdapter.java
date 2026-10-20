package eduar.studymindredacao.adapter.out.pagamento;

import com.google.gson.JsonSyntaxException;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.checkout.SessionCreateParams;
import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.exception.WebhookInvalidoException;
import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.model.SessaoCheckout;
import eduar.studymindredacao.domain.model.SolicitacaoCheckout;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StripePagamentoAdapter implements PagamentoGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(StripePagamentoAdapter.class);
    static final String NOME_EXIBIDO = "Kelly Academy";

    private final StripeClient client;
    private final ConfiguracaoStripe config;
    private final Map<CicloAssinatura, String> precos = new ConcurrentHashMap<>();

    public StripePagamentoAdapter(StripeClient client, ConfiguracaoStripe config) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String criarCliente(UUID usuarioId, String nome, String email) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        var params = CustomerCreateParams.builder()
                .setName(nome)
                .setEmail(email)
                .putMetadata(TradutorStripe.METADATA_USUARIO, usuarioId.toString())
                .build();

        var opcoes = RequestOptions.builder().setIdempotencyKey("kelly-cliente-" + usuarioId).build();
        try {
            return client.v1().customers().create(params, opcoes).getId();
        } catch (StripeException e) {
            log.warn("Stripe recusou criar o cliente do usuário {}: {}", usuarioId, e.getCode());
            throw new PagamentoIndisponivelException("Não foi possível iniciar o pagamento agora", e);
        }
    }

    @Override
    public SessaoCheckout criarSessaoCheckout(SolicitacaoCheckout solicitacao) {
        Objects.requireNonNull(solicitacao, "solicitacao");
        var params = montarParametrosSessao(solicitacao, precoDoCiclo(solicitacao.ciclo()), config);
        try {
            var sessao = client.v1().checkout().sessions().create(params);
            return new SessaoCheckout(sessao.getId(), sessao.getUrl());
        } catch (StripeException e) {
            log.warn("Stripe recusou criar a sessão de checkout do usuário {}: {}",
                    solicitacao.usuarioId(), e.getCode());
            throw new PagamentoIndisponivelException("Não foi possível iniciar o pagamento agora", e);
        }
    }

    @Override
    public EventoWebhook validarEvento(String corpo, String assinatura) {
        if (corpo == null || corpo.isBlank() || assinatura == null || assinatura.isBlank()) {
            throw new WebhookInvalidoException("Webhook sem corpo ou sem assinatura", null);
        }
        Event evento;
        try {
            evento = client.constructEvent(corpo, assinatura, config.webhookSecret());
        } catch (SignatureVerificationException | JsonSyntaxException e) {
            throw new WebhookInvalidoException("Assinatura do webhook inválida", e);
        }
        String objetoJson = evento.getDataObjectDeserializer().getRawJson();
        String subscription = TradutorStripe.subscriptionDoEvento(evento.getType(), objetoJson);
        return new EventoWebhook(evento.getId(), evento.getType(), subscription);
    }

    @Override
    public Optional<EstadoAssinaturaStripe> buscarAssinatura(String stripeSubscriptionId) {
        Objects.requireNonNull(stripeSubscriptionId, "stripeSubscriptionId");
        Subscription assinatura;
        try {
            assinatura = client.v1().subscriptions().retrieve(stripeSubscriptionId);
        } catch (InvalidRequestException e) {
            if ("resource_missing".equals(e.getCode())) {
                return Optional.empty();
            }
            throw new PagamentoIndisponivelException("Falha ao consultar a assinatura no Stripe", e);
        } catch (StripeException e) {
            throw new PagamentoIndisponivelException("Falha ao consultar a assinatura no Stripe", e);
        }
        return Optional.of(traduzir(assinatura));
    }


    static SessionCreateParams montarParametrosSessao(
            SolicitacaoCheckout solicitacao,
            String precoId,
            ConfiguracaoStripe config
    ) {
        String usuario = solicitacao.usuarioId().toString();

        var marca = SessionCreateParams.BrandingSettings.builder()
                .setDisplayName(NOME_EXIBIDO)
                .setBackgroundColor("#0b0c12")
                .setButtonColor("#2f5bff")
                .setFontFamily(SessionCreateParams.BrandingSettings.FontFamily.INTER)
                .setBorderStyle(SessionCreateParams.BrandingSettings.BorderStyle.ROUNDED);
        if (config.logoFileId() != null) {
            marca.setLogo(SessionCreateParams.BrandingSettings.Logo.builder()
                    .setType(SessionCreateParams.BrandingSettings.Logo.Type.FILE)
                    .setFile(config.logoFileId())
                    .build());
        }

        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(solicitacao.stripeCustomerId())
                .setClientReferenceId(usuario)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(precoId)
                        .setQuantity(1L)
                        .build())
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata(TradutorStripe.METADATA_USUARIO, usuario)
                        .build())
                .setSuccessUrl(config.frontendUrl() + "/assinatura/sucesso?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(config.frontendUrl() + "/planos")
                .setLocale(SessionCreateParams.Locale.PT_BR)
                .setBrandingSettings(marca.build())
                // Identificadores do Checkout Studio (fixed_by_ui), enviados crus: não dependem de setter no SDK
                .putExtraParam("integration_identifier", "hosted_web_0001")
                .putExtraParam("origin_context", "web")
                .build();
    }

    private String precoDoCiclo(CicloAssinatura ciclo) {
        return precos.computeIfAbsent(ciclo, c -> {
            String lookupKey = c == CicloAssinatura.MENSAL ? config.lookupKeyMensal() : config.lookupKeyAnual();
            var params = PriceListParams.builder().addLookupKey(lookupKey).setActive(true).build();
            try {
                return client.v1().prices().list(params).getData().stream()
                        .findFirst()
                        .map(Price::getId)
                        .orElseThrow(() -> new PagamentoIndisponivelException(
                                "Preço " + lookupKey + " não encontrado no Stripe", null));
            } catch (StripeException e) {
                throw new PagamentoIndisponivelException("Falha ao consultar o preço no Stripe", e);
            }
        });
    }

    private static EstadoAssinaturaStripe traduzir(Subscription assinatura) {
        var itens = assinatura.getItems() == null ? null : assinatura.getItems().getData();
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("assinatura " + assinatura.getId() + " sem itens");
        }
        // Vendemos um item por assinatura. Desde a API basil o fim do período fica no item, não na assinatura.
        var item = itens.get(0);
        var recorrencia = item.getPrice().getRecurring();
        boolean cancelando = Boolean.TRUE.equals(assinatura.getCancelAtPeriodEnd()) || assinatura.getCancelAt() != null;
        return new EstadoAssinaturaStripe(
                assinatura.getId(),
                assinatura.getCustomer(),
                TradutorStripe.usuarioDoMetadata(assinatura.getMetadata()),
                TradutorStripe.ciclo(recorrencia.getInterval(), recorrencia.getIntervalCount()),
                TradutorStripe.status(assinatura.getStatus()),
                TradutorStripe.instante(item.getCurrentPeriodEnd()),
                cancelando
        );
    }
}