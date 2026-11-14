package com.miqueiasbento.io.webhook_service.delivery;

import java.time.LocalDateTime;
import com.miqueiasbento.io.webhook_service.destination.Destination;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "deliveries")
public class Delivery {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    private String eventType;

    @Column(columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    private DeliveryStatus status;

    private Integer attemptCount;

    private Integer responseStatus;

    @Column(columnDefinition = "text")
    private String responseBody;

    @Column(columnDefinition = "text")
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
