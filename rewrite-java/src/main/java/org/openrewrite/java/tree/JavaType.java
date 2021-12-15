/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.tree;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.openrewrite.internal.lang.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@ref")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@c")
public interface JavaType {
    @JsonProperty("@c")
    default String getJacksonPolymorphicTypeTag() {
        return getClass().getName();
    }

    @Nullable
    default UUID getManagedReference() {
        return null;
    }

    default JavaType withManagedReference(UUID id) {
        return this;
    }

    /**
     * Return a JavaType for the specified string.
     * The string is expected to be either a primitive type like "int" or a fully-qualified-class name like "java.lang.String"
     */
    static JavaType buildType(String typeName) {
        Primitive primitive = Primitive.fromKeyword(typeName);
        if (primitive != null) {
            return primitive;
        } else {
            return Class.build(typeName);
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    @With
    class MultiCatch implements JavaType {
        List<JavaType> throwableTypes;
    }

    abstract class FullyQualified implements JavaType {

        public abstract String getFullyQualifiedName();

        public abstract FullyQualified withFullyQualifiedName(String fullyQualifiedName);

        public abstract List<FullyQualified> getAnnotations();

        public abstract boolean hasFlags(Flag... test);

        public abstract Set<Flag> getFlags();

        public abstract List<FullyQualified> getInterfaces();

        public abstract Kind getKind();

        public abstract List<Variable> getMembers();

        public abstract List<Method> getMethods();

        @Nullable
        public abstract FullyQualified getOwningClass();

        @Nullable
        public abstract FullyQualified getSupertype();

        public abstract List<Variable> getVisibleSupertypeMembers();

        public String getClassName() {
            AtomicBoolean dropWhile = new AtomicBoolean(false);
            return Arrays.stream(getFullyQualifiedName().split("\\."))
                    .filter(part -> {
                        dropWhile.set(dropWhile.get() || !Character.isLowerCase(part.charAt(0)));
                        return dropWhile.get();
                    })
                    .collect(joining("."));
        }

        public String getPackageName() {
            AtomicBoolean takeWhile = new AtomicBoolean(true);
            return Arrays.stream(getFullyQualifiedName().split("\\."))
                    .filter(part -> {
                        takeWhile.set(takeWhile.get() && !Character.isUpperCase(part.charAt(0)));
                        return takeWhile.get();
                    })
                    .collect(joining("."));
        }

        public boolean isAssignableTo(String fullyQualifiedName) {
            return getFullyQualifiedName().equals(fullyQualifiedName) ||
                    getInterfaces().stream().anyMatch(anInterface -> anInterface.isAssignableTo(fullyQualifiedName))
                    || (getSupertype() != null && getSupertype().isAssignableTo(fullyQualifiedName));
        }

        public boolean isAssignableFrom(@Nullable JavaType type) {
            // TODO This does not take into account type parameters.
            if (type instanceof FullyQualified) {
                FullyQualified clazz = (FullyQualified) type;
                return getFullyQualifiedName().equals(clazz.getFullyQualifiedName()) ||
                        isAssignableFrom(clazz.getSupertype()) ||
                        clazz.getInterfaces().stream().anyMatch(this::isAssignableFrom);
            } else if (type instanceof GenericTypeVariable) {
                GenericTypeVariable generic = (GenericTypeVariable) type;
                for (JavaType bound : generic.getBounds()) {
                    if (isAssignableFrom(bound)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public enum Kind {
            Class,
            Enum,
            Interface,
            Annotation
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    class Class extends FullyQualified {
        public static final Class CLASS = new Class(null, Flag.Public.getBitMask(), "java.lang.Class", Kind.Class,
                null, null, null, null, null, null);
        public static final Class ENUM = new Class(null, Flag.Public.getBitMask(), "java.lang.Enum", Kind.Class,
                null, null, null, null, null, null);

        @With
        @Nullable
        UUID managedReference;

        @With(AccessLevel.NONE)
        long flagsBitMap;

        @With
        String fullyQualifiedName;

        @With
        Kind kind;

        @With
        @Nullable
        @NonFinal
        FullyQualified supertype;

        @With
        @Nullable
        @NonFinal
        FullyQualified owningClass;

        @Nullable
        @NonFinal
        List<FullyQualified> annotations;

        public List<FullyQualified> getAnnotations() {
            return annotations == null ? emptyList() : annotations;
        }

        public JavaType.Class withAnnotations(@Nullable List<FullyQualified> annotations) {
            if (annotations != null && annotations.isEmpty()) {
                annotations = null;
            }
            if (annotations == this.annotations) {
                return this;
            }
            return new JavaType.Class(this.managedReference, this.flagsBitMap, this.fullyQualifiedName, this.kind, this.supertype,
                    this.owningClass, annotations, this.interfaces, this.members, this.methods);
        }

        @Nullable
        @NonFinal
        List<FullyQualified> interfaces;

        public List<FullyQualified> getInterfaces() {
            return interfaces == null ? emptyList() : interfaces;
        }

        public JavaType.Class withInterfaces(@Nullable List<FullyQualified> interfaces) {
            if (interfaces != null && interfaces.isEmpty()) {
                interfaces = null;
            }
            if (interfaces == this.interfaces) {
                return this;
            }
            return new JavaType.Class(this.managedReference, this.flagsBitMap, this.fullyQualifiedName, this.kind, this.supertype,
                    this.owningClass, this.annotations, interfaces, this.members, this.methods);
        }

        @Nullable
        @NonFinal
        List<Variable> members;

        public List<Variable> getMembers() {
            return members == null ? emptyList() : members;
        }

        public JavaType.Class withMembers(@Nullable List<Variable> members) {
            if (members != null && members.isEmpty()) {
                members = null;
            }
            if (members == this.members) {
                return this;
            }
            return new JavaType.Class(this.managedReference, this.flagsBitMap, this.fullyQualifiedName, this.kind, this.supertype,
                    this.owningClass, this.annotations, this.interfaces, members, this.methods);
        }

        @Nullable
        @NonFinal
        List<Method> methods;

        public List<Method> getMethods() {
            return methods == null ? emptyList() : methods;
        }

        public JavaType.Class withMethods(@Nullable List<Method> methods) {
            if (methods != null && methods.isEmpty()) {
                methods = null;
            }
            if (methods == this.methods) {
                return this;
            }
            return new JavaType.Class(this.managedReference, this.flagsBitMap, this.fullyQualifiedName, this.kind, this.supertype,
                    this.owningClass, this.annotations, this.interfaces, this.members, methods);
        }

        @Override
        public boolean hasFlags(Flag... test) {
            return Flag.hasFlags(flagsBitMap, test);
        }

        @Override
        public Set<Flag> getFlags() {
            return Flag.bitMapToFlags(flagsBitMap);
        }

        /**
         * Build a class type only from the class' fully qualified name. Since we are not providing any member, type parameter,
         * interface, or supertype information, this fully qualified name could potentially match on more than one version of
         * the class found in the type cache. This method will simply pick one of them, because there is no way of selecting
         * between the versions of the class based solely on the fully qualified class name.
         *
         * @param fullyQualifiedName The fully qualified name of the class to build
         * @return Any class found in the type cache
         */
        public static Class build(String fullyQualifiedName) {
            Class owningClass = null;

            int firstClassNameIndex = 0;
            int lastDot = 0;
            char[] fullyQualifiedNameChars = fullyQualifiedName.toCharArray();
            char prev = ' ';
            for (int i = 0; i < fullyQualifiedNameChars.length; i++) {
                char c = fullyQualifiedNameChars[i];

                if (firstClassNameIndex == 0 && prev == '.' && Character.isUpperCase(c)) {
                    firstClassNameIndex = i;
                } else if (c == '.') {
                    lastDot = i;
                }
                prev = c;
            }

            if (lastDot > firstClassNameIndex) {
                owningClass = build(fullyQualifiedName.substring(0, lastDot));
            }

            return new JavaType.Class(null, 1, fullyQualifiedName, Kind.Class, null, owningClass,
                    emptyList(), emptyList(), emptyList(), emptyList());
        }

        public List<Variable> getVisibleSupertypeMembers() {
            List<Variable> members = new ArrayList<>();
            if (this.supertype != null) {
                for (Variable member : this.supertype.getMembers()) {
                    if (!member.hasFlags(Flag.Private)) {
                        members.add(member);
                    }
                }
                members.addAll(supertype.getVisibleSupertypeMembers());
            }
            return members;
        }

        /**
         * Only meant to be used by parsers to avoid infinite recursion when building Class instances.
         */
        public void unsafeSet(@Nullable FullyQualified supertype, @Nullable FullyQualified owningClass,
                              @Nullable List<FullyQualified> annotations, @Nullable List<FullyQualified> interfaces,
                              @Nullable List<Variable> members, @Nullable List<Method> methods) {
            this.supertype = supertype;
            this.owningClass = owningClass;
            this.annotations = annotations;
            this.interfaces = interfaces;
            this.members = members;
            this.methods = methods;
        }

        @Override
        public String toString() {
            return "Class{" + fullyQualifiedName + '}';
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    @With
    class Parameterized extends FullyQualified {
        @Nullable
        UUID managedReference;

        @NonFinal
        @Nullable
        FullyQualified type;

        @NonFinal
        List<JavaType> typeParameters;

        /**
         * Only meant to be used by parsers to avoid infinite recursion when building Class instances.
         */
        public void unsafeSet(FullyQualified type, List<JavaType> typeParameters) {
            this.type = type;
            this.typeParameters = typeParameters;
        }

        @Override
        public String getFullyQualifiedName() {
            return type == null ? "" : type.getFullyQualifiedName();
        }

        public FullyQualified withFullyQualifiedName(String fullyQualifiedName) {
            assert type != null;
            return type.withFullyQualifiedName(fullyQualifiedName);
        }

        @Override
        public List<FullyQualified> getAnnotations() {
            assert type != null;
            return type.getAnnotations();
        }

        @Override
        public boolean hasFlags(Flag... test) {
            assert type != null;
            return type.hasFlags(test);
        }

        @Override
        public Set<Flag> getFlags() {
            assert type != null;
            return type.getFlags();
        }

        @Override
        public List<FullyQualified> getInterfaces() {
            assert type != null;
            return type.getInterfaces();
        }

        @Override
        public Kind getKind() {
            assert type != null;
            return type.getKind();
        }

        @Override
        public List<Variable> getMembers() {
            assert type != null;
            return type.getMembers();
        }

        @Override
        public List<Method> getMethods() {
            assert type != null;
            return type.getMethods();
        }

        @Nullable
        public FullyQualified getOwningClass() {
            assert type != null;
            return type.getOwningClass();
        }

        @Override
        public FullyQualified getSupertype() {
            assert type != null;
            return type.getSupertype();
        }

        @Override
        public List<Variable> getVisibleSupertypeMembers() {
            assert type != null;
            return type.getVisibleSupertypeMembers();
        }

        @Override
        public String toString() {
            return "Parameterized{" + getFullyQualifiedName() + "}";
        }
    }

    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    @With
    class GenericTypeVariable implements JavaType {
        @Getter
        @Nullable
        UUID managedReference;

        @Getter
        String name;

        @NonFinal
        @Nullable
        List<JavaType> bounds;

        public List<JavaType> getBounds() {
            assert bounds != null;
            return bounds;
        }

        public void unsafeSet(@Nullable List<JavaType> bounds) {
            this.bounds = bounds;
        }

        @Override
        public String toString() {
            return typeToString(this);
        }

        private static String typeToString(JavaType type) {
            if (type instanceof GenericTypeVariable){
                GenericTypeVariable generic = (GenericTypeVariable) type;
                StringBuilder s = new StringBuilder("GenericTypeVariable{" + generic.name + " extends ");
                if (generic.bounds == null || generic.bounds.isEmpty()) {
                    s.append("<no bounds>");
                } else {
                    StringJoiner b = new StringJoiner(" & ");
                    for (JavaType bound : generic.bounds) {
                        b.add(typeToString(bound));
                    }
                    s.append(b);
                }
                s.append('}');
                return s.toString();
            } else if (type instanceof FullyQualified) {
                return ((FullyQualified) type).getFullyQualifiedName();
            }
            return "";
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    @With
    class Array implements JavaType {
        JavaType elemType;

        @Override
        public String toString() {
            return "Array{" + elemType + "}";
        }
    }

    enum Primitive implements JavaType {
        Boolean,
        Byte,
        Char,
        Double,
        Float,
        Int,
        Long,
        Short,
        Void,
        String,
        None,
        Wildcard,
        Null;

        @Nullable
        public static Primitive fromKeyword(String keyword) {
            switch (keyword) {
                case "boolean":
                    return Boolean;
                case "byte":
                    return Byte;
                case "char":
                    return Char;
                case "double":
                    return Double;
                case "float":
                    return Float;
                case "int":
                    return Int;
                case "long":
                    return Long;
                case "short":
                    return Short;
                case "void":
                    return Void;
                case "String":
                    return String;
                case "*":
                    return Wildcard;
                case "null":
                    return Null;
                case "":
                    return None;
            }
            return null;
        }

        public String getKeyword() {
            switch (this) {
                case Boolean:
                    return "boolean";
                case Byte:
                    return "byte";
                case Char:
                    return "char";
                case Double:
                    return "double";
                case Float:
                    return "float";
                case Int:
                    return "int";
                case Long:
                    return "long";
                case Short:
                    return "short";
                case Void:
                    return "void";
                case String:
                    return "String";
                case Wildcard:
                    return "*";
                case Null:
                    return "null";
                case None:
                default:
                    return "";
            }
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@ref")
    class Method implements JavaType {
        @With(AccessLevel.PRIVATE)
        long flagsBitMap;

        @With
        @NonFinal
        FullyQualified declaringType;

        @With
        String name;

        @Nullable
        List<String> paramNames;

        @With
        @NonFinal
        @Nullable
        JavaType.Method.Signature genericSignature;

        @With
        @NonFinal
        @Nullable
        JavaType.Method.Signature resolvedSignature;

        @NonFinal
        @Nullable
        List<FullyQualified> thrownExceptions;

        @NonFinal
        @Nullable
        List<FullyQualified> annotations;

        public void unsafeSet(FullyQualified declaringType,
                              @Nullable JavaType.Method.Signature genericSignature,
                              @Nullable JavaType.Method.Signature resolvedSignature,
                              @Nullable List<FullyQualified> thrownExceptions,
                              @Nullable List<FullyQualified> annotations) {
            this.declaringType = declaringType;
            this.genericSignature = genericSignature;
            this.resolvedSignature = resolvedSignature;
            this.thrownExceptions = thrownExceptions != null && thrownExceptions.isEmpty() ? null : thrownExceptions;
            this.annotations = annotations != null && annotations.isEmpty() ? null : annotations;
        }

        public List<String> getParamNames() {
            return paramNames == null ? emptyList() : paramNames;
        }

        public JavaType.Method withParamNames(@Nullable List<String> paramNames) {
            if (paramNames != null && paramNames.isEmpty()) {
                paramNames = null;
            }
            if (paramNames == this.paramNames) {
                return this;
            }
            return new JavaType.Method(this.flagsBitMap, this.declaringType, this.name, paramNames,
                    this.genericSignature, this.resolvedSignature, this.thrownExceptions, this.annotations);
        }

        public List<FullyQualified> getThrownExceptions() {
            return thrownExceptions == null ? emptyList() : thrownExceptions;
        }

        public JavaType.Method withThrownExceptions(@Nullable List<FullyQualified> exceptions) {
            if (exceptions != null && exceptions.isEmpty()) {
                exceptions = null;
            }
            if (exceptions == this.thrownExceptions) {
                return this;
            }
            return new JavaType.Method(this.flagsBitMap, this.declaringType, this.name, this.paramNames, this.genericSignature,
                    this.resolvedSignature, exceptions, this.annotations);
        }

        public List<FullyQualified> getAnnotations() {
            return annotations == null ? emptyList() : annotations;
        }

        public JavaType.Method withAnnotations(@Nullable List<FullyQualified> annotations) {
            if (annotations != null && annotations.isEmpty()) {
                annotations = null;
            }
            if (annotations == this.annotations) {
                return this;
            }
            return new JavaType.Method(this.flagsBitMap, this.declaringType, this.name, this.paramNames, this.genericSignature,
                    this.resolvedSignature, this.thrownExceptions, annotations);
        }

        @Value
        @With
        public static class Signature {
            @Nullable
            JavaType returnType;

            List<JavaType> paramTypes;
        }

        public boolean hasFlags(Flag... test) {
            return Flag.hasFlags(flagsBitMap, test);
        }

        public Set<Flag> getFlags() {
            return Flag.bitMapToFlags(flagsBitMap);
        }

        public Method withFlags(Set<Flag> flags) {
            return withFlagsBitMap(Flag.flagsToBitMap(flags));
        }

        @Override
        public String toString() {
            return "Method{" + (declaringType == null ? "<unknown>" : declaringType) + "#" + name + "(" + String.join(", ", (paramNames == null ? emptyList() : paramNames)) + ")}";
        }
    }

    @Getter
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    @AllArgsConstructor
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@ref")
    class Variable implements JavaType {
        @With(AccessLevel.NONE)
        long flagsBitMap;

        @With
        String name;

        @With
        @NonFinal
        FullyQualified owner;

        @With
        @NonFinal
        @Nullable
        JavaType type;

        @NonFinal
        @Nullable
        List<FullyQualified> annotations;

        public List<FullyQualified> getAnnotations() {
            return annotations == null ? emptyList() : annotations;
        }

        public JavaType.Variable withAnnotations(@Nullable List<FullyQualified> annotations) {
            if (annotations != null && annotations.isEmpty()) {
                annotations = null;
            }
            if (this.annotations == annotations) {
                return this;
            }
            return new JavaType.Variable(this.flagsBitMap, this.name, this.owner, this.type, annotations);
        }

        public boolean hasFlags(Flag... test) {
            return Flag.hasFlags(flagsBitMap, test);
        }

        public Set<Flag> getFlags() {
            return Flag.bitMapToFlags(flagsBitMap);
        }

        public void unsafeSet(FullyQualified owner, @Nullable JavaType type,
                              @Nullable List<FullyQualified> annotations) {
            this.owner = owner;
            this.type = type;
            this.annotations = annotations;
        }

        @Override
        public String toString() {
            return "Variable{" + (owner == null ? "<unknown>" : owner) + "#" + name + "}";
        }
    }
}
