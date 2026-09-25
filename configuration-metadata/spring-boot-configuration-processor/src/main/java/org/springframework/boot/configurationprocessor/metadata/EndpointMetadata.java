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

package org.springframework.boot.configurationprocessor.metadata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The configuration metadata contributed by a type annotated with one of the actuator
 * endpoint annotations.
 * <p>
 * The shape of an endpoint's metadata (its group, the {@code access} property and the
 * optional {@code cache.time-to-live} property) is independent of the source model that
 * described the endpoint. This factory therefore builds it from plain values so that it
 * can be reused by both the {@code javax.lang.model} annotation processor and the Kotlin
 * Symbol Processing (KSP) processor rather than being duplicated in each.
 *
 * @author Stephane Nicoll
 * @author Areg Iazychian
 * @since 4.2.0
 */
public final class EndpointMetadata {

	/**
	 * Prefix of the metadata keys contributed by an endpoint.
	 */
	public static final String ENDPOINT_PREFIX = "management.endpoint.";

	/**
	 * Fully qualified name of the {@code Access} enum used for the {@code access}
	 * property.
	 */
	public static final String ACCESS_ENUM = "org.springframework.boot.actuate.endpoint.Access";

	/**
	 * Access level assumed when an endpoint does not declare one.
	 */
	public static final String DEFAULT_ACCESS = "unrestricted";

	private final ItemMetadata group;

	private final ItemMetadata accessProperty;

	private final ItemMetadata cacheProperty;

	private final String defaultAccess;

	private EndpointMetadata(ItemMetadata group, ItemMetadata accessProperty, ItemMetadata cacheProperty,
			String defaultAccess) {
		this.group = group;
		this.accessProperty = accessProperty;
		this.cacheProperty = cacheProperty;
		this.defaultAccess = defaultAccess;
	}

	/**
	 * Create the metadata contributed by an endpoint.
	 * @param endpointId the id of the endpoint
	 * @param type the fully qualified name of the endpoint type
	 * @param accessEnumType the fully qualified name of the {@code Access} enum, allowing
	 * a processor to override {@link #ACCESS_ENUM}
	 * @param defaultAccess the declared default access, or {@code null} to fall back to
	 * {@link #DEFAULT_ACCESS}; the value is always lower-cased
	 * @param hasMainReadOperation whether the endpoint declares a main read operation, in
	 * which case a {@code cache.time-to-live} property is contributed
	 * @return the endpoint metadata
	 */
	public static EndpointMetadata of(String endpointId, String type, String accessEnumType, String defaultAccess,
			boolean hasMainReadOperation) {
		String key = ItemMetadata.newItemMetadataPrefix(ENDPOINT_PREFIX, endpointId);
		String access = normalizeAccess(defaultAccess);
		ItemMetadata group = ItemMetadata.newGroup(key, type, type, null);
		ItemMetadata accessProperty = ItemMetadata.newProperty(key, "access", accessEnumType, type, null,
				"Permitted level of access for the %s endpoint.".formatted(endpointId), access, null);
		ItemMetadata cacheProperty = hasMainReadOperation
				? ItemMetadata.newProperty(key, "cache.time-to-live", Duration.class.getName(), type, null,
						"Maximum time that a response can be cached.", "0ms", null)
				: null;
		return new EndpointMetadata(group, accessProperty, cacheProperty, access);
	}

	/**
	 * Return the given access level defaulted to {@link #DEFAULT_ACCESS} and lower-cased.
	 * @param defaultAccess the declared access level, or {@code null}
	 * @return the normalized access level
	 */
	public static String normalizeAccess(String defaultAccess) {
		String access = (defaultAccess != null) ? defaultAccess : DEFAULT_ACCESS;
		return access.toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Return the group metadata of the endpoint.
	 * @return the group
	 */
	public ItemMetadata getGroup() {
		return this.group;
	}

	/**
	 * Return the {@code access} property metadata of the endpoint.
	 * @return the access property
	 */
	public ItemMetadata getAccessProperty() {
		return this.accessProperty;
	}

	/**
	 * Return the {@code cache.time-to-live} property metadata of the endpoint, or
	 * {@code null} when the endpoint has no main read operation.
	 * @return the cache property or {@code null}
	 */
	public ItemMetadata getCacheProperty() {
		return this.cacheProperty;
	}

	/**
	 * Return the property metadata contributed by the endpoint, that is the {@code access}
	 * property and, when present, the {@code cache.time-to-live} property.
	 * @return the properties
	 */
	public List<ItemMetadata> getProperties() {
		List<ItemMetadata> properties = new ArrayList<>();
		properties.add(this.accessProperty);
		if (this.cacheProperty != null) {
			properties.add(this.cacheProperty);
		}
		return properties;
	}

	/**
	 * Return the normalized default access level of the endpoint.
	 * @return the default access
	 */
	public String getDefaultAccess() {
		return this.defaultAccess;
	}

}
