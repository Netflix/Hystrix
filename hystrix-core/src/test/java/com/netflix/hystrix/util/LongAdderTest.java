/*
 * Copyright 2018 Diffblue Limited
 *
 * Diffblue Limited licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.util;

import com.netflix.hystrix.util.LongAdder;
import com.netflix.hystrix.util.Striped64.Cell;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class LongAdderTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void doubleValueOutputZero() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final double retval = objectUnderTest.doubleValue();

    // Assert result
    Assert.assertEquals(0.0, retval, 0.0);
  }

  @Test
  public void floatValueOutputZero() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final float retval = objectUnderTest.floatValue();

    // Assert result
    Assert.assertEquals(0.0f, retval, 0.0f);
  }

  @Test
  public void intValueOutputZero() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final int retval = objectUnderTest.intValue();

    // Assert result
    Assert.assertEquals(0, retval);
  }

  @Test
  public void longValueOutputZero() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final long retval = objectUnderTest.longValue();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void resetOutputVoid() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    objectUnderTest.reset();

    // Method returns void, testing that no exception is thrown
  }

  @Test
  public void sumOutputZero() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final long retval = objectUnderTest.sum();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void sumOutputZero2() throws InvocationTargetException {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();
    objectUnderTest.busy = 0;
    final Cell[] cellArray = {};
    objectUnderTest.cells = cellArray;
    objectUnderTest.base = 0L;

    // Act
    final long retval = objectUnderTest.sum();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void sumOutputZero3() throws InvocationTargetException {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();
    objectUnderTest.busy = 0;
    final Cell[] cellArray = {null};
    objectUnderTest.cells = cellArray;
    objectUnderTest.base = 0L;

    // Act
    final long retval = objectUnderTest.sum();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void sumThenResetOutputZero() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final long retval = objectUnderTest.sumThenReset();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void sumThenResetOutputZero2() throws InvocationTargetException {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();
    objectUnderTest.busy = 0;
    final Cell[] cellArray = {};
    objectUnderTest.cells = cellArray;
    objectUnderTest.base = 0L;

    // Act
    final long retval = objectUnderTest.sumThenReset();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void sumThenResetOutputZero3() throws InvocationTargetException {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();
    objectUnderTest.busy = 0;
    final Cell[] cellArray = {null};
    objectUnderTest.cells = cellArray;
    objectUnderTest.base = 0L;

    // Act
    final long retval = objectUnderTest.sumThenReset();

    // Assert result
    Assert.assertEquals(0L, retval);
  }

  @Test
  public void toStringOutputNotNull() {

    // Arrange
    final LongAdder objectUnderTest = new LongAdder();

    // Act
    final String retval = objectUnderTest.toString();

    // Assert result
    Assert.assertEquals("0", retval);
  }
}
