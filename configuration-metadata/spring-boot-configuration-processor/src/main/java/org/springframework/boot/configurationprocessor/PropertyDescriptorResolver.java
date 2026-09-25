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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;

import org.springframework.boot.configurationprocessor.ConfigurationPropertiesSourceResolver.SourceMetadata;
import org.springframework.boot.configurationprocessor.support.BindConstructorResolver;

/**
 * Resolve {@link PropertyDescriptor} instances.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @author Pavel Anisimov
 */
class PropertyDescriptorResolver {

	private final MetadataGenerationEnvironment environment;

	PropertyDescriptorResolver(MetadataGenerationEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the {@link PropertyDescriptor} instances that are valid candidates for the
	 * specified {@link TypeElement type} based on the specified {@link ExecutableElement
	 * factory method}, if any.
	 * @param type the target type
	 * @param factoryMethod the method that triggered the metadata for that {@code type}
	 * or {@code null}
	 * @return the candidate properties for metadata generation
	 */
	Stream<PropertyDescriptor> resolve(TypeElement type, ExecutableElement factoryMethod) {
		TypeElementMembers members = new TypeElementMembers(this.environment, type);
		if (factoryMethod != null) {
			return resolveJavaBeanProperties(type, members, factoryMethod);
		}
		List<ElementSourceConstructor> constructors = ElementFilter.constructorsIn(type.getEnclosedElements())
			.stream()
			.map((constructor) -> new ElementSourceConstructor(constructor, this.environment))
			.toList();
		boolean member = type.getNestingKind() == NestingKind.MEMBER;
		BindConstructorResolver.Resolution<ElementSourceConstructor> resolution = BindConstructorResolver
			.resolve(constructors, member);
		return switch (resolution.getKind()) {
			case CONSTRUCTOR_BOUND ->
				resolveConstructorBoundProperties(type, members, resolution.getConstructor().getElement());
			case JAVA_BEAN -> resolveJavaBeanProperties(type, members, null);
			case NONE -> Stream.empty();
		};
	}

	private Stream<PropertyDescriptor> resolveConstructorBoundProperties(TypeElement declaringElement,
			TypeElementMembers members, ExecutableElement bindConstructor) {
		Map<String, PropertyDescriptor> candidates = new LinkedHashMap<>();
		bindConstructor.getParameters().forEach((parameter) -> {
			PropertyDescriptor descriptor = extracted(declaringElement, members, parameter);
			register(candidates, descriptor);
		});
		return candidates.values().stream();
	}

	private PropertyDescriptor extracted(TypeElement declaringElement, TypeElementMembers members,
			VariableElement parameter) {
		String parameterName = parameter.getSimpleName().toString();
		String name = getPropertyName(parameter, parameterName);
		TypeMirror type = parameter.asType();
		ExecutableElement getter = members.getPublicGetter(parameterName, type);
		ExecutableElement setter = members.getPublicSetter(parameterName, type);
		VariableElement field = members.getFields().get(parameterName);
		RecordComponentElement recordComponent = members.getRecordComponents().get(parameterName);
		SourceMetadata sourceMetadata = this.environment.resolveSourceMetadata(field, getter);
		PropertyDescriptor propertyDescriptor = (recordComponent != null)
				? new RecordParameterPropertyDescriptor(name, type, parameter, declaringElement, getter,
						recordComponent)
				: new ConstructorParameterPropertyDescriptor(name, type, parameter, declaringElement, getter, setter,
						field);
		return sourceMetadata.createPropertyDescriptor(name, propertyDescriptor);
	}

	private String getPropertyName(VariableElement parameter, String fallback) {
		AnnotationMirror nameAnnotation = this.environment.getNameAnnotation(parameter);
		if (nameAnnotation != null) {
			return this.environment.getAnnotationElementStringValue(nameAnnotation, "value");
		}
		return fallback;
	}

	private Stream<PropertyDescriptor> resolveJavaBeanProperties(TypeElement declaringElement,
			TypeElementMembers members, ExecutableElement factoryMethod) {
		// First check if we have regular java bean properties there
		Map<String, PropertyDescriptor> candidates = new LinkedHashMap<>();
		members.getPublicGetters().forEach((name, getters) -> {
			VariableElement field = members.getFields().get(name);
			ExecutableElement getter = findMatchingGetter(members, getters, field);
			TypeMirror propertyType = getter.getReturnType();
			SourceMetadata sourceMetadata = this.environment.resolveSourceMetadata(field, getter);
			register(candidates,
					sourceMetadata.createPropertyDescriptor(getPropertyName(field, name),
							(propertyName) -> new JavaBeanPropertyDescriptor(propertyName, propertyType,
									declaringElement, getter, members.getPublicSetter(name, propertyType), field,
									factoryMethod)));
		});
		// Then check for Lombok ones
		members.getFields().forEach((name, field) -> {
			TypeMirror propertyType = field.asType();
			ExecutableElement getter = members.getPublicGetter(name, propertyType);
			ExecutableElement setter = members.getPublicSetter(name, propertyType);
			SourceMetadata sourceMetadata = this.environment.resolveSourceMetadata(field, getter);
			register(candidates,
					sourceMetadata.createPropertyDescriptor(getPropertyName(field, name),
							(propertyName) -> new LombokPropertyDescriptor(propertyName, propertyType, declaringElement,
									getter, setter, field, factoryMethod)));
		});
		return candidates.values().stream();
	}

	private ExecutableElement findMatchingGetter(TypeElementMembers members, List<ExecutableElement> candidates,
			VariableElement field) {
		if (candidates.size() > 1 && field != null) {
			return members.getMatchingGetter(candidates, field.asType());
		}
		return candidates.get(0);
	}

	private void register(Map<String, PropertyDescriptor> candidates, PropertyDescriptor descriptor) {
		if (!isCandidate(descriptor)) {
			return;
		}
		PropertyDescriptor existing = candidates.get(descriptor.getName());
		if (existing == null) {
			candidates.put(descriptor.getName(), descriptor);
		}
		else if (isDistinctDescriptor(existing, descriptor)) {
			this.environment.getMessager()
				.printMessage(Kind.ERROR,
						"Property name '%s' maps to distinct properties in type %s".formatted(descriptor.getName(),
								this.environment.getTypeUtils().getQualifiedName(descriptor.getDeclaringElement())));
		}
	}

	private boolean isCandidate(PropertyDescriptor descriptor) {
		return descriptor.isProperty(this.environment) || descriptor.isNested(this.environment);
	}

	private boolean isDistinctDescriptor(PropertyDescriptor existing, PropertyDescriptor candidate) {
		return Objects.equals(existing.getClass(), candidate.getClass())
				&& !Objects.equals(existing.getSourceElement(), candidate.getSourceElement());
	}

}
