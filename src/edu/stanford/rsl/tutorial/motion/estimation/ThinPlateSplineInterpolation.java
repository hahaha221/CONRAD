package edu.stanford.rsl.tutorial.motion.estimation;

import ij.ImageJ;

import java.util.ArrayList;

import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.geometry.shapes.simple.PointND;
import edu.stanford.rsl.conrad.numerics.SimpleMatrix;
import edu.stanford.rsl.conrad.numerics.SimpleOperators;
import edu.stanford.rsl.conrad.numerics.SimpleVector;

/**
 * This class provides methods to interpolate scattered data of any dimension
 * using a thin plate spline. The kernel is correct for 3-D. Other dimensions
 * will require a different kernel, e.g. r*r*ln(r) for 2-D
 * 
 * @author Marco Boegel
 * 
 */
public class ThinPlateSplineInterpolation {
	/**
	 * Input control points
	 */
	private ArrayList<PointND> gridPoints;
	/**
	 * Corresponding values to the gridPoints
	 */
	private ArrayList<PointND> values;
	/**
	 * Dimension of the points
	 */
	private int dim;
	/**
	 * TPS Interpolation coefficients
	 */
	private SimpleMatrix coefficients;
	/**
	 * Polynomial Ax+b
	 */
	private SimpleMatrix A;
	/**
	 * Polynomial Ax+b
	 */
	private SimpleVector b;
	
	public float[] getAsFloatPoints() {
		float[] pts = new float[gridPoints.size()*dim];
		for(int i = 0; i < gridPoints.size(); i++) {
			for(int k = 0 ; k < dim; k++) {
				pts[i*dim +k] = (float) gridPoints.get(i).get(k);
			}
		}
		return pts;
	}
	
	public float[] getAsFloatA() {
		float[] Af = new float[A.getRows()*A.getCols()];
		
		for(int i = 0; i < A.getCols(); i++) {
			for(int j = 0; j < A.getRows(); j++) {
				Af[i*A.getRows()+j] = (float) A.getElement(j, i);
			}
		}
		return Af;
	}

	public float[] getAsFloatB() {
		float [] Bf = new float[b.getLen()];
		
		for(int i = 0; i < b.getLen(); i++) {
			Bf[i] = (float) b.getElement(i);
		}
		return Bf;
	}
	
	public float[] getAsFloatCoeffs() {
		float [] coeff = new float[coefficients.getCols()*coefficients.getRows()];
		for(int i = 0; i < coefficients.getCols(); i++) {
			for(int j = 0; j < coefficients.getRows(); j++) {
				coeff[i*coefficients.getRows()+j] = (float) coefficients.getElement(j, i);
			}
		}
		return coeff;
	}
	public ThinPlateSplineInterpolation(int dimension,
			ArrayList<PointND> points, ArrayList<PointND> values) {
		this.gridPoints = points;
		this.values = values;
		this.dim = dimension;
		estimateCoefficients();
	}

	/**
	 * This method accepts a new pointgrid for interpolation, and the
	 * corresponding values. It automatically re-estimates the interpolation
	 * coefficients
	 * 
	 * @param points
	 *            Interpolation grid
	 * @param values
	 *            corresponding values for the points
	 * @param dimension
	 *            Dimension
	 */
	public void setNewPointsAndRecalibrate(ArrayList<PointND> points,
			ArrayList<PointND> values, int dimension) {
		this.gridPoints = points;
		this.values = values;
		this.dim = dimension;
		estimateCoefficients();

	}

	/**
	 * Estimates the coefficients for the augmented TPS interpolation (including
	 * polynomial)
	 */
	private void estimateCoefficients() {
		int n = gridPoints.size();
		int sizeL = dim * n + dim * dim + dim;
		int sizePc = dim * dim + dim;
		int sizePr = dim * n;
		SimpleMatrix L = new SimpleMatrix(sizeL, sizeL);
		SimpleMatrix P = new SimpleMatrix(sizePr, sizePc);
		SimpleVector rhs = new SimpleVector(sizeL);
		A = new SimpleMatrix(dim, dim);
		b = new SimpleVector(dim);
		L.zeros();
		P.zeros();
		rhs.zeros();

		for (int i = 0; i < n; i++) {
			rhs.setSubVecValue(i * dim, values.get(i).getAbstractVector());
			for (int j = 0; j < n; j++) {
				double val = kernel(gridPoints.get(i), gridPoints.get(j));
				for (int k = 0; k < dim; k++) {
					L.setElementValue(i * dim + k, j * dim + k, val);
				}

			}
		}

		for (int i = 0; i < n; i++) {
			for (int j = 0; j < dim; j++) {
				double val = gridPoints.get(i).get(j);
				for (int k = 0; k < dim; k++) {
					P.setElementValue(dim * i + k, dim * j + k, val);
				}
			}
			for (int k = 0; k < dim; k++) {
				P.setElementValue(i * dim + k, dim * dim + k, 1.0);
			}
		}

		L.setSubMatrixValue(0, dim * n, P);
		L.setSubMatrixValue(dim * n, 0, P.transposed());

		

		SimpleMatrix Linv = L.inverse(SimpleMatrix.InversionType.INVERT_SVD);
		SimpleVector parameters = SimpleOperators.multiply(Linv, rhs);
		coefficients = new SimpleMatrix(dim, n);
		for (int i = 0; i < n; i++) {
			coefficients.setSubColValue(0, i,
					parameters.getSubVec(i * dim, dim));
		}
		for (int i = 0; i < dim; i++) {
			A.setSubColValue(0, i, parameters.getSubVec(dim * n + i * dim, dim));
		}
		b.setSubVecValue(0, parameters.getSubVec(dim * n + dim * dim, dim));

	}

	/**
	 * Interpolation kernel. Implements only euclidean distance
	 * 
	 * @param p1
	 *            Point 1
	 * @param p2
	 *            Point 2
	 * @return euclidean distance between two points
	 */
	private double kernel(PointND p1, PointND p2) {
		return p1.euclideanDistance(p2);
	}

	/**
	 * Lifts the kernel to the required dimension for interpolation
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	private SimpleMatrix G(PointND p1, PointND p2) {
		SimpleMatrix G = new SimpleMatrix(dim, dim);
		G.identity();
		double val = kernel(p1, p2);
		G.multiplyBy(val);
		return G;
	}

	/**
	 * Interpolation function
	 * 
	 * @param pt
	 *            point at which the value should be interpolated
	 * @return value
	 */
	public SimpleVector interpolate(PointND pt) {

		SimpleVector res = new SimpleVector(pt.getDimension());
		res.zeros();
		for (int i = 0; i < coefficients.getCols(); i++) {
			SimpleVector Gc = SimpleOperators.multiply(
					G(pt, gridPoints.get(i)), coefficients.getCol(i));

			res = SimpleOperators.add(res, Gc);
		}
		SimpleVector Ax = SimpleOperators.multiply(A, pt.getAbstractVector());
		res = SimpleOperators.add(res, Ax, b);
		return res;
	}

	public static void main(String args[]) {
		new ImageJ();
		ArrayList<PointND> points = new ArrayList<PointND>();
		points.add(new PointND(30, 30));
		points.add(new PointND(60, 60));
		points.add(new PointND(90, 90));
		points.add(new PointND(120, 140));
		points.add(new PointND(150, 90));
		points.add(new PointND(180, 60));
		points.add(new PointND(210, 30));
		ArrayList<PointND> values = new ArrayList<PointND>();
		values.add(new PointND(100));
		values.add(new PointND(200));
		values.add(new PointND(300));
		values.add(new PointND(400));
		values.add(new PointND(300));
		values.add(new PointND(200));
		values.add(new PointND(100));

		for (int i = 0; i < 100; i++) {
			points.add(new PointND(i * 5, 400));
			values.add(new PointND(0, 0));
		}
		// for(int i = 0; i < 100;i++) {
		// points.add(new PointND(i*5,499));
		// values.add(new PointND(0,0));
		// }

		ThinPlateSplineInterpolation tps = new ThinPlateSplineInterpolation(2,
				points, values);

		tps.interpolate(new PointND(30, 210));
		Grid2D grid = new Grid2D(500, 500);

		for (int i = 0; i < 500; i++) {
			for (int j = 0; j < 500; j++) {
				grid.setAtIndex(i, j, (float) tps
						.interpolate(new PointND(i, j)).getElement(0));
			}
		}
		grid.show();
		

	}
}
/*
 * Copyright (C) 2010-2014 Marco B�gel
 * CONRAD is developed as an Open Source project under the GNU General Public License (GPL).
*/