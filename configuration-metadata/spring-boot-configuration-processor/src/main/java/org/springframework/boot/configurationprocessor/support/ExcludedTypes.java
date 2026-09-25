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

import java.util.Set;

/**
 * The fully qualified names of the types that can never be a configuration property or a
 * nested group.
 * <p>
 * The list is independent of the source model that describes a property, so it is shared
 * by both the {@code javax.lang.model} annotation processor and the Kotlin Symbol
 * Processing (KSP) processor rather than being duplicated in each.
 *
 * @author Stephane Nicoll
 * @author Areg Iazychian
 * @since 4.2.0
 */
public final class ExcludedTypes {

	private static final Set<String> EXCLUDES = Set.of("com.zaxxer.hikari.IConnectionCustomizer",
			"groovy.lang.MetaClass", "groovy.text.markup.MarkupTemplateEngine", "java.io.Writer", "java.io.PrintWriter",
			"java.lang.ClassLoader", "java.util.concurrent.ThreadFactory", "jakarta.jms.XAConnectionFactory",
			"javax.sql.DataSource", "javax.sql.XADataSource", "org.apache.tomcat.jdbc.pool.PoolConfiguration",
			"org.apache.tomcat.jdbc.pool.Validator", "org.flywaydb.core.api.callback.FlywayCallback",
			"org.flywaydb.core.api.resolver.MigrationResolver");

	private ExcludedTypes() {
	}

	/**
	 * Return whether the given fully qualified type name is excluded.
	 * @param qualifiedName the fully qualified name of the type
	 * @return {@code true} if the type is excluded
	 */
	public static boolean isExcluded(String qualifiedName) {
		return EXCLUDES.contains(qualifiedName);
	}

}
