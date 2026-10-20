package eduar.studymindredacao.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;


@Entity
@Table(name = "eventos_stripe")
@Getter
@Setter
@NoArgsConstructor
public class EventoStripeEntity {
    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(name = "processado_em", nullable = false)
    private OffsetDateTime processadoEm;
}