/*
 * Copyright 1997-2014 Optimatika (www.optimatika.se)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.matrix.task;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.ojalgo.FunctionalityTest;
import org.ojalgo.matrix.decomposition.MatrixDecompositionTests;
import org.ojalgo.matrix.task.DeterminantTask;

/**
 * MatrixDecompositionPackageTests
 * 
 * @author apete
 */
public abstract class MatrixTaskTests extends FunctionalityTest {

    static final boolean DEBUG = false;

    public static final List<DeterminantTask<Double>> getPrimitiveFull() {

        final ArrayList<DeterminantTask<Double>> retVal = new ArrayList<DeterminantTask<Double>>();

        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionTests.getLUPrimitive()) {
            retVal.add(tmpDeterminantTask);
        }

        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionTests.getEigenvaluePrimitiveNonsymmetric()) {
            retVal.add(tmpDeterminantTask);
        }

        //        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionPackageTests.getQRPrimitive()) {
        //            retVal.add(tmpDeterminantTask);
        //        }

        return retVal;
    }

    public static final List<DeterminantTask<Double>> getPrimitiveSymmetric() {

        final ArrayList<DeterminantTask<Double>> retVal = new ArrayList<DeterminantTask<Double>>();

        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionTests.getCholeskyPrimitive()) {
            retVal.add(tmpDeterminantTask);
        }

        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionTests.getEigenvaluePrimitiveSymmetric()) {
            retVal.add(tmpDeterminantTask);
        }

        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionTests.getLUPrimitive()) {
            retVal.add(tmpDeterminantTask);
        }

        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionTests.getEigenvaluePrimitiveNonsymmetric()) {
            retVal.add(tmpDeterminantTask);
        }

        //        for (final DeterminantTask<Double> tmpDeterminantTask : MatrixDecompositionPackageTests.getQRPrimitive()) {
        //            retVal.add(tmpDeterminantTask);
        //        }

        return retVal;
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(MatrixTaskTests.class.getPackage().getName());
        //$JUnit-BEGIN$
        suite.addTestSuite(DeterminantTest.class);
        //$JUnit-END$
        return suite;
    }

    protected MatrixTaskTests() {
        super();
    }

    protected MatrixTaskTests(final String name) {
        super(name);
    }
}