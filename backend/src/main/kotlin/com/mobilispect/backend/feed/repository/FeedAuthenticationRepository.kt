package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.FeedAuthentication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FeedAuthenticationRepository : JpaRepository<FeedAuthentication, String>
