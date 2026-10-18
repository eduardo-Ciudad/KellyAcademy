package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Conta do aluno.
 * <ul>
 *   <li>{@code emailVerificadoEm} nulo = e-mail ainda não confirmado pelo link enviado no cadastro;</li>
 *   <li>{@code versaoSessao} sobe a cada troca de senha: vai dentro do JWT, e token com versão antiga deixa de valer
 *       (é assim que a troca de senha derruba as outras sessões);</li>
 *   <li>{@code senhaAlteradaEm} nulo = a senha nunca foi trocada desde o cadastro.</li>
 * </ul>
 */
public record Usuario(
        UUID id,
        String nome,
        String email,
        String senhaHash,
        Role role,
        OffsetDateTime criadoEm,
        OffsetDateTime emailVerificadoEm,
        int versaoSessao,
        OffsetDateTime senhaAlteradaEm
) {
    public Usuario {
        Validacoes.validarTextoObrigatorio(nome, "nome", 150);
        Validacoes.validarTextoObrigatorio(email, "email", 150);
        Validacoes.validarTextoObrigatorio(senhaHash, "senhaHash", 255);
        if (versaoSessao < 0) {
            throw new IllegalArgumentException("versaoSessao não pode ser negativa");
        }
        email = email.trim().toLowerCase();
        if (role == null) {
            role = Role.ALUNO;
        }
    }

    /** Conta que nunca trocou de senha (versão de sessão 0). É o caso do cadastro e dos testes. */
    public Usuario(
            UUID id,
            String nome,
            String email,
            String senhaHash,
            Role role,
            OffsetDateTime criadoEm,
            OffsetDateTime emailVerificadoEm
    ) {
        this(id, nome, email, senhaHash, role, criadoEm, emailVerificadoEm, 0, null);
    }

    public boolean emailVerificado() {
        return emailVerificadoEm != null;
    }

    /** Marca o e-mail como confirmado. Se já estava, mantém a data da primeira confirmação. */
    public Usuario comEmailVerificado(OffsetDateTime quando) {
        Validacoes.requererNaoNulo(quando, "quando");
        if (emailVerificado()) {
            return this;
        }
        return new Usuario(id, nome, email, senhaHash, role, criadoEm, quando, versaoSessao, senhaAlteradaEm);
    }

    /**
     * Troca a senha: grava o hash novo, sobe a versão da sessão (os JWT emitidos antes deixam de valer) e guarda
     * quando foi. Também confirma o e-mail se ainda não estava: quem troca a senha pelo link do e-mail provou que é
     * dono dele. Na persistência, o mesmo efeito é feito num UPDATE atômico.
     */
    public Usuario comSenhaAlterada(String novoSenhaHash, OffsetDateTime quando) {
        Validacoes.validarTextoObrigatorio(novoSenhaHash, "novoSenhaHash", 255);
        Validacoes.requererNaoNulo(quando, "quando");
        OffsetDateTime verificadoEm = emailVerificado() ? emailVerificadoEm : quando;
        return new Usuario(id, nome, email, novoSenhaHash, role, criadoEm, verificadoEm,
                Math.incrementExact(versaoSessao), quando);
    }

    @Override
    public String toString() {
        return "Usuario[id=" + id + ", email=" + email + ", role=" + role
                + ", emailVerificado=" + emailVerificado() + ", versaoSessao=" + versaoSessao + ", senhaHash=***]";
    }
}
