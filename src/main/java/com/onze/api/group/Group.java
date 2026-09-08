package com.onze.api.group;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String mascot;

    @Column(length = 255)
    private String venue;

    @Column(name = "default_payment_amount", precision = 10, scale = 2)
    private BigDecimal defaultPaymentAmount;

    @Column(name = "default_pix_key", length = 255)
    private String defaultPixKey;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Group() {
    }

    public Group(String name, String description, UUID createdBy) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public String getCity() {
        return city;
    }

    public String getMascot() {
        return mascot;
    }

    public String getVenue() {
        return venue;
    }

    public BigDecimal getDefaultPaymentAmount() {
        return defaultPaymentAmount;
    }

    public String getDefaultPixKey() {
        return defaultPixKey;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateOptionalDetails(String city, String mascot, String venue) {
        if (city != null) {
            this.city = city;
        }
        if (mascot != null) {
            this.mascot = mascot;
        }
        if (venue != null) {
            this.venue = venue;
        }
    }

    public void updatePhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void updatePaymentDetails(BigDecimal paymentAmount, String pixKey) {
        this.defaultPaymentAmount = paymentAmount;
        this.defaultPixKey = pixKey;
    }
}
