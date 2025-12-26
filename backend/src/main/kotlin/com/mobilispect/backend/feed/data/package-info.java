/**
 * Internal data layer for the Feed module.
 *
 * This package contains JPA entities, repositories, and mappers that should
 * not be accessed directly by other modules. Other modules must use the
 * FeedQueryApi in the feed.api package instead.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
@org.springframework.modulith.NamedInterface("internal")
package com.mobilispect.backend.feed.data;
