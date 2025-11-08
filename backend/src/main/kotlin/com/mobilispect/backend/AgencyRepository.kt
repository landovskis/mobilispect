package com.mobilispect.backend

import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.Repository
import org.springframework.data.rest.core.annotation.RestResource

interface AgencyRepository : ListCrudRepository<Agency, String> {
    override fun findAll(): List<Agency>

    @RestResource(exported = false)
    fun save(agency: Agency): Agency

    @RestResource(exported = false)
    override fun deleteAll()
}
