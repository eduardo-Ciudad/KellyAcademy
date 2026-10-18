package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, UUID> {
    Optional<UsuarioEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Troca a senha num único UPDATE. O "+ 1" é feito no banco e o UPDATE trava a linha: duas trocas
     * simultâneas não perdem incremento. Também confirma o e-mail se ainda não estava (quem troca a senha
     * pelo link provou que é dono do e-mail). Retorna 1 (trocou) ou 0 (conta não existe).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE usuarios
               SET senha_hash = :hash,
                   versao_sessao = versao_sessao + 1,
                   senha_alterada_em = :quando,
                   email_verificado_em = COALESCE(email_verificado_em, :quando)
             WHERE id = :id
            """, nativeQuery = true)
    int alterarSenha(
            @Param("id") UUID id,
            @Param("hash") String hash,
            @Param("quando") OffsetDateTime quando
    );

    /** Só a versão da sessão, sem carregar a entity: é consultada a cada requisição autenticada. */
    @Query(value = "SELECT versao_sessao FROM usuarios WHERE id = :id", nativeQuery = true)
    Optional<Integer> buscarVersaoSessao(@Param("id") UUID id);
}
