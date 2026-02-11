/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
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

package org.kinotic.continuum.idl.api.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * This is the base class for all type schemas.
 * It can be used to create {@link C3Type} from use within a Continuum IDL.
 * <p>
 * Created by navid on 2023-4-13.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ArrayC3Type.class, name = "array"),
        @JsonSubTypes.Type(value = BooleanC3Type.class, name = "boolean"),
        @JsonSubTypes.Type(value = ByteC3Type.class, name = "byte"),
        @JsonSubTypes.Type(value = CharC3Type.class, name = "char"),
        @JsonSubTypes.Type(value = DateC3Type.class, name = "date"),
        @JsonSubTypes.Type(value = DoubleC3Type.class, name = "double"),
        @JsonSubTypes.Type(value = EnumC3Type.class, name = "enum"),
        @JsonSubTypes.Type(value = FloatC3Type.class, name = "float"),
        @JsonSubTypes.Type(value = IntC3Type.class, name = "int"),
        @JsonSubTypes.Type(value = LongC3Type.class, name = "long"),
        @JsonSubTypes.Type(value = MapC3Type.class, name = "map"),
        @JsonSubTypes.Type(value = ObjectC3Type.class, name = "object"),
        @JsonSubTypes.Type(value = ReferenceC3Type.class, name = "ref"),
        @JsonSubTypes.Type(value = ShortC3Type.class, name = "short"),
        @JsonSubTypes.Type(value = StringC3Type.class, name = "string"),
        @JsonSubTypes.Type(value = UnionC3Type.class, name = "union"),
        @JsonSubTypes.Type(value = VoidC3Type.class, name = "void")
})
@JsonInclude(JsonInclude.Include.NON_EMPTY) // do not include any empty or null fields
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class C3Type {
}
