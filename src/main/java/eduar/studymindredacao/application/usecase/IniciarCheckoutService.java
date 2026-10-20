package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.AssinaturaExistenteException;
import eduar.studymindredacao.domain.model.SessaoCheckout;
import eduar.studymindredacao.domain.model.SolicitacaoCheckout;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Abre o checkout do Pro. Sem @Transactional de propósito: as chamadas ao Stripe são HTTP e não devem segurar
 * uma conexão do banco. As corridas (dois cliques) são resolvidas pelo vincular "só se vazio" e pela chave
 * de idempotência na criação do cliente.
 */
@Service
public class IniciarCheckoutService {
    static final String MENSAGEM_CONTA_INEXISTENTE = "conta não encontrada";

    private final UsuarioRepositoryPort usuarios;
    private final AssinaturaRepositoryPort assinaturas;
    private final PagamentoGatewayPort gateway;

    public IniciarCheckoutService(
            UsuarioRepositoryPort usuarios,
            AssinaturaRepositoryPort assinaturas,
            PagamentoGatewayPort gateway
    ) {
        this.usuarios = usuarios;
        this.assinaturas = assinaturas;
        this.gateway = gateway;
    }

    public SessaoCheckout iniciar(UUID usuarioId, CicloAssinatura ciclo) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(ciclo, "ciclo é obrigatório");

        boolean temAssinaturaViva = assinaturas.listarPorUsuario(usuarioId).stream()
                .anyMatch(a -> !a.status().encerrada());
        if (temAssinaturaViva) {
            throw new AssinaturaExistenteException();
        }

        var usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException(MENSAGEM_CONTA_INEXISTENTE));
        String cliente = assinaturas.buscarStripeCustomerId(usuarioId)
                .orElseGet(() -> criarCliente(usuario));

        return gateway.criarSessaoCheckout(new SolicitacaoCheckout(usuarioId, cliente, ciclo));
    }

    /**
     * Cria o cliente no Stripe e grava no aluno. Se outra requisição gravou primeiro (duplo clique), usa a dela:
     * com a chave de idempotência, o Stripe devolve o mesmo cus_ para as duas, então é o mesmo cliente.
     */
    private String criarCliente(Usuario usuario) {
        String novo = gateway.criarCliente(usuario.id(), usuario.nome(), usuario.email());
        if (assinaturas.vincularStripeCustomerId(usuario.id(), novo)) {
            return novo;
        }
        return assinaturas.buscarStripeCustomerId(usuario.id())
                .orElseThrow(() -> new IllegalStateException("cliente do Stripe sumiu após o vínculo"));
    }
}
