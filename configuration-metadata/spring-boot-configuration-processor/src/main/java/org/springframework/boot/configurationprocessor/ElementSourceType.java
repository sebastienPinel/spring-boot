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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.springframework.boot.configurationprocessor.support.SourceType;

/**
 * {@link SourceType} backed by a {@code javax.lang.model} {@link TypeElement}.
 *
 * @author Areg Iazychian
 */
class ElementSourceType implements SourceType {

	private final TypeElement element;

	private final TypeUtils typeUtils;

	ElementSourceType(TypeElement element, TypeUtils typeUtils) {
		this.element = element;
		this.typeUtils = typeUtils;
	}

	@Override
	public String getQualifiedName() {
		return this.typeUtils.getQualifiedName(this.element);
	}

	@Override
	public boolean isEnum() {
		return this.element.getKind() == ElementKind.ENUM;
	}

	@Override
	public SourceType getEnclosingType() {
		return (this.element.getEnclosingElement() instanceof TypeElement enclosing)
				? new ElementSourceType(enclosing, this.typeUtils) : null;
	}

	@Override
	public SourceType getSuperType() {
		Element superElement = this.typeUtils.asElement(this.element.getSuperclass());
		return (superElement instanceof TypeElement superType) ? new ElementSourceType(superType, this.typeUtils)
				: null;
	}

}
