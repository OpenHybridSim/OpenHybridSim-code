package edu.asu.hybridSimu;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.sparse.impl.SparseEqnComplexImpl;
import com.interpss.core.sparse.solver.CSparseJEqnComplexSolver;
import com.interpss.dstab.DStabilityNetwork;

/**
 * Sequence Network Helper is to solve the negative and zeor
 * sequence networks, which, together with the traditional positive
 * sequence transient stability simulation, realizes the three-sequence
 * transient stability simulation. 
 * 
 * @author Qiuhua Huang
 * School of Electrical, Computer and Energy Engineering
 * Ira A. Fulton Schools of Engineering
 * Arizona State University
 * Email: qhuang24@asu.edu
 *
 */
public class SequenceNetworkSolver {
	
	private DStabilityNetwork net = null;
	private ISparseEqnComplex zeroSeqYMatrix = null; 
	private ISparseEqnComplex negSeqYMatrix  = null;
	private Hashtable<String,List<Complex3x1>>  seqVoltTable =null;
	private CSparseJEqnComplexSolver zeroYSolver=null;
	private CSparseJEqnComplexSolver negYSolver=null;
    
	private String[] monitorBusAry =null;
	
	/**
	 * 
	 * @param dsNet
	 * @param monitorBusAry
	 */
	public SequenceNetworkSolver(DStabilityNetwork dsNet,String[] monitorBusAry){
		this.net =dsNet;
		this.monitorBusAry = monitorBusAry;
		
		zeroSeqYMatrix = dsNet.formYMatrix(SequenceCode.ZERO,false);
		zeroYSolver = new CSparseJEqnComplexSolver(zeroSeqYMatrix);
		
		negSeqYMatrix =  dsNet.formYMatrix(SequenceCode.NEGATIVE, false);
		negYSolver = new CSparseJEqnComplexSolver(negSeqYMatrix);
		
		//LU factorize the YMaxtri, prepare it for calculating Z matrix;
		try {
			zeroYSolver.luMatrix(1.0e-6);// tolearance is not used actually.
			negYSolver.luMatrix(1.0e-6);// tolearance is not used actually.
		} catch (IpssNumericException e) {
			
			e.printStackTrace();
		} 
		
		
		
		seqVoltTable = new Hashtable<>();
		for(String id: monitorBusAry){
			List<Complex3x1> seqVoltList= new ArrayList<>();
			seqVoltTable.put(id, seqVoltList);
		}
		
		
	}
	
	public Hashtable<String,List<Complex3x1>> getSeqVoltTable(){
		return seqVoltTable;
	}
	
	
	/**
	 * 
	 * 
	 * @param zeroSeqCurInjTable
	 * @return
	 */
	public Hashtable<String, Complex> calcZeroSeqVolt(Hashtable<String, Complex> zeroSeqCurInjTable){
		
		Hashtable<String, Complex> zeroSeqVoltHashtable = new Hashtable<>();
		((SparseEqnComplexImpl)zeroSeqYMatrix).setB2Zero();
		for(String busId: zeroSeqCurInjTable.keySet()){
		   zeroSeqYMatrix.setBi(zeroSeqCurInjTable.get(busId), net.getBus(busId).getSortNumber());
		}
		try {
			zeroYSolver.solveEqn();
		} catch (IpssNumericException e) {
			e.printStackTrace();
		}
		
		for(String busId:monitorBusAry){
			int busSortNum = net.getBus(busId).getSortNumber();
		   zeroSeqVoltHashtable.put(busId,zeroSeqYMatrix.getX(busSortNum));
		}
		
		return zeroSeqVoltHashtable;
	}
	
	/**
	 * 
	 * @param negSeqCurInjTable
	 * @return
	 */
	public Hashtable<String, Complex> calcNegativeSeqVolt(Hashtable<String, Complex> negSeqCurInjTable){
		
		Hashtable<String, Complex> negSeqVoltHashtable = new Hashtable<>();
		((SparseEqnComplexImpl)negSeqYMatrix).setB2Zero();
		for(String busId: negSeqCurInjTable.keySet()){
			int sortNum = net.getBus(busId).getSortNumber();
			Complex i2 =negSeqCurInjTable.get(busId);
		    negSeqYMatrix.setBi(i2, sortNum);
		   // System.out.println(busId+","+net.getBus(busId).getName()+", sort: "+sortNum+","+i2);
		}
		try {
			negYSolver.solveEqn();
		} catch (IpssNumericException e) {
			e.printStackTrace();
		}
		
		for(String busId:monitorBusAry){
			int busSortNum = net.getBus(busId).getSortNumber();
		   negSeqVoltHashtable.put(busId,negSeqYMatrix.getX(busSortNum));
		}
		
		return negSeqVoltHashtable;
	}


}
