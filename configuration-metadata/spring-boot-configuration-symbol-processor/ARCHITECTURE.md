# Configuration metadata processing — shared architecture

## Why this document exists

`spring-boot-configuration-symbol-processor` is a Kotlin Symbol Processing (KSP)
processor that writes the same `META-INF/spring-configuration-metadata.json` as the
Java annotation processor in `spring-boot-configuration-processor`.

The design constraint is deliberate:

> The KSP support must be as thin a layer as possible that deals with Kotlin
> specifics, and delegate to an implementation written in Java that is **shared**
> with the annotation processor — rather than duplicating that logic.

This note describes how the two processors share a single Java implementation of the
metadata-generation decisions, where the shared code lives, and where the boundary is
that legitimately stays specific to each source model.

## The two source models

The processors are built on two unrelated type systems, and this is the root reason a
naive "share everything" is not possible:

| | Annotation processor | Symbol processor |
|---|---|---|
| Input model | `javax.lang.model` (`TypeElement`, `ExecutableElement`, `TypeMirror`, `AnnotationMirror`) | KSP (`KSClassDeclaration`, `KSFunctionDeclaration`, `KSType`, `KSAnnotation`) |
| Members | Java methods (`getX`/`isX`/`setX`) + fields + record components | Kotlin properties (getter/setter/mutability unified) |
| Reads initializers | Yes (via the compiler trees) | No (KSP does not expose them) |

Everything that can be expressed **independently of these models** is written once in
Java and reused. Everything that must traverse one of the models stays in that model's
adapter.

## Shared implementation (in `spring-boot-configuration-processor`)

Both processors depend on `spring-boot-configuration-processor`, so the shared code
lives there in two packages.

### `…configurationprocessor.support` — source-model SPI and decisions

| Type | Role |
|---|---|
| `SourceType` | Source-model-neutral view of a class/interface: `getQualifiedName()`, `isEnum()`, `getEnclosingType()`, `getSuperType()`. |
| `NestedTypeDetector` | The "is this property a nested group?" decision: enum / `@ConfigurationProperties` / `@NestedConfigurationProperty` short-circuits, then the cycle check and the same-top-level-type check, walking the hierarchy through `SourceType`. |
| `SourceConstructor` | Source-model-neutral view of a constructor: `hasParameters()`, `isPrivate()`, `hasAutowiredAnnotation()`, `hasConstructorBindingAnnotation()`. |
| `BindConstructorResolver` | Selects the bind constructor: deduce a single eligible constructor first (has parameters, not `@Autowired`, not a private constructor of a member type); otherwise use the `@ConstructorBinding`-annotated ones — one → constructor binding, none → JavaBean, more than one → no properties. Returns a `Resolution` (`CONSTRUCTOR_BOUND` / `JAVA_BEAN` / `NONE`). |
| `ExcludedTypes` | The fixed set of fully qualified type names that can never be a property or nested group. |
| `ConventionUtils` | Dashed-case naming convention (pre-existing). |

### `…configurationprocessor.metadata` — model, collector, factories

| Type | Role |
|---|---|
| `ConfigurationMetadata` | The metadata model. Shared helpers `removeIgnored()` and `mergedWith(additional)` centralise the "remove ignored items" and "copy + merge additional metadata" operations both processors need. |
| `MetadataCollector` | Source-model-neutral accumulation of items/hints during processing (`add`, `add(item, onConflict)`, `addIfAbsent`, `hasSimilarGroup`, `getMetadata`). Now public; both processors collect through it. |
| `EndpointMetadata` | Builds the metadata an actuator endpoint contributes (the group, the `access` property, and the optional `cache.time-to-live` property) from plain values. |
| `ItemMetadata`, `ItemHint`, `ItemDeprecation`, `ItemIgnore`, `JsonMarshaller` | The metadata model and its JSON serialization (pre-existing). |

## Thin per-model adapters

Each processor provides small adapters that expose its own model through the SPI, then
calls the shared code.

**Annotation processor (`javax.lang.model`)**

- `ElementSourceType` implements `SourceType` over a `TypeElement`.
- `ElementSourceConstructor` implements `SourceConstructor` over an `ExecutableElement`.
- `PropertyDescriptor.isNested(...)` delegates to `NestedTypeDetector`;
  `PropertyDescriptorResolver.resolve(...)` delegates to `BindConstructorResolver`;
  the processor collects through `MetadataCollector` and builds endpoints with
  `EndpointMetadata`.

**Symbol processor (KSP)**

- `DeclarationSourceType` implements `SourceType` over a `KSClassDeclaration`.
- `KspSourceConstructor` implements `SourceConstructor` over a `KSFunctionDeclaration`.
- `PropertyDescriptor.isNested(...)`, `PropertyDescriptorResolver.resolve(...)`,
  `EndpointMetadataResolver`, and `ConfigurationMetadataSymbolProcessor` delegate to the
  same shared classes.

The result: the nesting decision, the bind-constructor selection, the exclusion list,
the endpoint metadata shape, and the metadata accumulation/merge exist **once**, in
Java. The KSP module only translates Kotlin declarations into the SPI and reads Kotlin
specifics (KDoc, nullability, JVM names).

## The model-specific boundary (intentionally not shared)

The following genuinely depend on the source model or diverge in behaviour, so they stay
in each processor:

- **Type-name rendering** — KSP `JavaTypeNameResolver` walks a `KSType` and maps Kotlin
  types to their JVM names (`kotlin.Int` → `java.lang.Integer`); the annotation
  processor renders a `TypeMirror` with a `SimpleTypeVisitor8`. There is no common
  traversal, and the Kotlin→Java mapping table is meaningful only for KSP.
- **Member discovery** — the annotation processor discovers properties by matching
  `getX`/`isX`/`setX` methods against fields and record components; KSP reads Kotlin
  properties directly. Forcing a shared model would mean synthesising fake accessors.
- **Annotation access** — `KSAnnotation` vs `AnnotationMirror` have no common surface.
- **Default-value coercion** — the annotation processor reads initializers from the
  compiler trees and coerces by throwing on error; KSP cannot read initializers and logs
  instead. Behaviour and inputs differ.
- **Deprecation detection** — different annotation sets (`kotlin.Deprecated` for KSP; the
  annotation processor's `@Deprecated` handling plus its deprecation excludes).
- **Output** — the annotation processor writes through the `Filer`; KSP writes through
  the `CodeGenerator` and reads additional metadata from configured directories because
  KSP gives no access to module resources.

## Adding a third backend

To support another source model, provide implementations of `SourceType` and
`SourceConstructor`, translate that model into the metadata `ItemMetadata`, collect
through `MetadataCollector`, and reuse `NestedTypeDetector`, `BindConstructorResolver`,
`ExcludedTypes`, and `EndpointMetadata`. Only the model-specific concerns listed above
need a new implementation.
