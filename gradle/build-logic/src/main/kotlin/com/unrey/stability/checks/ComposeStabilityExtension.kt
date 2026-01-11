package com.unrey.stability.checks

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ComposeStabilityExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val reportFormat: Property<String> = objects.property(String::class.java).convention("text")
}