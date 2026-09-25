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

package org.springframework.boot.configurationprocessor.support;

/**
 * A minimal, source-model independent view of a class or interface, letting logic that
 * only needs to navigate the type hierarchy by name be written once and backed by either
 * the {@code javax.lang.model} annotation processor or the Kotlin Symbol Processing (KSP)
 * processor.
 *
 * @author Areg Iazychian
 * @since 4.2.0
 * @see NestedTypeDetector
 */
public interface SourceType {

	/**
	 * Return the fully qualified name of the type, using the binary {@code $} separator
	 * for nested types. Names only need to be self-consistent within a single processor
	 * as they are only ever compared with other names produced by the same processor.
	 * @return the fully qualified name
	 */
	String getQualifiedName();

	/**
	 * Return whether the type is an enum.
	 * @return {@code true} if the type is an enum
	 */
	boolean isEnum();

	/**
	 * Return the type that encloses this type, or {@code null} if it is a top-level type.
	 * @return the enclosing type or {@code null}
	 */
	SourceType getEnclosingType();

	/**
	 * Return the superclass of this type, or {@code null} if it has none of interest (for
	 * example {@code java.lang.Object}).
	 * @return the superclass or {@code null}
	 */
	SourceType getSuperType();

}
