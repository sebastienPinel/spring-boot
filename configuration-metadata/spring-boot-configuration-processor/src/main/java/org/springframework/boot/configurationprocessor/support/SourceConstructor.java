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
 * A minimal, source-model independent view of a constructor, exposing only the facts that
 * {@link BindConstructorResolver} needs to select the constructor used for binding.
 * <p>
 * It is implemented as a thin adapter over a {@code javax.lang.model.element.ExecutableElement}
 * by the annotation processor and over a KSP constructor declaration by the Kotlin Symbol
 * Processing processor.
 *
 * @author Areg Iazychian
 * @since 4.2.0
 * @see BindConstructorResolver
 */
public interface SourceConstructor {

	/**
	 * Return whether the constructor declares at least one parameter.
	 * @return {@code true} if the constructor has parameters
	 */
	boolean hasParameters();

	/**
	 * Return whether the constructor is private.
	 * @return {@code true} if the constructor is private
	 */
	boolean isPrivate();

	/**
	 * Return whether the constructor is annotated with {@code @Autowired}.
	 * @return {@code true} if the constructor is autowired
	 */
	boolean hasAutowiredAnnotation();

	/**
	 * Return whether the constructor is annotated with {@code @ConstructorBinding}.
	 * @return {@code true} if the constructor is annotated for binding
	 */
	boolean hasConstructorBindingAnnotation();

}
