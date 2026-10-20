package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.CheckoutRequest;
import eduar.studymindredacao.adapter.in.web.dto.CheckoutResponse;
import eduar.studymindredacao.adapter.in.web.security.LimitadorDeTentativas;
import eduar.studymindredacao.adapter.in.web.security.UsuarioAutenticado;
import eduar.studymindredacao.application.usecase.IniciarCheckoutService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/assinaturas")
public class AssinaturaController {
    // Cada checkout é uma chamada de rede ao Stripe: freia script que fica clicando
    static final int TENTATIVAS_POR_USUARIO = 10;
    static final Duration JANELA_TENTATIVAS = Duration.ofMinutes(15);

    private final IniciarCheckoutService iniciarCheckout;
    private final LimitadorDeTentativas limitador;

    public AssinaturaController(IniciarCheckoutService iniciarCheckout, LimitadorDeTentativas limitador) {
        this.iniciarCheckout = iniciarCheckout;
        this.limitador = limitador;
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestBody @Valid CheckoutRequest request
    ) {
        limitador.consumir("checkout:" + usuario.id(), TENTATIVAS_POR_USUARIO, JANELA_TENTATIVAS);
        var sessao = iniciarCheckout.iniciar(usuario.id(), request.ciclo());
        return new CheckoutResponse(sessao.url());
    }
}
