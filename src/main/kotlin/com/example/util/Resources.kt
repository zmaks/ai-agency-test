package com.example.util

import org.springframework.core.io.ClassPathResource

object Resources {

    fun read(path: String): String {
        return ClassPathResource(path).inputStream
            .bufferedReader()
            .use { it.readText() }
    }
}