package com.mobilispect.backend.config

import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule

/**
 * Jackson module to support serialization/deserialization of Spring Data Page objects in Redis
 * cache.
 *
 * PageImpl doesn't have a default constructor, which causes Jackson deserialization to fail when
 * retrieving cached Page objects from Redis. This module provides a custom deserializer that
 * properly reconstructs PageImpl instances.
 */
@Component
class PageJacksonModule : SimpleModule() {
  init {
    addDeserializer(PageImpl::class.java, PageImplDeserializer())
  }
}

/**
 * Custom deserializer for PageImpl to handle JSON deserialization from Redis cache.
 *
 * This deserializer properly handles the polymorphic type information stored by Jackson, ensuring
 * that content items are deserialized with their correct concrete types.
 */
class PageImplDeserializer : ValueDeserializer<PageImpl<*>>() {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PageImpl<*> {
    val node: JsonNode = ctxt.readTree(p)

    // Extract page metadata
    val number = node.get("number")?.asInt() ?: 0
    val size = node.get("size")?.asInt() ?: 20
    val totalElements = node.get("totalElements")?.asLong() ?: 0

    // Extract content array, preserving type information
    val contentNode = node.get("content")
    val content: List<Any> =
      if (contentNode != null && contentNode.isArray) {
        // Use the codec's ability to read values with full type information
        val listType =
          ctxt.typeFactory.constructCollectionType(ArrayList::class.java, Any::class.java)
        ctxt.readTreeAsValue(contentNode, listType) ?: emptyList()
      } else {
        emptyList()
      }

    return PageImpl(content, PageRequest.of(number, size), totalElements)
  }
}
