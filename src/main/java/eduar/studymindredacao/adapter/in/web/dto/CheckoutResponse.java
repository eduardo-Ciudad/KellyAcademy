package eduar.studymindredacao.adapter.in.web.dto;

/** Só a URL: o id da sessão não interessa ao front (ele volta no success_url). */
public record CheckoutResponse(String url) {
}
