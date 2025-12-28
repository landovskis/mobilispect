package com.mobilispect.backend.infastructure

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.repository.CrudRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.data.rest.core.annotation.RestResource

@RepositoryRestResource
interface StopRepository : CrudRepository<Stop, String> {
  /**
   * Returns all instances of the type.
   *
   * @return all entities
   */
  override fun findAll(): List<Stop>

  /**
   * Saves a given entity. Use the returned instance for further operations as the save operation
   * might have changed the entity instance completely.
   *
   * @param entity must not be null.
   * @return the saved entity; will never be null.
   * @throws IllegalArgumentException in case the given entity is null.
   * @throws OptimisticLockingFailureException when the entity uses optimistic locking and has a
   *   version attribute with a different value from that found in the persistence store. Also
   *   thrown if the entity is assumed to be present but does not exist in the database.
   */
  @RestResource(exported = false) fun save(entity: Stop): Stop

  /**
   * Deletes all entities.
   *
   * @throws OptimisticLockingFailureException when at least one entity uses optimistic locking and
   *   has a version attribute with a different value from that found in the persistence store. Also
   *   thrown if at least one entity is assumed to be present but does not exist in the database.
   */
  @RestResource(exported = false) override fun deleteAll()
}
