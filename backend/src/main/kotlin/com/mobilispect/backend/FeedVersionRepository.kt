package com.mobilispect.backend

import org.springframework.data.repository.CrudRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.data.rest.core.annotation.RestResource

@RepositoryRestResource
interface FeedVersionRepository : CrudRepository<FeedVersion, String> {
  /**
   * Returns all instances of the type.
   *
   * @return all entities
   */
  override fun findAll(): List<FeedVersion>

  fun getByUid(uid: String): FeedVersion?

  @RestResource(exported = false) fun save(entity: FeedVersion): FeedVersion

  @RestResource(exported = false) override fun deleteAll()
}
