package com.mobilispect.backend.gtfsrt.data.repository

import com.mobilispect.backend.gtfsrt.data.entity.VehiclePositionEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface VehiclePositionJpaRepository : JpaRepository<VehiclePositionEntity, UUID>
