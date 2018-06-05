/*
 * Copyright (c) 2018 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.html.
 */

package org.eclipse.milo.opcua.sdk.server.events.conversions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

abstract class AbstractConversionTest<S> {

    @Test
    public void testNullConversion() {
        for (BuiltinDataType targetType : BuiltinDataType.values()) {
            assertNull(convert(null, targetType, true));
            assertNull(convert(null, targetType, false));
        }
    }

    @Test
    public void testAllConversions() {
        for (BuiltinDataType targetType : BuiltinDataType.values()) {
            Conversion[] conversions = getConversions(targetType);

            for (Conversion conversion : conversions) {
                S fromValue = getSourceClass().cast(conversion.fromValue);
                Object targetValue = conversion.targetValue;

                ConversionType conversionType = getConversionType(conversion.targetType);
                Object convertedValue = convert(fromValue, targetType, conversionType == ConversionType.IMPLICIT);

                System.out.println(String.format(
                    "[%s] fromValue=%s targetType=%s targetValue=%s",
                    conversionType, fromValue, targetType, targetValue));

                assertEquals(convertedValue, targetValue);
            }
        }
    }

    @Test
    public void testExplicitConversionsCalledImplicitlyAreNull() {
        for (BuiltinDataType targetType : BuiltinDataType.values()) {
            Conversion[] conversions = getConversions(targetType);

            for (Conversion conversion : conversions) {
                ConversionType conversionType = getConversionType(conversion.targetType);

                if (conversionType == ConversionType.EXPLICIT) {
                    S fromValue = getSourceClass().cast(conversion.fromValue);

                    Object convertedValue = convert(fromValue, targetType, true);

                    assertNull(convertedValue);
                }
            }
        }
    }

    @Test
    public void testImplicitConversionsCalledExplicitly() {
        for (BuiltinDataType targetType : BuiltinDataType.values()) {
            Conversion[] conversions = getConversions(targetType);

            for (Conversion conversion : conversions) {
                ConversionType conversionType = getConversionType(conversion.targetType);

                if (conversionType == ConversionType.IMPLICIT) {
                    S fromValue = getSourceClass().cast(conversion.fromValue);

                    Object convertedValue = convert(fromValue, targetType, false);

                    System.out.println(String.format(
                        "[%s] fromValue=%s targetType=%s targetValue=%s",
                        conversionType, fromValue, targetType, conversion.targetValue));

                    assertEquals(convertedValue, conversion.targetValue);
                }
            }
        }
    }

    protected Conversion c(@Nonnull S fromValue, @Nonnull Object targetValue) {
        Conversion c = new Conversion();
        c.fromValue = fromValue;
        c.targetValue = targetValue;
        c.targetType = BuiltinDataType.fromBackingClass(targetValue.getClass());
        return c;
    }

    protected Conversion c(@Nonnull S fromValue, @Nullable Object targetValue, @Nonnull BuiltinDataType targetType) {
        Conversion c = new Conversion();
        c.fromValue = fromValue;
        c.targetValue = targetValue;
        c.targetType = targetType;
        return c;
    }

    protected abstract Class<S> getSourceClass();

    public abstract Conversion[] getConversions(BuiltinDataType targetType);

    public abstract ConversionType getConversionType(BuiltinDataType targetType);

    /*
    {
        //@formatter:off
        switch (targetType) {
            case Boolean:   return ConversionType.IMPLICIT;
            case Byte:      return ConversionType.IMPLICIT;
            case Float:     return ConversionType.IMPLICIT;
            case Double:    return ConversionType.IMPLICIT;
            case Int16:     return ConversionType.IMPLICIT;
            case Int32:     return ConversionType.IMPLICIT;
            case Int64:     return ConversionType.IMPLICIT;
            case SByte:     return ConversionType.IMPLICIT;
            case String:    return ConversionType.EXPLICIT;
            case UInt16:    return ConversionType.IMPLICIT;
            case UInt32:    return ConversionType.IMPLICIT;
            case UInt64:    return ConversionType.IMPLICIT;
            default:        return ConversionType.NONE;
        }
        //@formatter:on
    }
    */

    // protected abstract Conversion[] getExplicitConversions();

    // protected abstract Conversion[] getImplicitConversions();

    protected abstract Object convert(Object fromValue, BuiltinDataType targetType, boolean implicit);

    static class Conversion {
        Object fromValue;
        Object targetValue;
        BuiltinDataType targetType;
    }

    enum ConversionType {
        EXPLICIT,
        IMPLICIT,
        NONE
    }

}