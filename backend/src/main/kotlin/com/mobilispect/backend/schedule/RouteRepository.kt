package com.mobilispect.backend.schedule

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.repository.CrudRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.data.rest.core.annotation.RestResource

@RepositoryRestResource(path = "routes")
interface RouteRepository : CrudRepository<Route, String> {
  /**
   * Returns all instances of the type.
   *
   * @return all entities
   */
  override fun findAll(): List<Route>

  @RestResource(exported = false) fun save(entity: Route): Route

  /**
   * Deletes all entities.
   *
   * @throws OptimisticLockingFailureException when at least one entity uses optimistic locking and
   *   has a version attribute with a different value from that found in the persistence store. Also
   *   thrown if at least one entity is assumed to be present but does not exist in the database.
   */
  override fun deleteAll()

  fun findAllByAgencyID(agencyID: String): List<Route>
}
