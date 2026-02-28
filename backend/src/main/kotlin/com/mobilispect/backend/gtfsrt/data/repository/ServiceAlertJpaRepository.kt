package com.mobilispect.backend.gtfsrt.data.repository

import com.mobilispect.backend.gtfsrt.data.entity.ServiceAlertEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ServiceAlertJpaRepository : JpaRepository<ServiceAlertEntity, UUID>
