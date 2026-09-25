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

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Origin
import org.springframework.boot.configurationprocessor.ksp.ConfigurationPropertiesAnnotations.NAME
import org.springframework.boot.configurationprocessor.support.BindConstructorResolver

/**
 * Resolves the [PropertyDescriptor] instances of a class annotated with
 * `@ConfigurationProperties`.
 *
 * @author Areg Iazychian
 * @since 4.2.0
 */
internal class PropertyDescriptorResolver(private val context: MetadataGenerationContext) {

	/**
	 * Return the properties of the given [declaration] that are candidates for metadata
	 * generation.
	 */
	fun resolve(
		declaration: KSClassDeclaration,
		factoryMethod: KSFunctionDeclaration? = null,
	): List<PropertyDescriptor> {
		if (factoryMethod != null) {
			return resolveJavaBean(declaration, factoryMethod)
		}
		val constructors = declaration.getConstructors().map(::KspSourceConstructor).toList()
		val member = declaration.parentDeclaration is KSClassDeclaration
		val resolution = BindConstructorResolver.resolve(constructors, member)
		return when (resolution.kind) {
			BindConstructorResolver.Resolution.Kind.CONSTRUCTOR_BOUND ->
				resolveConstructorBound(declaration, resolution.constructor.declaration)
			BindConstructorResolver.Resolution.Kind.JAVA_BEAN -> resolveJavaBean(declaration)
			BindConstructorResolver.Resolution.Kind.NONE -> emptyList()
		}
	}

	private fun resolveConstructorBound(
		declaration: KSClassDeclaration,
		constructor: KSFunctionDeclaration,
	): List<PropertyDescriptor> {
		val properties = declaration.getAllProperties().associateBy { it.simpleName.asString() }
		val descriptors = LinkedHashMap<String, PropertyDescriptor>()
		constructor.parameters.forEach { parameter ->
			val parameterName = parameter.name?.asString()
			if (parameterName != null) {
				val name = parameter.findAnnotation(NAME)?.findStringValue("value") ?: parameterName
				val descriptor = PropertyDescriptor.ConstructorParameter(
					name,
					parameter.type.resolve(),
					declaration,
					parameter,
					properties[parameterName],
				)
				register(descriptors, descriptor, declaration)
			}
		}
		return descriptors.values.toList()
	}

	private fun resolveJavaBean(
		declaration: KSClassDeclaration,
		factoryMethod: KSFunctionDeclaration? = null,
	): List<PropertyDescriptor> {
		val descriptors = LinkedHashMap<String, PropertyDescriptor>()
		// Kotlin properties, whose getter and mutability are exposed directly.
		declaration.getAllProperties().filter(::isCandidate).forEach { property ->
			val name = property.findAnnotation(NAME)?.findStringValue("value") ?: property.simpleName.asString()
			val descriptor = PropertyDescriptor.JavaBean(
				name,
				this.context.typeOf(property, declaration),
				declaration,
				property,
				factoryMethod,
			)
			register(descriptors, descriptor, declaration)
		}
		// Java properties, declared through their public accessors rather than as Kotlin
		// properties (their backing field is private, so it is not a candidate above).
		resolveAccessorProperties(declaration, factoryMethod, descriptors)
		return descriptors.values.toList()
	}

	private fun isCandidate(property: KSPropertyDeclaration): Boolean =
		property.isPublic() && property.getter != null

	private fun resolveAccessorProperties(
		declaration: KSClassDeclaration,
		factoryMethod: KSFunctionDeclaration?,
		descriptors: MutableMap<String, PropertyDescriptor>,
	) {
		val functions = declaration.getAllFunctions()
			.filter { it.isPublic() && isJavaOrigin(it) && !isFromObject(it) }
			.toList()
		val setters = functions.filter(::isSetter).associateBy { accessorName(it.simpleName.asString()) }
		val fields = declaration.getAllProperties().associateBy { it.simpleName.asString() }
		functions.filter(::isGetter).forEach { getter ->
			val propertyName = accessorName(getter.simpleName.asString())
			val type = getter.returnType?.resolve() ?: return@forEach
			val field = fields[propertyName]
			val name = (getter.findAnnotation(NAME) ?: field?.findAnnotation(NAME))?.findStringValue("value")
				?: propertyName
			val descriptor = PropertyDescriptor.JavaAccessor(
				name,
				type,
				declaration,
				getter,
				setters[propertyName],
				field,
				factoryMethod,
			)
			register(descriptors, descriptor, declaration)
		}
	}

	private fun isGetter(function: KSFunctionDeclaration): Boolean {
		if (function.parameters.isNotEmpty() || returnsUnit(function)) {
			return false
		}
		val name = function.simpleName.asString()
		return (name.startsWith("get") && name.length > 3) || (name.startsWith("is") && name.length > 2)
	}

	private fun isSetter(function: KSFunctionDeclaration): Boolean {
		val name = function.simpleName.asString()
		return name.startsWith("set") && name.length > 3 && function.parameters.size == 1
	}

	private fun returnsUnit(function: KSFunctionDeclaration): Boolean =
		function.returnType?.resolve()?.declaration?.qualifiedName?.asString() == KOTLIN_UNIT

	private fun accessorName(methodName: String): String {
		val stripped = if (methodName.startsWith("is")) methodName.substring(2) else methodName.substring(3)
		return stripped.replaceFirstChar(Char::lowercaseChar)
	}

	private fun isJavaOrigin(function: KSFunctionDeclaration): Boolean =
		function.origin == Origin.JAVA || function.origin == Origin.JAVA_LIB

	private fun isFromObject(function: KSFunctionDeclaration): Boolean {
		val owner = function.parentDeclaration?.qualifiedName?.asString()
		return owner == JAVA_OBJECT || owner == KOTLIN_ANY
	}

	private fun register(
		descriptors: MutableMap<String, PropertyDescriptor>,
		descriptor: PropertyDescriptor,
		declaration: KSClassDeclaration,
	) {
		if (!descriptor.isProperty(this.context) && !descriptor.isNested(this.context)) {
			return
		}
		val existing = descriptors.putIfAbsent(descriptor.name, descriptor)
		if (existing != null && existing.type != descriptor.type) {
			this.context.logger.error(
				"Property name '${descriptor.name}' maps to distinct properties in type " +
					context.typeNames.resolve(declaration),
				declaration,
			)
		}
	}

	private companion object {

		private const val KOTLIN_UNIT = "kotlin.Unit"

		private const val KOTLIN_ANY = "kotlin.Any"

		private const val JAVA_OBJECT = "java.lang.Object"

	}

}
