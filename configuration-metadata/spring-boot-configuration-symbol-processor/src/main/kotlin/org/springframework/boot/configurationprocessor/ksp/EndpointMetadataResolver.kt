/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.configurationprocessor.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import org.springframework.boot.configurationprocessor.ksp.ConfigurationPropertiesAnnotations.ENDPOINT_ACCESS_ENUM
import org.springframework.boot.configurationprocessor.ksp.ConfigurationPropertiesAnnotations.ENDPOINT_ANNOTATIONS
import org.springframework.boot.configurationprocessor.ksp.ConfigurationPropertiesAnnotations.READ_OPERATION
import org.springframework.boot.configurationprocessor.metadata.EndpointMetadata

/**
 * Resolves the metadata contributed by a type annotated with one of the actuator endpoint
 * annotations.
 *
 * The shape of the metadata is built by the shared [EndpointMetadata] factory so that it
 * stays identical to the one produced by the Java annotation processor. This resolver only
 * deals with the Kotlin specifics: locating the endpoint annotation, reading its
 * attributes and detecting the main read operation.
 *
 * @author Areg Iazychian
 * @since 4.2.0
 */
internal class EndpointMetadataResolver(private val context: MetadataGenerationContext) {

	/**
	 * Return the metadata of the given [declaration] when it is an endpoint, or `null`
	 * when it is not an endpoint or declares no identifier.
	 */
	fun resolve(declaration: KSClassDeclaration): EndpointMetadata? {
		val annotation = ENDPOINT_ANNOTATIONS.firstNotNullOfOrNull { declaration.findAnnotation(it) }
			?: return null
		val id = annotation.findStringValue("id") ?: return null
		val type = this.context.typeNames.resolve(declaration)
		val defaultAccess = accessNameOf(annotation.findValue("defaultAccess"))
		return EndpointMetadata.of(id, type, ENDPOINT_ACCESS_ENUM, defaultAccess, hasMainReadOperation(declaration))
	}

	private fun accessNameOf(value: Any?): String? = when (value) {
		null -> null
		is KSType -> value.declaration.simpleName.asString()
		is KSClassDeclaration -> value.simpleName.asString()
		else -> value.toString()
	}

	private fun hasMainReadOperation(declaration: KSClassDeclaration): Boolean =
		declaration.getDeclaredFunctions().any { function ->
			function.hasAnnotation(READ_OPERATION) && !returnsUnit(function) && hasNoMandatoryParameters(function)
		}

	private fun returnsUnit(function: KSFunctionDeclaration): Boolean =
		function.returnType?.resolve()?.declaration?.qualifiedName?.asString() == KOTLIN_UNIT

	private fun hasNoMandatoryParameters(function: KSFunctionDeclaration): Boolean =
		function.parameters.all { it.type.resolve().isMarkedNullable || it.hasDefault }

	private companion object {

		private const val KOTLIN_UNIT = "kotlin.Unit"

	}

}
