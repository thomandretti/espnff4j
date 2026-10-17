package com.thomandretti.espnff4j.internal.espn

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

internal val OBJECT_MAPPER: ObjectMapper =
    ObjectMapper()
        .registerModules(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
