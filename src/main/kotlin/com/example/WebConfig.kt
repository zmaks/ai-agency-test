package com.example

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


@Configuration
open class WebConfig {

    @Bean
    open fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**") // allow all endpoints
                    .allowedOrigins("*") // allow all domains
                    .allowedMethods("*") // allow all HTTP methods
                    .allowedHeaders("*") // allow all headers
            }
        }
    }
}