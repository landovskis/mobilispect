package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.Administrator
import java.util.Optional
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AdministratorRepository : JpaRepository<Administrator, UUID> {
  fun findByUsername(username: String): Optional<Administrator>
}
