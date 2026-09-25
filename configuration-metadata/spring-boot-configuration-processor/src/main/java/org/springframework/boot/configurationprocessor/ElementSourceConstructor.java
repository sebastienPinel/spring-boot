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

package org.springframework.boot.configurationprocessor;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

import org.springframework.boot.configurationprocessor.support.SourceConstructor;

/**
 * {@link SourceConstructor} backed by a {@code javax.lang.model} {@link ExecutableElement}.
 *
 * @author Areg Iazychian
 */
class ElementSourceConstructor implements SourceConstructor {

	private final ExecutableElement element;

	private final MetadataGenerationEnvironment environment;

	ElementSourceConstructor(ExecutableElement element, MetadataGenerationEnvironment environment) {
		this.element = element;
		this.environment = environment;
	}

	ExecutableElement getElement() {
		return this.element;
	}

	@Override
	public boolean hasParameters() {
		return !this.element.getParameters().isEmpty();
	}

	@Override
	public boolean isPrivate() {
		return this.element.getModifiers().contains(Modifier.PRIVATE);
	}

	@Override
	public boolean hasAutowiredAnnotation() {
		return this.environment.hasAutowiredAnnotation(this.element);
	}

	@Override
	public boolean hasConstructorBindingAnnotation() {
		return this.environment.hasConstructorBindingAnnotation(this.element);
	}

}
