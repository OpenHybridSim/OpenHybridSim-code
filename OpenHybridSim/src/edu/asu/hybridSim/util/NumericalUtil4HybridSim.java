package edu.asu.hybridSim.util;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.sparse.ISparseEqnComplex;
/**
 * Numerical util for hybrid simulation
 * @author Qiuhua Huang
 * Email: qhuang24@asu.edu
 *
 */
public class NumericalUtil4HybridSim {
	
	public static String SparseComplexMatrixToString(ISparseEqnComplex m){
		StringBuffer sb = new StringBuffer();
		for(int i= 0; i<m.getDimension();i++){
			for(int j=0;j<m.getDimension();j++){
				if(m.getA(i, j).abs()>1.0E-8)sb.append((i+1)+","+(j+1)+","+m.getA(i, j).getReal()+" + ("+m.getA(i, j).getImaginary()+"i)\n");
			}
		}
		return sb.toString();
	}
	
	public static void printComplexMatrix(Complex[][] m){
		System.out.println("\n\n");
		for(int i=0;i<m.length;i++){
			for(int j=0;j<m[0].length;j++){
				System.out.print(m[i][j].toString() +"  ");
			}
			System.out.println();
		}
	}
	public static void outputComplexMatrixToMatlab(Complex[][] m){
		System.out.println("\n\n");
		for(int i=0;i<m.length;i++){
			for(int j=0;j<m[0].length;j++){
				System.out.print(complexToMaltabForm(m[i][j])+"  ");
			}
			System.out.print(";\n");
		}
	}
	private static String complexToMaltabForm(Complex a){
		return "("+a.getReal()+ (a.getImaginary()>=0?" + ":" ")+a.getImaginary()+"i)";
	}
	
	public static Complex[] creatComplex1DArray(int col){
		Complex[] complexs = new Complex[col];
		
		for(int i=0;i<col;i++){
			complexs[i] = new Complex(0,0);
		}
		return complexs;
	}
	
	
	
	public static Complex[][] creatComplex2DArray(int row,int col){
		Complex[][] complexs = new Complex[row][col];
		
		for(int i=0;i<row;i++){
			for(int j=0;j<col;j++){
			  complexs[i][j] = new Complex(0,0);
			}
		}
		return complexs;
	}
	
	public static Complex3x1[] creatComplex3x1Array(int col){
		Complex3x1[] complexs = new Complex3x1[col];
		
		for(int i=0;i<col;i++){
			complexs[i] = new Complex3x1();
		}
		return complexs;
	}

}
