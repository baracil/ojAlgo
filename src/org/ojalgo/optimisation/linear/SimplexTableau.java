/*
 * Copyright 1997-2017 Optimatika (www.optimatika.se)
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
package org.ojalgo.optimisation.linear;

import static org.ojalgo.constant.PrimitiveMath.*;
import static org.ojalgo.function.PrimitiveFunction.*;

import org.ojalgo.access.Access1D;
import org.ojalgo.access.Access2D;
import org.ojalgo.access.Mutate1D;
import org.ojalgo.access.Mutate2D;
import org.ojalgo.array.Array1D;
import org.ojalgo.array.BasicArray;
import org.ojalgo.array.DenseArray;
import org.ojalgo.array.DenseArray.Factory;
import org.ojalgo.array.Primitive64Array;
import org.ojalgo.array.SparseArray;
import org.ojalgo.array.SparseArray.NonzeroView;
import org.ojalgo.array.SparseArray.SparseFactory;
import org.ojalgo.function.NullaryFunction;
import org.ojalgo.function.PrimitiveFunction;
import org.ojalgo.function.UnaryFunction;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.optimisation.linear.SimplexSolver.AlgorithmStore;
import org.ojalgo.type.IndexSelector;

abstract class SimplexTableau implements AlgorithmStore, Access2D<Double> {

    static final class DenseTableau extends SimplexTableau {

        private final PrimitiveDenseStore myTransposed;

        DenseTableau(final LinearSolver.Builder matrices) {

            super(matrices.countEqualityConstraints(), matrices.countVariables(), 0);

            final int tmpConstraintsCount = this.countConstraints();
            final int tmpVariablesCount = this.countVariables();

            final MatrixStore.LogicalBuilder<Double> tmpTableauBuilder = MatrixStore.PRIMITIVE.makeZero(1, 1);
            tmpTableauBuilder.left(matrices.getC().transpose().logical().right(MatrixStore.PRIMITIVE.makeZero(1, tmpConstraintsCount).get()).get());

            if (tmpConstraintsCount >= 1) {
                tmpTableauBuilder.above(matrices.getAE(), MatrixStore.PRIMITIVE.makeIdentity(tmpConstraintsCount).get(), matrices.getBE());
            }
            tmpTableauBuilder.below(MatrixStore.PRIMITIVE.makeZero(1, tmpVariablesCount).get(),
                    PrimitiveDenseStore.FACTORY.makeFilled(1, tmpConstraintsCount, new NullaryFunction<Double>() {

                        public double doubleValue() {
                            return ONE;
                        }

                        public Double invoke() {
                            return ONE;
                        }

                    }));
            //myTransposedTableau = (PrimitiveDenseStore) tmpTableauBuilder.build().transpose().copy();
            myTransposed = PrimitiveDenseStore.FACTORY.transpose(tmpTableauBuilder.get());
            // myTableau = LinearSolver.make(myTransposedTableau);

            for (int i = 0; i < tmpConstraintsCount; i++) {

                myTransposed.caxpy(NEG, i, tmpConstraintsCount + 1, 0);

            }

        }

        DenseTableau(final SparseTableau sparse) {

            super(sparse.countConstraints(), sparse.countProblemVariables(), sparse.countSlackVariables());

            myTransposed = sparse.transpose();
        }

        public long countColumns() {
            return myTransposed.countRows();
        }

        public long countRows() {
            return myTransposed.countColumns();
        }

        public double doubleValue(final long row, final long col) {
            return myTransposed.doubleValue(col, row);
        }

        public Double get(final long row, final long col) {
            return myTransposed.get(col, row);
        }

        @Override
        protected void pivot(final IterationPoint iterationPoint) {

            final int row = iterationPoint.row;
            final int col = iterationPoint.col;

            final double pivotElement = myTransposed.doubleValue(col, row);

            for (int i = 0; i < myTransposed.countColumns(); i++) {
                // TODO Stop updating phase 1 objective when in phase 2.
                if (i != row) {
                    final double colVal = myTransposed.doubleValue(col, i);
                    if (colVal != ZERO) {
                        myTransposed.caxpy(-colVal / pivotElement, row, i, 0);
                    }
                }
            }

            if (PrimitiveFunction.ABS.invoke(pivotElement) < ONE) {
                myTransposed.modifyColumn(0, row, DIVIDE.second(pivotElement));
            } else if (pivotElement != ONE) {
                myTransposed.modifyColumn(0, row, MULTIPLY.second(ONE / pivotElement));
            }

            this.update(iterationPoint);
        }

        @Override
        protected Array1D<Double> sliceConstraintsRHS() {
            return myTransposed.sliceRow(this.countVariablesTotally()).sliceRange(0, this.countConstraints());
        }

        @Override
        protected Array1D<Double> sliceDualVariables() {
            return myTransposed.sliceColumn(this.countConstraints()).sliceRange(this.countVariables(), this.countVariables() + this.countConstraints());
        }

        @Override
        protected Array1D<Double> sliceTableauColumn(final int col) {
            return myTransposed.sliceRow(col).sliceRange(0, this.countConstraints());
        }

        @Override
        protected Array1D<Double> sliceTableauRow(final int row) {
            return myTransposed.sliceColumn(row).sliceRange(0, this.countVariablesTotally());
        }

    }

    static final class IterationPoint {

        private boolean myPhase1 = true;

        int col;
        int row;

        IterationPoint() {
            super();
            this.reset();
        }

        boolean isPhase1() {
            return myPhase1;
        }

        boolean isPhase2() {
            return !myPhase1;
        }

        void reset() {
            row = -1;
            col = -1;
        }

        void switchToPhase2() {
            myPhase1 = false;
        }

    }

    static final class SparseTableau extends SimplexTableau {

        class ConstraintsBody implements Mutate2D {

            public void add(final long row, final long col, final double addend) {
                myRows[(int) row].add(col, addend);
                myPhase1Weights.add(col, -addend);
            }

            public void add(final long row, final long col, final Number addend) {
                this.add(row, col, addend.doubleValue());
            }

            public long countColumns() {
                return SparseTableau.this.countVariables();
            }

            public long countRows() {
                return SparseTableau.this.countConstraints();
            }

            public void set(final long row, final long col, final double value) {
                myRows[(int) row].set(col, value);
                myPhase1Weights.add(col, -value);
            }

            public void set(final long row, final long col, final Number value) {
                this.set(row, col, value.doubleValue());
            }

        }

        class ConstraintsRHS implements Mutate1D {

            public void add(final long index, final double addend) {
                myRows[(int) index].set(SparseTableau.this.countVariables() + index, ONE);
                myRHS.add(index, addend);
                myInfeasibility -= addend;
            }

            public void add(final long index, final Number addend) {
                this.add(index, addend.doubleValue());
            }

            public long count() {
                return SparseTableau.this.countConstraints();
            }

            public void set(final long index, final double value) {
                myRows[(int) index].set(SparseTableau.this.countVariables() + index, ONE);
                myRHS.set(index, value);
                myInfeasibility -= value;
            }

            public void set(final long index, final Number value) {
                this.set(index, value.doubleValue());
            }

        }

        class Objective implements Mutate1D {

            public void add(final long index, final double addend) {
                myObjectiveWeights.add(index, addend);
            }

            public void add(final long index, final Number addend) {
                this.add(index, addend.doubleValue());
            }

            public long count() {
                return SparseTableau.this.countVariables();
            }

            public void set(final long index, final double value) {
                myObjectiveWeights.set(index, value);
            }

            public void set(final long index, final Number value) {
                this.set(index, value.doubleValue());
            }

        }

        private transient ConstraintsBody myConstraintsBody = null;
        private transient ConstraintsRHS myConstraintsRHS = null;
        private double myInfeasibility = ZERO;

        private transient Objective myObjective = null;
        private final Array1D<Double> myObjectiveWeights;
        private final DenseArray<Double> myPhase1Weights;
        private final Array1D<Double> myRHS;
        private final SparseArray<Double>[] myRows;
        private double myValue = ZERO;

        @SuppressWarnings("unchecked")
        SparseTableau(final int numberOfConstraints, final int numberOfProblemVariables, final int numberOfSlackVariables) {

            super(numberOfConstraints, numberOfProblemVariables, numberOfSlackVariables);

            // Including artificial variables
            final int totNumbVars = this.countVariablesTotally();

            final Factory<Double> denseFactory = Primitive64Array.FACTORY;
            final SparseFactory<Double> sparseFactory = SparseArray.factory(denseFactory, totNumbVars).initial(3).limit(totNumbVars);
            final Array1D.Factory<Double> factory1D = Array1D.factory(denseFactory);

            myRows = new SparseArray[numberOfConstraints];
            for (int r = 0; r < numberOfConstraints; r++) {
                myRows[r] = sparseFactory.make();
            }

            myRHS = factory1D.makeZero(numberOfConstraints);

            myObjectiveWeights = factory1D.makeZero(totNumbVars);
            myPhase1Weights = denseFactory.makeZero(totNumbVars);
        }

        public long countColumns() {
            return this.countVariablesTotally() + 1L;
        }

        public long countRows() {
            return this.countConstraints() + 2L;
        }

        public double doubleValue(final long row, final long col) {

            final int myNumberOfConstraints = this.countConstraints();
            final int myNumberOfVariables = this.countVariables();

            if (row < myNumberOfConstraints) {
                if (col < (myNumberOfVariables + myNumberOfConstraints)) {
                    return myRows[(int) row].doubleValue(col);
                } else {
                    return myRHS.doubleValue(row);
                }
            } else if (row == myNumberOfConstraints) {
                if (col < (myNumberOfVariables + myNumberOfConstraints)) {
                    return myObjectiveWeights.doubleValue(col);
                } else {
                    return myValue;
                }
            } else {
                if (col < (myNumberOfVariables + myNumberOfConstraints)) {
                    return myPhase1Weights.doubleValue(col);
                } else {
                    return myInfeasibility;
                }
            }
        }

        public Double get(final long row, final long col) {
            return this.doubleValue(row, col);
        }

        protected ConstraintsBody constraintsBody() {
            if (myConstraintsBody == null) {
                myConstraintsBody = new ConstraintsBody();
            }
            return myConstraintsBody;
        }

        @Override
        protected void pivot(final IterationPoint iterationPoint) {

            final int row = iterationPoint.row;
            final int col = iterationPoint.col;

            final SparseArray<Double> pivotRow = myRows[row];
            final double pivotElement = pivotRow.doubleValue(col);

            if (PrimitiveFunction.ABS.invoke(pivotElement) < ONE) {
                final UnaryFunction<Double> tmpModifier = DIVIDE.second(pivotElement);
                pivotRow.modifyAll(tmpModifier);
                myRHS.modifyOne(row, tmpModifier);
            } else if (pivotElement != ONE) {
                final UnaryFunction<Double> tmpModifier = MULTIPLY.second(ONE / pivotElement);
                pivotRow.modifyAll(tmpModifier);
                myRHS.modifyOne(row, tmpModifier);
            }

            final double pivotedRHS = myRHS.doubleValue(row);

            double colVal;

            for (int i = 0; i < myRows.length; i++) {
                if (i != row) {
                    final SparseArray<Double> rowY = myRows[i];
                    colVal = -rowY.doubleValue(col);
                    if (colVal != ZERO) {
                        pivotRow.axpy(colVal, rowY);
                        myRHS.add(i, colVal * pivotedRHS);
                    }
                }
            }

            colVal = -myObjectiveWeights.doubleValue(col);
            if (colVal != ZERO) {
                pivotRow.axpy(colVal, myObjectiveWeights);
                myValue += colVal * pivotedRHS;
            }

            // TODO Stop updating phase 1 objective hen in phase 2.

            colVal = -myPhase1Weights.doubleValue(col);
            if (colVal != ZERO) {
                pivotRow.axpy(colVal, myPhase1Weights);
                myInfeasibility += colVal * pivotedRHS;
            }

            this.update(iterationPoint);
        }

        @Override
        protected Array1D<Double> sliceConstraintsRHS() {
            return myRHS;
        }

        @Override
        protected Array1D<Double> sliceDualVariables() {
            return myObjectiveWeights.sliceRange(this.countVariables(), this.countVariables() + this.countConstraints());
        }

        @Override
        protected Access1D<Double> sliceTableauColumn(final int col) {
            if (col < this.countVariablesTotally()) {
                return new Access1D<Double>() {

                    public long count() {
                        return SparseTableau.this.countConstraints();
                    }

                    public double doubleValue(final long index) {
                        return myRows[(int) index].doubleValue(col);
                    }

                    public Double get(final long index) {
                        return myRows[(int) index].get(col);
                    }

                };
            } else {
                return myRHS;
            }
        }

        @Override
        protected Access1D<Double> sliceTableauRow(final int row) {
            if (row < this.countConstraints()) {
                return myRows[row];
            } else if (row == this.countConstraints()) {
                return myObjectiveWeights;
            } else {
                return myPhase1Weights;
            }
        }

        ConstraintsRHS constraintsRHS() {
            if (myConstraintsRHS == null) {
                myConstraintsRHS = new ConstraintsRHS();
            }
            return myConstraintsRHS;
        }

        /**
         * @return The phase 1 objective function value
         */
        double getInfeasibility() {
            return myInfeasibility;
        }

        Objective objective() {
            if (myObjective == null) {
                myObjective = new Objective();
            }
            return myObjective;
        }

        PrimitiveDenseStore transpose() {

            final int myNumberOfConstraints = this.countConstraints();

            final PrimitiveDenseStore retVal = PrimitiveDenseStore.FACTORY.makeZero(this.countColumns(), this.countRows());

            for (int i = 0; i < myRows.length; i++) {
                for (final NonzeroView<Double> nz : myRows[i].nonzeros()) {
                    retVal.set(nz.index(), i, nz.doubleValue());
                }
            }

            retVal.fillColumn(myRows.length, myObjectiveWeights);
            retVal.fillColumn(myRows.length + 1, myPhase1Weights);

            retVal.fillRow(this.countVariables() + myNumberOfConstraints, myRHS);

            retVal.fillOne(this.countVariables() + myNumberOfConstraints, myNumberOfConstraints, myValue);
            retVal.fillOne(this.countVariables() + myNumberOfConstraints, myNumberOfConstraints + 1, myInfeasibility);

            return retVal;
        }

    }

    private final int[] myBasis;
    private final int myNumberOfConstraints;
    private final int myNumberOfProblemVariables;
    private final int myNumberOfSlackVariables;
    private final IndexSelector mySelector;

    protected SimplexTableau(final int numberOfConstraints, final int numberOfProblemVariables, final int numberOfSlackVariables) {

        super();

        myNumberOfConstraints = numberOfConstraints;
        myNumberOfProblemVariables = numberOfProblemVariables;
        myNumberOfSlackVariables = numberOfSlackVariables;

        mySelector = new IndexSelector(this.countVariables());
        myBasis = BasicArray.makeIncreasingRange(-numberOfConstraints, numberOfConstraints);
    }

    protected int countArtificialVariables() {
        return myNumberOfConstraints;
    }

    protected int countBasicArtificials() {
        int retVal = 0;
        final int tmpLength = myBasis.length;
        for (int i = 0; i < tmpLength; i++) {
            if (myBasis[i] < 0) {
                retVal++;
            }
        }
        return retVal;
    }

    protected final int countBasisDeficit() {
        return this.countConstraints() - mySelector.countIncluded();
    }

    protected int countConstraints() {
        return myNumberOfConstraints;
    }

    protected int countProblemVariables() {
        return myNumberOfProblemVariables;
    }

    protected int countSlackVariables() {
        return myNumberOfSlackVariables;
    }

    protected int countVariables() {
        return myNumberOfProblemVariables + myNumberOfSlackVariables;
    }

    protected int countVariablesTotally() {
        return myNumberOfProblemVariables + myNumberOfSlackVariables + myNumberOfConstraints;
    }

    protected final void exclude(final int anIndexToExclude) {
        mySelector.exclude(anIndexToExclude);
    }

    protected int[] getBasis() {
        return myBasis.clone();
    }

    protected int getBasis(final int basisIndex) {
        return myBasis[basisIndex];
    }

    protected final int[] getExcluded() {
        return mySelector.getExcluded();
    }

    protected final int[] getIncluded() {
        return mySelector.getIncluded();
    }

    protected final void include(final int anIndexToInclude) {
        mySelector.include(anIndexToInclude);
    }

    protected final void include(final int[] someIndecesToInclude) {
        mySelector.include(someIndecesToInclude);
    }

    protected boolean isBasicArtificials() {
        final int tmpLength = myBasis.length;
        for (int i = 0; i < tmpLength; i++) {
            if (myBasis[i] < 0) {
                return true;
            }
        }
        return false;
    }

    protected abstract void pivot(IterationPoint iterationPoint);

    protected abstract Array1D<Double> sliceConstraintsRHS();

    /**
     * @return An array of the dual variable values (of the original problem, not phase 1).
     */
    protected abstract Array1D<Double> sliceDualVariables();

    protected abstract Access1D<Double> sliceTableauColumn(final int col);

    protected abstract Access1D<Double> sliceTableauRow(final int row);

    protected void update(final IterationPoint point) {

        final int pivotRow = point.row;
        final int pivotCol = point.col;

        final int tmpOld = myBasis[pivotRow];
        if (tmpOld >= 0) {
            this.exclude(tmpOld);
        }
        final int tmpNew = pivotCol;
        if (tmpNew >= 0) {
            this.include(tmpNew);
        }
        myBasis[pivotRow] = pivotCol;
    }

}
