package com.mobilispect.backend.gtfsrt.data.repository

import com.mobilispect.backend.gtfsrt.data.entity.TripUpdateEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface TripUpdateJpaRepository : JpaRepository<TripUpdateEntity, UUID>
