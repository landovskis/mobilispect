package com.mobilispect.backend.route.data.entity

import com.mobilispect.backend.stop.data.entity.StopEntity
import jakarta.persistence.*
import java.io.Serializable

/**
 * Composite primary key for RouteVariantStopEntity.
 *
 * Represents the combination of variant_id and stop_sequence that uniquely identifies a stop
 * position within a route variant.
 */
@Embeddable
data class RouteVariantStopId(
  @Column(name = "variant_id", nullable = false, length = 64) val variantId: String = "",
  @Column(name = "stop_sequence", nullable = false) val stopSequence: Int = 0,
) : Serializable {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RouteVariantStopId) return false
    return variantId == other.variantId && stopSequence == other.stopSequence
  }

  override fun hashCode(): Int {
    var result = variantId.hashCode()
    result = 31 * result + stopSequence
    return result
  }
}

/**
 * JPA entity for route_variant_stops junction table.
 *
 * Links route variants to their ordered stop sequences, enabling normalized queries like "find all
 * variants serving stop X" while maintaining denormalized stop patterns in route_variants table for
 * display performance.
 */
@Entity
@Table(name = "route_variant_stops")
class RouteVariantStopEntity(
  @EmbeddedId val id: RouteVariantStopId,
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_id", insertable = false, updatable = false)
  val variant: RouteVariantEntity,
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stop_onestop_id", nullable = false)
  val stop: StopEntity,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RouteVariantStopEntity) return false
    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()
}
