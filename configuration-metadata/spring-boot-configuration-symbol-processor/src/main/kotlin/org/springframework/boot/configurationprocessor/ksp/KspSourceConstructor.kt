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

import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import org.springframework.boot.configurationprocessor.ksp.ConfigurationPropertiesAnnotations.AUTOWIRED
import org.springframework.boot.configurationprocessor.ksp.ConfigurationPropertiesAnnotations.CONSTRUCTOR_BINDING
import org.springframework.boot.configurationprocessor.support.SourceConstructor

/**
 * [SourceConstructor] backed by a KSP [KSFunctionDeclaration], letting the shared
 * [org.springframework.boot.configurationprocessor.support.BindConstructorResolver] select
 * the bind constructor with the same rules as the Java annotation processor.
 *
 * @author Areg Iazychian
 * @since 4.2.0
 */
internal class KspSourceConstructor(val declaration: KSFunctionDeclaration) : SourceConstructor {

	override fun hasParameters(): Boolean = this.declaration.parameters.isNotEmpty()

	override fun isPrivate(): Boolean = this.declaration.isPrivate()

	override fun hasAutowiredAnnotation(): Boolean = this.declaration.hasAnnotation(AUTOWIRED)

	override fun hasConstructorBindingAnnotation(): Boolean =
		this.declaration.hasAnnotation(CONSTRUCTOR_BINDING, considerMetaAnnotations = true)

}
