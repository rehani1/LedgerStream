package com.ledgerstream.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@MappedSuperclass
public abstract class TimestampedUuidEntity extends CreatedAtUuidEntity {

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	protected void markCreatedAndUpdatedAt() {
		if (getCreatedAt() == null) {
			setCreatedAt(Instant.now());
		}
		if (updatedAt == null) {
			updatedAt = getCreatedAt();
		}
	}

	@PreUpdate
	protected void markUpdatedAt() {
		updatedAt = Instant.now();
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
