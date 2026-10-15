package com.mentalgym

import com.mentalgym.config.MentalGymProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(MentalGymProperties::class)
class MentalGymApplication

fun main(args: Array<String>) {
    runApplication<MentalGymApplication>(*args)
}
