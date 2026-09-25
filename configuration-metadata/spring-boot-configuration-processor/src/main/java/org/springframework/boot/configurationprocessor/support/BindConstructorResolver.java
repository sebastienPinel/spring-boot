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

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the constructor used to bind a configuration properties type, or determines that
 * the type should be bound as a JavaBean instead.
 * <p>
 * The selection is independent of the source model, so it is shared by both the
 * {@code javax.lang.model} annotation processor and the Kotlin Symbol Processing (KSP)
 * processor: each supplies its constructors as {@link SourceConstructor} adapters and
 * whether the declaring type is a member (nested) type.
 * <p>
 * A single constructor is deduced as the bind constructor when it has parameters, is not
 * annotated with {@code @Autowired} and is not a private constructor of a member type.
 * Otherwise the constructors explicitly annotated with {@code @ConstructorBinding} are
 * used: exactly one selects constructor binding, none falls back to JavaBean binding, and
 * more than one is ambiguous and contributes no properties.
 *
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @author Areg Iazychian
 * @since 4.2.0
 */
public final class BindConstructorResolver {

	private BindConstructorResolver() {
	}

	/**
	 * Resolve how the type owning the given constructors should be bound.
	 * @param <C> the constructor type
	 * @param constructors the declared constructors of the type
	 * @param declaringTypeIsMember whether the declaring type is a member (nested) type
	 * @return the resolution
	 */
	public static <C extends SourceConstructor> Resolution<C> resolve(List<C> constructors,
			boolean declaringTypeIsMember) {
		List<C> boundConstructors = getBoundConstructors(constructors, declaringTypeIsMember);
		if (boundConstructors.isEmpty()) {
			return new Resolution<>(Resolution.Kind.JAVA_BEAN, null);
		}
		if (boundConstructors.size() == 1) {
			return new Resolution<>(Resolution.Kind.CONSTRUCTOR_BOUND, boundConstructors.get(0));
		}
		return new Resolution<>(Resolution.Kind.NONE, null);
	}

	private static <C extends SourceConstructor> List<C> getBoundConstructors(List<C> constructors,
			boolean declaringTypeIsMember) {
		C deduced = deduceBindConstructor(constructors, declaringTypeIsMember);
		if (deduced != null) {
			return List.of(deduced);
		}
		List<C> annotated = new ArrayList<>();
		for (C candidate : constructors) {
			if (candidate.hasConstructorBindingAnnotation()) {
				annotated.add(candidate);
			}
		}
		return annotated;
	}

	private static <C extends SourceConstructor> C deduceBindConstructor(List<C> constructors,
			boolean declaringTypeIsMember) {
		if (constructors.size() != 1) {
			return null;
		}
		C candidate = constructors.get(0);
		if (!candidate.hasParameters() || candidate.hasAutowiredAnnotation()) {
			return null;
		}
		if (declaringTypeIsMember && candidate.isPrivate()) {
			return null;
		}
		return candidate;
	}

	/**
	 * The outcome of {@link #resolve(List, boolean)}.
	 *
	 * @param <C> the constructor type
	 */
	public static final class Resolution<C extends SourceConstructor> {

		/**
		 * How a configuration properties type should be bound.
		 */
		public enum Kind {

			/**
			 * The type is bound using a constructor, available from
			 * {@link Resolution#getConstructor()}.
			 */
			CONSTRUCTOR_BOUND,

			/**
			 * The type is bound as a JavaBean.
			 */
			JAVA_BEAN,

			/**
			 * The binding is ambiguous and the type contributes no properties.
			 */
			NONE

		}

		private final Kind kind;

		private final C constructor;

		private Resolution(Kind kind, C constructor) {
			this.kind = kind;
			this.constructor = constructor;
		}

		/**
		 * Return the kind of binding.
		 * @return the kind
		 */
		public Kind getKind() {
			return this.kind;
		}

		/**
		 * Return the bind constructor, only set when the kind is
		 * {@link Kind#CONSTRUCTOR_BOUND}.
		 * @return the bind constructor or {@code null}
		 */
		public C getConstructor() {
			return this.constructor;
		}

	}

}
