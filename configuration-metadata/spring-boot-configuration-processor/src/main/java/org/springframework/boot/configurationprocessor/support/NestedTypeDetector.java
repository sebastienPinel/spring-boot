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
 * Decides whether a property describes a nested group of properties rather than a leaf
 * property.
 * <p>
 * The decision walks the type hierarchy by name and is therefore independent of the
 * source model. It is shared by both the {@code javax.lang.model} annotation processor and
 * the Kotlin Symbol Processing (KSP) processor: each supplies the boolean facts it can
 * observe about the property and a {@link SourceType} view of the property type and its
 * declaring type.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @author Areg Iazychian
 * @since 4.2.0
 */
public final class NestedTypeDetector {

	private NestedTypeDetector() {
	}

	/**
	 * Return whether the described property is a nested group of properties.
	 * @param nestedType the type of the property, or {@code null} if the property type is
	 * not a class or interface (in which case it can never be nested)
	 * @param declaringType the type that declares the property
	 * @param hasConfigurationProperties whether the property is itself annotated with
	 * {@code @ConfigurationProperties}, in which case it is a group of its own rather than
	 * a nested group
	 * @param markedAsNested whether the property is explicitly marked as nested (for
	 * example using {@code @NestedConfigurationProperty})
	 * @return whether the property is a nested group of properties
	 */
	public static boolean isNested(SourceType nestedType, SourceType declaringType, boolean hasConfigurationProperties,
			boolean markedAsNested) {
		if (nestedType == null || nestedType.isEnum() || hasConfigurationProperties) {
			return false;
		}
		if (markedAsNested) {
			return true;
		}
		return !isCyclePresent(nestedType, declaringType) && isParentTheSame(nestedType, declaringType);
	}

	private static boolean isCyclePresent(SourceType nestedType, SourceType declaringType) {
		SourceType enclosing = (declaringType != null) ? declaringType.getEnclosingType() : null;
		while (enclosing != null) {
			if (enclosing.getQualifiedName().equals(nestedType.getQualifiedName())) {
				return true;
			}
			enclosing = enclosing.getEnclosingType();
		}
		return false;
	}

	private static boolean isParentTheSame(SourceType nestedType, SourceType declaringType) {
		if (declaringType == null) {
			return false;
		}
		String topLevelName = topLevelType(nestedType).getQualifiedName();
		SourceType candidate = declaringType;
		while (candidate != null) {
			if (topLevelType(candidate).getQualifiedName().equals(topLevelName)) {
				return true;
			}
			candidate = candidate.getSuperType();
		}
		return false;
	}

	private static SourceType topLevelType(SourceType type) {
		SourceType current = type;
		while (current.getEnclosingType() != null) {
			current = current.getEnclosingType();
		}
		return current;
	}

}
