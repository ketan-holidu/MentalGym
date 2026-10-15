package com.mentalgym.config

import com.mentalgym.persistence.TopicRepository
import com.mentalgym.service.DEFAULT_TOPIC
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DefaultTopicInitializer {

    @Bean
    fun ensureDefaultTopic(topicRepository: TopicRepository) = ApplicationRunner {
        topicRepository.findOrCreate(DEFAULT_TOPIC)
    }
}
