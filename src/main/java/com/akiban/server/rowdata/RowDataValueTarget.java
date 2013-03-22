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

package com.akiban.server.rowdata;

import com.akiban.qp.operator.Cursor;
import com.akiban.server.AkServerUtil;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSourceHelper;
import com.akiban.server.types.ValueTarget;
import com.akiban.util.ArgumentValidation;
import com.akiban.util.ByteSource;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class RowDataValueTarget implements ValueTarget, RowDataTarget {

    @Override
    public void bind(FieldDef fieldDef, byte[] backingBytes, int offset) {
        if(fieldDef.getType().akType() == AkType.INTERVAL_MILLIS || fieldDef.getType().akType() == AkType.INTERVAL_MONTH)
            throw new UnsupportedOperationException();
        clear();
        ArgumentValidation.notNull("fieldDef", fieldDef);
        if (offset < 0)
            throw new IllegalArgumentException("offset may not be zero");
        if (offset >= backingBytes.length)
            throw new ArrayIndexOutOfBoundsException(offset);
        this.fieldDef = fieldDef;
        this.bytes = backingBytes;
        this.offset = offset;
    }

    @Override
    public int lastEncodedLength() {
        if (lastEncodedLength < 0) {
            throw new IllegalStateException("no last recorded length available");
        }
        return lastEncodedLength;
    }

    public RowDataValueTarget() {
        clear();
    }

    // ValueTarget interface

    @Override
    public void putNull() {
        checkState(AkType.NULL);
        setNullBit();
        recordEncoded(0);
    }

    @Override
    public void putDate(long value) {
        checkState(AkType.DATE);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putDateTime(long value) {
        checkState(AkType.DATETIME);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putDecimal(BigDecimal value) {
        checkState(AkType.DECIMAL);
        recordEncoded(ConversionHelperBigDecimal.fromObject(fieldDef, value, bytes, offset));
    }

    @Override
    public void putDouble(double value) {
        checkState(AkType.DOUBLE);
        recordEncoded(encodeLong(Double.doubleToLongBits(value)));
    }

    @Override
    public void putFloat(float value) {
        checkState(AkType.FLOAT);
        recordEncoded(encodeInt(Float.floatToIntBits(value)));
    }

    @Override
    public void putInt(long value) {
        checkState(AkType.INT);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putLong(long value) {
        checkState(AkType.LONG);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putString(String value) {
        checkState(AkType.VARCHAR);
        recordEncoded(ConversionHelper.encodeString(value, bytes, offset, fieldDef));
    }

    @Override
    public void putText(String value) {
        checkState(AkType.TEXT);
        recordEncoded(ConversionHelper.encodeString(value, bytes, offset, fieldDef));
    }

    @Override
    public void putTime(long value) {
        checkState(AkType.TIME);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putTimestamp(long value) {
        checkState(AkType.TIMESTAMP);
        recordEncoded(encodeLong(value));
    }
    
    @Override
    public void putInterval_Millis(long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putInterval_Month(long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putUBigInt(BigInteger value) {
        checkState(AkType.U_BIGINT);
        assert encodableAsLong(value) : value;
        long asLong = value.longValue();
        int width = fieldDef.getMaxStorageSize();
        recordEncoded(AkServerUtil.putIntegerByWidth(bytes, offset, width, asLong));
    }

    @Override
    public void putUDouble(double value) {
        checkState(AkType.U_DOUBLE);
        // TODO call to Math.max lifted from UDoubleEncoder.fromObject. Probably doesn't belong here.
        int longBits = Math.max(encodeLong(Double.doubleToLongBits(value)), 0);
        recordEncoded(longBits);
    }

    @Override
    public void putUFloat(float value) {
        checkState(AkType.U_FLOAT);
        // TODO call to Math.max lifted from UFloatEncoder.fromObject. Probably doesn't belong here.
        int intBits = Math.max(encodeInt(Float.floatToIntBits(value)), 0);
        recordEncoded(intBits);
    }

    @Override
    public void putUInt(long value) {
        checkState(AkType.U_INT);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putVarBinary(ByteSource value) {
        checkState(AkType.VARBINARY);
        recordEncoded(
                ConversionHelper.putByteArray(
                        value.byteArray(), value.byteArrayOffset(), value.byteArrayLength(),
                        bytes, offset, fieldDef)
        );
    }

    @Override
    public void putYear(long value) {
        checkState(AkType.YEAR);
        recordEncoded(encodeLong(value));
    }

    @Override
    public void putBool(boolean value) {
        checkState(AkType.BOOL);
        recordEncoded(encodeLong(value ? 1 : 0));
    }

    @Override
    public void putResultSet(Cursor value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AkType getConversionType() {
        return fieldDef == null ? AkType.UNSUPPORTED : fieldDef.getType().akType();
    }

    // private methods
    
    private void recordEncoded(int encodedLength) {
        clear();
        lastEncodedLength = encodedLength;
    }

    private void checkState(AkType expectedType) {
        if (expectedType == AkType.NULL) {
            if (fieldDef == null) {
                throw new IllegalStateException("target is not bound");
            }
        }
        else {
            ValueSourceHelper.checkType(expectedType, getConversionType());
        }
    }

    private void clear() {
        lastEncodedLength = -1;
        bytes = null;
        offset = -1;
    }

    private int encodeInt(int value) {
        assert INT_STORAGE_SIZE == fieldDef.getMaxStorageSize() : fieldDef.getMaxStorageSize();
        return AkServerUtil.putIntegerByWidth(bytes, offset, INT_STORAGE_SIZE, value);
    }

    private int encodeLong(long value) {
        int width = fieldDef.getMaxStorageSize();
        return AkServerUtil.putIntegerByWidth(bytes, offset, width, value);
    }

    private boolean encodableAsLong(BigInteger value) {
        return value.compareTo(MAX_BIGINT) <= 0;
    }

    private void setNullBit() {
        // TODO unloop this
        int target = fieldDef.getFieldIndex();
        int fieldCount = fieldDef.getRowDef().getFieldCount();
        int offsetWithinMap = offset;
        for (int index = 0; index < fieldCount; index += 8) {
            for (int j = index; j < index + 8 && j < fieldCount; j++) {
                if (j == target) {
                    bytes[offsetWithinMap] |= (1 << j - index);
                    return;
                }
            }
            ++offsetWithinMap;
        }
        throw new AssertionError("field not found! " + fieldDef);
    }

    // object state

    private FieldDef fieldDef;
    private int lastEncodedLength;
    private byte bytes[];
    private int offset;

    // consts

    private static final int INT_STORAGE_SIZE = 4;

    /**
     * We want to encode BigInteger as long, so we require it to be smaller than (2^64) + 1
     */
    private static final BigInteger MAX_BIGINT = BigInteger.valueOf(2).pow(Long.SIZE).add(BigInteger.ONE);
}
