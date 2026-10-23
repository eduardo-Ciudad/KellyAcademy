package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.UsoIAGlobalDiarioEntity;
import eduar.studymindredacao.adapter.out.persistence.entity.UsoIAGlobalDiarioId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

interface UsoIAGlobalDiarioJpaRepository extends JpaRepository<UsoIAGlobalDiarioEntity, UsoIAGlobalDiarioId> {

    /**
     * Ler e reservar numa instrução só. Se a linha do dia ainda não existe e duas requisições chegam juntas,
     * o índice da chave primária faz a segunda esperar a primeira e cair no DO UPDATE, que reavalia o WHERE
     * com o valor já gravado. Retorna 1 se reservou, 0 se o teto já foi atingido.
     */
    @Modifying
    @Query(value = """
            INSERT INTO uso_ia_global_diario (data, recurso, quantidade)
            VALUES (:data, :recurso, 1)
            ON CONFLICT (data, recurso) DO UPDATE
                SET quantidade = uso_ia_global_diario.quantidade + 1
                WHERE uso_ia_global_diario.quantidade < :teto
            """, nativeQuery = true)
    int reservar(@Param("recurso") String recurso, @Param("data") LocalDate data, @Param("teto") int teto);

    @Modifying
    @Query(value = """
            UPDATE uso_ia_global_diario
               SET quantidade = GREATEST(quantidade - 1, 0)
             WHERE data = :data AND recurso = :recurso
            """, nativeQuery = true)
    int liberar(@Param("recurso") String recurso, @Param("data") LocalDate data);

    @Query(value = """
            SELECT COALESCE(MAX(quantidade), 0)
              FROM uso_ia_global_diario
             WHERE data = :data AND recurso = :recurso
            """, nativeQuery = true)
    int quantidadeDoDia(@Param("recurso") String recurso, @Param("data") LocalDate data);
}
