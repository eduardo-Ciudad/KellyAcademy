package eduar.studymindredacao.adapter.out.pagamento;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tradução do vocabulário do Stripe para o domínio. Funções puras: nada de SDK nem rede,
 * por isso todos os casos estranhos (status desconhecido, ciclo que não vendemos) são testados aqui.
 */
final class TradutorStripe {
    static final String METADATA_USUARIO = "usuario_id";
    static final String PREFIXO_EVENTO_ASSINATURA = "customer.subscription.";
    static final String EVENTO_CHECKOUT_CONCLUIDO = "checkout.session.completed";

    private TradutorStripe() {
    }

    /**
     * "past_due" → PAST_DUE. Status que o domínio não conhece lança exceção de propósito: o webhook responde 500,
     * o Stripe tenta de novo, e o erro aparece no log em vez de virar um aluno com o status errado.
     */
    static StatusAssinatura status(String statusStripe) {
        if (statusStripe == null || statusStripe.isBlank()) {
            throw new IllegalArgumentException("status da assinatura ausente");
        }
        try {
            return StatusAssinatura.valueOf(statusStripe.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status de assinatura desconhecido: " + statusStripe, e);
        }
    }

    /** Só vendemos 1 mês e 1 ano. Qualquer outro preço (criado à mão no Dashboard, por engano) é recusado. */
    static CicloAssinatura ciclo(String intervalo, Long quantidadeIntervalo) {
        long quantidade = quantidadeIntervalo == null ? 1L : quantidadeIntervalo;
        if (quantidade == 1L && "month".equals(intervalo)) {
            return CicloAssinatura.MENSAL;
        }
        if (quantidade == 1L && "year".equals(intervalo)) {
            return CicloAssinatura.ANUAL;
        }
        throw new IllegalArgumentException("ciclo não vendido: " + quantidade + " " + intervalo);
    }

    /** Epoch em segundos (formato do Stripe) → instante em UTC. */
    static OffsetDateTime instante(Long epochSegundos) {
        if (epochSegundos == null) {
            throw new IllegalArgumentException("data ausente na resposta do Stripe");
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSegundos), ZoneOffset.UTC);
    }

    /** Id do aluno gravado no metadata. Ausente ou inválido = nulo (o dono é achado pelo cliente do Stripe). */
    static UUID usuarioDoMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        String valor = metadata.get(METADATA_USUARIO);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extrai do JSON cru do evento o id da assinatura afetada, ou nulo se o evento não mexe em assinatura.
     * Lê o JSON cru em vez do objeto tipado do SDK: assim funciona mesmo se o endpoint do webhook estiver numa
     * versão da API diferente da do SDK (nesse caso o SDK não consegue desserializar o objeto).
     */
    static String subscriptionDoEvento(String tipo, String objetoJson) {
        if (tipo == null || objetoJson == null || objetoJson.isBlank()) {
            return null;
        }
        boolean eventoDeAssinatura = tipo.startsWith(PREFIXO_EVENTO_ASSINATURA);
        boolean checkoutConcluido = EVENTO_CHECKOUT_CONCLUIDO.equals(tipo);
        if (!eventoDeAssinatura && !checkoutConcluido) {
            return null;
        }
        JsonObject objeto;
        try {
            objeto = JsonParser.parseString(objetoJson).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
        // customer.subscription.*: o próprio objeto é a assinatura. checkout.session.completed: campo "subscription"
        // (nulo quando a sessão é de pagamento avulso; pode vir expandido como objeto).
        JsonElement id = eventoDeAssinatura ? objeto.get("id") : objeto.get("subscription");
        if (id != null && id.isJsonObject()) {
            id = id.getAsJsonObject().get("id");
        }
        if (id == null || id.isJsonNull() || !id.isJsonPrimitive()) {
            return null;
        }
        String valor = id.getAsString();
        return valor.startsWith("sub_") ? valor : null;
    }
}