/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.types3;

import com.foundationdb.server.types3.pvalue.PUnderlying;
import com.foundationdb.server.types3.pvalue.PValueCacher;
import com.foundationdb.server.types3.pvalue.PValueTargets;
import com.foundationdb.server.types3.texpressions.Serialization;
import com.foundationdb.server.types3.texpressions.SerializeAs;
import com.foundationdb.sql.types.DataTypeDescriptor;
import com.foundationdb.server.types3.pvalue.PValueSource;
import com.foundationdb.server.types3.pvalue.PValueTarget;
import com.foundationdb.util.AkibanAppender;
import com.foundationdb.util.ArgumentValidation;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedBytes;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class TClass {

    protected abstract DataTypeDescriptor dataTypeDescriptor(TInstance instance);

    public abstract void fromObject (TExecutionContext contextForErrors, PValueSource in, PValueTarget out);

    public abstract TCast castToVarchar();
    public abstract TCast castFromVarchar();

    public abstract TClass widestComparable();

    public void selfCast(TExecutionContext context,
                         TInstance sourceInstance, PValueSource source, TInstance targetInstance, PValueTarget target) {
        PValueTargets.copyFrom(source, target);
    }

    public boolean normalizeInstancesBeforeComparison() {
        return false;
    }

    public static boolean comparisonNeedsCasting(TInstance left, TInstance right) {
        if (left == null || right == null)
            return true;
        TClass leftClass = left.typeClass();
        TClass rightClass = right.typeClass();
        if (leftClass.normalizeInstancesBeforeComparison())
            return !left.equalsExcludingNullable(right);
        else if (leftClass.getClass() == rightClass.getClass())
            return !leftClass.compatibleForCompare(rightClass);
        else
            return true;
    }

    public boolean compatibleForCompare(TClass other) {
        return (this == other);
    }

    public static int compare(TInstance instanceA, PValueSource sourceA, TInstance instanceB, PValueSource sourceB) {
        if (comparisonNeedsCasting(instanceA, instanceB))
            throw new IllegalArgumentException("can't compare " + instanceA + " and " + instanceB);

        if (sourceA.isNull())
            return sourceB.isNull() ? 0 : -1;
        if (sourceB.isNull())
            return 1;
        return instanceA.typeClass().doCompare(instanceA, sourceA, instanceB, sourceB);
    }

    /**
     * Compares two values, assuming neither is null. The call site (<tt>TClass.compare</tt>) will handle the case
     * that either or both sources is null.
     * @param instanceA the first operand's instance
     * @param sourceA the first operand's value, which will not represent a null PValueSource
     * @param instanceB the second operand's instance
     * @param sourceB the second operand's value, which will not represent a null PValueSource
     * @return -1 if sourceA is less than sourceB; 0 if they're equal; 1 if sourceA is greater than sourceB
     * @see TClass#compare(TInstance, PValueSource, TInstance, PValueSource)
     */
    protected int doCompare(TInstance instanceA, PValueSource sourceA, TInstance instanceB, PValueSource sourceB) {
        if (sourceA.hasCacheValue() && sourceB.hasCacheValue()) {
            Object objectA = sourceA.getObject();
            if (objectA instanceof Comparable<?>) {
                // assume objectA and objectB are of the same class. If it's comparable, use that
                @SuppressWarnings("unchecked")
                Comparable<Object> comparableA = (Comparable<Object>) objectA;
                return comparableA.compareTo(sourceB.getObject());
            }
        }
        switch (TInstance.pUnderlying(sourceA.tInstance())) {
        case BOOL:
            return Booleans.compare(sourceA.getBoolean(), sourceB.getBoolean());
        case INT_8:
            return sourceA.getInt8() - sourceB.getInt8();
        case INT_16:
            return sourceA.getInt16() - sourceB.getInt16();
        case UINT_16:
            return sourceA.getUInt16() - sourceB.getUInt16();
        case INT_32:
            return sourceA.getInt32() - sourceB.getInt32();
        case INT_64:
            return Longs.compare(sourceA.getInt64(), sourceB.getInt64());
        case FLOAT:
            return Floats.compare(sourceA.getFloat(), sourceB.getFloat());
        case DOUBLE:
            return Doubles.compare(sourceA.getDouble(), sourceB.getDouble());
        case BYTES:
            return UnsignedBytes.lexicographicalComparator().compare(sourceA.getBytes(), sourceB.getBytes());
        case STRING:
            return sourceA.getString().compareTo(sourceB.getString());
        default:
            throw new AssertionError(sourceA.tInstance());
        }
    }

    final void writeCanonical(PValueSource in, TInstance typeInstance, PValueTarget out) {
        if (in.isNull())
            out.putNull();
        else
            getPValueIO().copyCanonical(in, typeInstance, out);
    }

    protected Object attributeToObject(int attributeIndex, int value) {
        return value;
    }

    public final boolean attributeIsPhysical(Attribute attribute) {
        return attributeIsPhysical(attribute.ordinal());
    }

    protected abstract boolean attributeIsPhysical(int attributeIndex);

    public void attributeToString(int attributeIndex, long value, StringBuilder output) {
        output.append(value);
    }

    protected PValueIO getPValueIO() {
        return defaultPValueIO;
    }

    public abstract TInstance instance(boolean nullable);

    public TInstance instance(int arg0, boolean nullable)
    {
        return createInstance(1, arg0, EMPTY, EMPTY, EMPTY, nullable);
    }

    public TInstance instance(int arg0, int arg1, boolean nullable)
    {
        return createInstance(2, arg0, arg1, EMPTY, EMPTY, nullable);
    }

    public TInstance instance(int arg0, int arg1, int arg2, boolean nullable)
    {
        return createInstance(3, arg0, arg1, arg2, EMPTY, nullable);
    }

    public TInstance instance(int arg0, int arg1, int arg2, int arg3, boolean nullable)
    {
        return createInstance(4, arg0, arg1, arg2, arg3, nullable);
    }

    final void writeCollating(PValueSource inValue, TInstance inInstance, PValueTarget out) {
        if (inValue.isNull())
            out.putNull();
        else
            getPValueIO().writeCollating(inValue, inInstance, out);
    }

    final void readCollating(PValueSource inValue, TInstance inInstance, PValueTarget out) {
        if (inValue.isNull())
            out.putNull();
        else
            getPValueIO().readCollating(inValue, inInstance, out);
    }

    public TInstance pickInstance(TInstance left, TInstance right) {
        if (left.typeClass() != TClass.this || right.typeClass() != TClass.this)
            throw new IllegalArgumentException("can't combine " + left + " and " + right + " using " + this);

        return doPickInstance(left, right, left.nullability() || right.nullability());
    }

    public PUnderlying underlyingType() {
        return pUnderlying;
    }

    int nAttributes() {
        return attributeSerializations.size();
    }

    public Collection<? extends Attribute> attributes() {
        return attributeSerializations.keySet();
    }

    public Map<? extends Attribute, ? extends Serialization> attributeSerializations() {
        return attributeSerializations;
    }

    public TName name() {
        return name;
    }

    public int internalRepresentationVersion() {
        return internalRepVersion;
    }

    public int serializationVersion() {
        return serializationVersion;
    }

    public boolean hasFixedSerializationSize() {
        return serializationSize >= 0;
    }

    public int fixedSerializationSize() {
        assert hasFixedSerializationSize() : this + " has no fixed serialization size";
        return serializationSize;
    }

    // object interface

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TClass other = (TClass) o;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name.toString();
    }

    void format(TInstance instance, PValueSource source, AkibanAppender out) {
        if (source.isNull())
            out.append("NULL");
        else
            formatter.format(instance, source, out);
    }

    void formatAsLiteral(TInstance instance, PValueSource source, AkibanAppender out) {
        if (source.isNull())
            out.append("NULL");
        else
            formatter.formatAsLiteral(instance, source, out);
    }

    void formatAsJson(TInstance instance, PValueSource source, AkibanAppender out) {
        if (source.isNull())
            out.append("null");
        else
            formatter.formatAsJson(instance, source, out);
    }

    public Object formatCachedForNiceRow(PValueSource source) {
        return source.getObject();
    }

    // for use by subclasses
    protected abstract TInstance doPickInstance(TInstance left, TInstance right, boolean suggestedNullability);
    protected abstract void validate(TInstance instance);

    // for use by this class

    protected TInstance createInstanceNoArgs(boolean nullable) {
        return createInstance(0, EMPTY, EMPTY, EMPTY, EMPTY, nullable);
    }

    protected TInstance createInstance(int nAttrs, int attr0, int attr1, int attr2, int attr3, boolean nullable) {
        return TInstance.create(this, enumClass, nAttrs, attr0, attr1, attr2, attr3, nullable);
    }

    public PValueCacher cacher() {
        return null;
    }

    // state

     protected <A extends Enum<A> & Attribute> TClass(TName name,
            Class<A> enumClass,
            TClassFormatter formatter,
            int internalRepVersion, int serializationVersion, int serializationSize,
            PUnderlying pUnderlying)
     {

         ArgumentValidation.notNull("name", name);
         this.name = name;
         this.formatter = formatter;
         this.internalRepVersion = internalRepVersion;
         this.serializationVersion = serializationVersion;
         this.serializationSize = serializationSize < 0 ? -1 : serializationSize; // normalize all negative numbers
         this.pUnderlying = pUnderlying;
         this.attributeSerializations = serializationsFor(enumClass);

         this.enumClass = enumClass;
         for (Attribute attribute : attributeSerializations.keySet())
         {
             String attrValue = attribute.name();
             if (!VALID_ATTRIBUTE_PATTERN.matcher(attrValue).matches())
                 throw new IllegalNameException(attribute + " in " + name + " has invalid name: " + attrValue);
         }
     }

    private static <A extends Enum<A> & Attribute> Map<A, Serialization> serializationsFor(Class<A> enumClass) {
        EnumSet<Serialization> seenSerializations = EnumSet.noneOf(Serialization.class);
        Map<A, Serialization> serializationsMap = new EnumMap<>(enumClass);
        for (A attribute : enumClass.getEnumConstants()) {
            Field attributeField;
            try {
                attributeField = enumClass.getField(attribute.name());
            } catch (NoSuchFieldException e) {
                throw new AssertionError(e);
            }
            SerializeAs serializeAs = attributeField.getAnnotation(SerializeAs.class);
            Serialization serialization;
            if (serializeAs != null) {
                serialization = serializeAs.value();
                if (!seenSerializations.add(serialization))
                    throw new RuntimeException("duplicate serialization policy in " + enumClass);
            }
            else {
                serialization = null;
            }
            serializationsMap.put(attribute, serialization);
        }
        return Collections.unmodifiableMap(serializationsMap);
    }

    protected <A extends Enum<A> & Attribute> TClass(TBundleID bundle,
            String name,
            Enum<?> category,
            Class<A> enumClass,
            TClassFormatter formatter,
            int internalRepVersion, int serializationVersion, int serializationSize,
            PUnderlying pUnderlying)
     {
        this(new TName(bundle, name, category),
                enumClass,
                formatter,
                internalRepVersion, serializationVersion, serializationSize,
                pUnderlying);

     }
    private final TName name;
    private final Class<?> enumClass;
    protected final TClassFormatter formatter;
    private final Map<? extends Attribute, Serialization> attributeSerializations;
    private final int internalRepVersion;
    private final int serializationVersion;
    private final int serializationSize;

    private final PUnderlying pUnderlying;

    private static final Pattern VALID_ATTRIBUTE_PATTERN = Pattern.compile("[a-zA-Z]\\w*");

    private static final int EMPTY = -1;

    private static final PValueIO defaultPValueIO = new SimplePValueIO() {
        @Override
        protected void copy(PValueSource in, TInstance typeInstance, PValueTarget out) {
            PValueTargets.copyFrom(in, out);
        }
    };
}