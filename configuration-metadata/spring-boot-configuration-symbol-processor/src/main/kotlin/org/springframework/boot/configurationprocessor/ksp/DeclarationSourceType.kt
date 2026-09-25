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

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.springframework.boot.configurationprocessor.support.SourceType

/**
 * [SourceType] backed by a KSP [KSClassDeclaration], letting the shared
 * [org.springframework.boot.configurationprocessor.support.NestedTypeDetector] navigate a
 * Kotlin type hierarchy using the Java names the metadata is expected to contain.
 *
 * @author Areg Iazychian
 * @since 4.2.0
 */
internal class DeclarationSourceType(
	private val declaration: KSClassDeclaration,
	private val typeNames: JavaTypeNameResolver,
) : SourceType {

	override fun getQualifiedName(): String = this.typeNames.resolve(this.declaration)

	override fun isEnum(): Boolean =
		this.declaration.classKind == ClassKind.ENUM_CLASS || this.declaration.classKind == ClassKind.ENUM_ENTRY

	override fun getEnclosingType(): SourceType? {
		val parent = this.declaration.parentDeclaration
		return if (parent is KSClassDeclaration) DeclarationSourceType(parent, this.typeNames) else null
	}

	override fun getSuperType(): SourceType? {
		val superClass = this.declaration.superTypes
			.map { it.resolve().declaration }
			.filterIsInstance<KSClassDeclaration>()
			.firstOrNull { it.classKind == ClassKind.CLASS && it.qualifiedName?.asString() != KOTLIN_ANY }
		return if (superClass != null) DeclarationSourceType(superClass, this.typeNames) else null
	}

	private companion object {

		private const val KOTLIN_ANY = "kotlin.Any"

	}

}
