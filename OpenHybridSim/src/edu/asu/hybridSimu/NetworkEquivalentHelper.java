package edu.asu.hybridSimu;

import static com.interpss.core.funcImpl.AcscFunction.acscLineAptr;
import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.FieldLUDecomposition;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.FieldVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.ipss.multiNet.algo.SubNetworkProcessor;

import au.com.bytecode.opencsv.CSVWriter;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.adpter.AclfLine;
import com.interpss.core.aclf.adpter.AclfXformer;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscLine;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.sparse.impl.SparseEqnComplexImpl;
import com.interpss.core.sparse.solver.CSparseJEqnComplexSolver;
import com.interpss.dstab.DStabilityNetwork;

import edu.asu.hybridSim.util.NumericalUtil4HybridSim;

/**
 * 
 * @author Qiuhua Huang
 * School of Electrical, Computer and Energy Engineering
 * Ira A. Fulton Schools of Engineering
 * Arizona State University
 * Email: qhuang24@asu.edu
 *
 *
 */
public class NetworkEquivalentHelper {
	
	private HybidSimSubNetworkHelper hsSubNetHelper = null;
	private SubNetworkProcessor      subNetProcessor = null;
	
	private BaseAclfNetwork<? extends AclfBus,? extends AclfBranch> net = null;
	
    private CSparseJEqnComplexSolver csSolver=null;
    private ISparseEqnComplex seqYmatrixEqn =null;

    private Hashtable<String, Complex[][]> boundaryBusSelfYabcTable =null;
    
   
	private String[] boundaryBusIdAry = null;

	private List<String> boundaryBusIdList =null;
	private List<String> internalBoundaryBusIdList =null;
	private List<String> internalNetworkBusList =null;
	private List<String> internalNetworkBranchList =null;
	private Hashtable<String, Integer> busIdxTable =new Hashtable<>();
	 
	private int networkType = 1; // network type: 1 -> aclf, 2->acsc, 3->dstab
	
	private Complex[][] seqZMatrix =null;
	private Complex[][] posZMatrix =null;
	private Complex[][] negZMatrix =null;
	private Complex[][] zeroZMatrix =null;
	private Complex[][] abcZMatrix =null;
	private Complex[][] Y120Matrix =null;
	private Complex3x3[][] nortonYABCMatrix = null;
	private Complex3x1[] nortonIABCVector = null;
	
	private Complex[] INorton120 =null;
	
	private StringBuffer equivalentInfo = new StringBuffer();
	
	private List<String> originalOffLineBusIdList = new ArrayList<>();
	private List<String> originalOFFLineBranchIdList = new ArrayList<>();
	
	private boolean useSubNetProcessor = false;
	
	

	private static final Complex a = new Complex(-0.5, Math.sqrt(3)/2);
	public static final Complex[][] T = new Complex[][]{
			{new Complex(1,0),new Complex(1,0),new Complex(1,0)},
			{a.multiply(a),      a            ,new Complex(1,0)},
			{a,               a.multiply(a)   ,new Complex(1,0)}};
	
	 public static final Complex[][] Tinv = new Complex[][]{
			{new Complex(1.0/3,0), a.divide(3)              ,a.multiply(a).divide(3)},
			{new Complex(1.0/3,0), a.multiply(a).divide(3)  ,a.divide(3)},
			{new Complex(1.0/3,0), new Complex(1.0/3,0)       ,new Complex(1.0/3,0)}};
	
	/**
	 * Within the constructor, the subNetworkHelper is called to determine the internal or study subnetwork
	 * and set the internal network status to inactive, i.e., set the status of buses and branches
	 * within the subNetwork to be false or off-line
	 * @param subNetworkHelper
	 */
	public NetworkEquivalentHelper(HybidSimSubNetworkHelper subNetworkHelper){
		
		this.hsSubNetHelper = subNetworkHelper;
		this.net = hsSubNetHelper.getNet();
		
		//determine the network type
		if(this.net instanceof AcscNetwork)
			networkType = 2;
		else if(this.net instanceof DStabilityNetwork)
			networkType = 3;
		
		//keep a record of the original off-line buses and branches
		//whose status will not be changed by the subNetwork processing
		for(Bus b:this.net.getBusList()){
			if(!b.isActive())
				originalOffLineBusIdList.add(b.getId());
		}
		for(Branch bra:this.net.getBranchList()){
			if(!bra.isActive())
				originalOFFLineBranchIdList.add(bra.getId());
		}
		// search the study subNetwork and set the status of buses and branches within the subNetwork  to be false
		if(!hsSubNetHelper.isSubNetworkSearched()){
		   hsSubNetHelper.searchSubNetwork();
		
		}
		
		
		// save the internal network info to the equivalent network helper
		internalNetworkBusList = hsSubNetHelper.getInternalNetworkBusList();
		internalNetworkBranchList = hsSubNetHelper.getInternalNetworkBranchList();
		boundaryBusIdList = hsSubNetHelper.getBoundaryBusIdList();
		
		boundaryBusIdAry = boundaryBusIdList.toArray(new String[0]);
		
		
		 //set the internal subNetwork status to false;
		setSubNetworkStatus(true, false);
		
	
	}
	
	/**
	 * Different from using the HybridSimSubNetworkHelper, which use the full network as the input 
	 * to calculate the equivalent of the external system
	 * 
	 * SubNetworkProcessor already split the network into internal and external system, and it uses the 
	 * boundary buses and the external system to calculate the equivalent.
	 * 
	 * @param subNetProc
	 */
	public NetworkEquivalentHelper(SubNetworkProcessor subNetProc){
		
		this.useSubNetProcessor = true;
		this.subNetProcessor = subNetProc;
		
		//set the tie Lines to be false
		subNetProc.setInternalTieLineStatus(false);
		
		//this.subNetHelper = subNetworkHelper;
		this.net = subNetProc.getExternalSubNetwork();
		
		//determine the network type
		if(this.net instanceof AcscNetwork)
			networkType = 2;
		else if(this.net instanceof DStabilityNetwork)
			networkType = 3;
		
		
		internalNetworkBusList = new ArrayList();
		
		// save the external network info to the equivalent network helper
		boundaryBusIdList = subNetProc.getExternalSubNetBoundaryBusIdList();
		
		this.internalBoundaryBusIdList=subNetProc.getInternalSubNetBoundaryBusIdList();
		
		boundaryBusIdAry = boundaryBusIdList.toArray(new String[0]);
	
	
	}
	
	
	/**
	 * 
	 * @param onlyPositiveSequence
	 * @param zthreshold
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public  boolean buildNetWorkEquivalent(boolean onlyPositiveSequence, double zthreshold){
		// for equivalent info logging and output
		String generatorEquivInfo ="\n\n Generator Equivalent info, all in pu :\n";
		generatorEquivInfo +="BusId, BusName, basekV,GenP,GenQ, posGenZ(Re) ,posGenZ(Im),negGenZ(Re) ,negGenZ(Im),zeroGenZ(Re),zeroGenZ(Im)\n";
			    
		equivalentInfo.append("\n\nLine and Xfr Equivalent info, all in pu: \n");
		equivalentInfo.append("From Bus Id, From Bus Name, To Bus Id,To Bus Name, cirId, Type, Zpos(Re), Zpos(Im), Zzero(Re), Zzero(Im)\n");
				
		
		
		if(!onlyPositiveSequence){
			if(networkType ==1){
				IpssLogger.getLogger().severe("The input network is ACLF type, thus the equivalencing is processed with positive sequence only!");
				onlyPositiveSequence = true;
			}
			else if(networkType >1 && ((BaseAcscNetwork<AcscBus,AcscBranch>)net).isPositiveSeqDataOnly()){
				throw new Error("The argument <onlyPositiveSequence> is set to false, but the network provides the positive sequence data only");
			}
		}
		
		//set internal or study network offline for calculating the Yseq or Zeq of the external network
		//this.setSubNetworkStatus(true,false);
		
		// step-1 build Norton equivalents for the external subnetwork
		
		//calculate the sequence network admittance matrix related to the boundary buses
		
		Complex[][] yPosMatrix = calcInterfaceSeqYMatrix(SequenceCode.POSITIVE);

		
		 Complex[][] yZeroMatrix = null;
		 Complex[][] yNegMatrix  = null; 
		if(!onlyPositiveSequence){
		    yZeroMatrix = calcInterfaceSeqYMatrix(SequenceCode.ZERO);
		    yNegMatrix  = calcInterfaceSeqYMatrix(SequenceCode.NEGATIVE); 
		}
		
		//set the internal network on-line again
		this.setSubNetworkStatus(true,true);
		
		// step-2 set the buses and branches of the external network to be off-line
		this.setSubNetworkStatus(false,false);
		
		
		// step-3 create new branches connecting the boundary buses, with branch cirId = 99
		for(int i = 0; i<hsSubNetHelper.getBoundaryBusIdList().size(); i++){
			String fromBusId = hsSubNetHelper.getBoundaryBusIdList().get(i);
			AclfBus busi = this.net.getBus(fromBusId);
			
			for(int j = i+1;j<hsSubNetHelper.getBoundaryBusIdList().size();j++){
				String toBusId = hsSubNetHelper.getBoundaryBusIdList().get(j);
				AclfBus busj = this.net.getBus(toBusId);
				
				
				Complex Y1ij = yPosMatrix[i][j];
				Complex z1ij = new Complex(-1,0).divide(Y1ij);
				
				
				if(Math.abs(z1ij.getImaginary())<=zthreshold){
					// based on the terminal bus base voltage, the new branch can be either a 
					// line or a transformer
					boolean isLine = busi.getBaseVoltage()==busj.getBaseVoltage();
					
					double baseV = busi.getBaseVoltage()>=busj.getBaseVoltage()?busi.getBaseVoltage():
						                busj.getBaseVoltage();
					
					//the cirId of the equivalent branches are set to be 99
					 String cirId = "99";
					
					AclfBranch branch = CoreObjectFactory.createAclfBranch();
					
					 if(this.networkType ==2)
						 branch =CoreObjectFactory.createAcscBranch(); 
				     else if(this.networkType ==3)
				        branch = DStabObjectFactory.createDStabBranch();
					 
					
					try {
						((BaseAclfNetwork<AclfBus,AclfBranch>)net).addBranch(branch, fromBusId, toBusId, cirId);
					} catch (InterpssException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					
					
					// the equivalent branch is a line
					if(isLine){
						AclfLine line = branch.toLine();
						line.setZ(z1ij, UnitType.PU, busi.getBaseVoltage());
					}
					else{
						AclfXformer xfr = branch.toXfr();
						xfr.setZ(z1ij, UnitType.PU, baseV);
						xfr.setFromTurnRatio(1.0);
						xfr.setToTurnRatio(1.0);
					}
					
					equivalentInfo.append(fromBusId+","+this.net.getBus(fromBusId).getName()+","+toBusId+","+
					          this.net.getBus(toBusId).getName()+"," +cirId+","+(isLine?"Line":"Transformer")+
					          ","+z1ij.getReal() +","+z1ij.getImaginary()); /*+","+z2ij.getReal() +","+z2ij.getImaginary()*/
					
					//set the zero sequence data for the equivalent branches;
					Complex z0ij = null;
					if(!onlyPositiveSequence){
						z0ij = new Complex(-1,0).divide(yZeroMatrix[i][j]);
						
						if(branch instanceof AcscBranch){
						
						   if(isLine){
							   AcscLine scLine = acscLineAptr.apply((AcscBranch)branch);
							   scLine.setZ0(z0ij, UnitType.PU, busi.getBaseVoltage());
						   }
						   else{
							   AcscXformer xfr = acscXfrAptr.apply((AcscBranch)branch);
							   xfr.setZ0(z0ij, UnitType.PU,  baseV);
							   //((AcscBranch)branch).setZ0(z0ij);
							   //NOTE: Connection type is assumed to be Wye-Ground and Wye-Ground
							   ((AcscBranch)branch).setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							   ((AcscBranch)branch).setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
						   }
						   
						   equivalentInfo.append(","+z0ij.getReal() +","+z0ij.getImaginary());
						}
						
					}
					equivalentInfo.append("\n");
					}
				
			}
			
			    
			    
				// step-4 calculate the power flow of the boundary bus to determine the equivalent Generator injection
		        
		        //Calculate the bus power mismatch, in order to determine the equiv gen power injection into the bus 
		        Complex powerMis = busi.mismatch(AclfMethod.QA);
		        Complex genPQ = powerMis.multiply(-1); // equivalent generation is to compensate the power mismatch
				
		        //get selfAdmittance of a bus
			    Complex eqvYii1 = getSelfAdmittance(yPosMatrix,i);
		        
		        // eqvGenZi = 1/eqvYii0
		        Complex eqvGenZi = new Complex(1,0).divide(eqvYii1);
		        
		        //create new generator model, id = EQ
		        if(!busi.isGen())
					busi.setGenCode(AclfGenCode.GEN_PQ);
		        
		        AclfGen gen = CoreObjectFactory.createAclfGen("EQ");
		        if(this.networkType ==2)
		        	gen = CoreObjectFactory.createAcscGen("EQ");
		        else if(this.networkType ==3)
		        	gen = DStabObjectFactory.createDStabGen("EQ");
		        
				busi.getContributeGenList().add(gen);
				
				gen.setGen(genPQ);
				gen.setSourceZ(eqvGenZi);
				
				gen.setMvaBase(this.net.getBaseMva()); // system mva
				
				generatorEquivInfo +=busi.getId()+","+busi.getName()+","+busi.getBaseVoltage()/1000.0+","+genPQ.getReal()+","+genPQ.getImaginary()+","+eqvGenZi.getReal()+","+eqvGenZi.getImaginary();
				
				if(!onlyPositiveSequence){
					 ((AcscBus)busi).setScCode(BusScCode.CONTRIBUTE);
					 
					//positive and negative
					((AcscGen)gen).setPosGenZ(eqvGenZi);
					
					//negative sequence
					
					Complex eqvYii2 = getSelfAdmittance(yNegMatrix,i);
					Complex negGenZi = new Complex(1,0).divide(eqvYii2);
					((AcscGen)gen).setNegGenZ(negGenZi);
					
					//zero sequence
					
					//must set grounding to be solid_grouned, to make sure the equivalent zero sequence genZ
					//is correctly represented in the network
					 ((AcscBus)busi).getGrounding().setCode(BusGroundCode.SOLID_GROUNDED);
					 Complex eqvYii0 = getSelfAdmittance(yZeroMatrix,i);
					 Complex zeroGenZi = new Complex(1,0).divide(eqvYii0);
					 ((AcscGen)gen).setZeroGenZ(zeroGenZi);
					 
					 generatorEquivInfo +=","+negGenZi.getReal()+","+negGenZi.getImaginary()+","+zeroGenZi.getReal()+","+zeroGenZi.getImaginary();
				}
				generatorEquivInfo +="\n";
				
				// as new generator model is added, need to re-initMultiGen
				try {
					busi.initContributeGen();
				} catch (InterpssException e) {
					e.printStackTrace();
					return false;
				}
			
		} // end of for loop -i
		equivalentInfo.append(generatorEquivInfo);

		//step-5 check if there is a swing bus in the network, if not choose the bus with the largest generation output 
		// as the swing bus
		boolean hasSwing = false;
		for(String id:hsSubNetHelper.getInternalNetworkBusList()){
			if(this.net.getBus(id).isSwing())
				hasSwing = true;
		}
		for(String id:hsSubNetHelper.getBoundaryBusIdList()){
			if(this.net.getBus(id).isSwing())
				hasSwing = true;
		}
		
		String swingBusId = "";
		double genP =0;
		if(!hasSwing){
			for(String id:hsSubNetHelper.getInternalNetworkBusList()){
				if(this.net.getBus(id).isActive() && this.net.getBus(id).isGen()){
					if(this.net.getBus(id).getGenP()>genP){
						genP = this.net.getBus(id).getGenP();
						swingBusId = id;
					}
				}
			}
			
			// if there is no generation bus within the internal network, choose one of the boundary bus 
			// as the swing bus
			if(swingBusId.equals("")){
				swingBusId = hsSubNetHelper.getBoundaryBusIdList().get(0);
			}
			this.net.getBus(swingBusId).setGenCode(AclfGenCode.SWING);
			IpssLogger.getLogger().info("Bus : id ="+swingBusId+", name ="+this.net.getBus(swingBusId).getName()+"is selected as the swing bus for the study sub-network");
			
		}
		//display the network equivalent info;
		System.out.println(equivalentInfo.toString());
		return true;
		
	}
	public String getEquivalentInfo() {
		return equivalentInfo.toString();
	}
	
	public BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch> getNetwork() {
		return net;
	}

	public void setNetwork(BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch> net) {
		this.net = net;
	}
    
	/**
	 * set the subnetwork (study or external sub network) bus and branch status
	 * @param internalNetwork    boolean for defining which part of the network needs to process
	 * @param status      true for on-line, false for off-line
	 * @return
	 */
	public boolean setSubNetworkStatus(boolean internalNetwork, boolean status){
		//set internal network to be off-line, usually used for EMT-TSA hybrid simulation
		if(internalNetwork){
			for(Bus b:this.net.getBusList()){
				//NOTE: boundary buses are not elements of the internalNetworkBusList.
				if(this.internalNetworkBusList.contains(b.getId()) && 
						!this.originalOffLineBusIdList.contains(b.getId()))
					b.setStatus(status);
			}
			for(Branch bra:this.net.getBranchList()){
				if(this.internalNetworkBranchList.contains(bra.getId()) &&
						!this.originalOFFLineBranchIdList.contains(bra.getId()))
					bra.setStatus(status);
				
			}
		}
		// set external network to be off-line, usually used for network equivalent
		else{
			for(Bus b:this.net.getBusList()){
				if(!this.internalNetworkBusList.contains(b.getId()) &&
						!this.boundaryBusIdList.contains(b.getId()) &&
						!this.originalOffLineBusIdList.contains(b.getId()))
					b.setStatus(status);
			}
			for(Branch bra:this.net.getBranchList()){
				if(!this.internalNetworkBranchList.contains(bra.getId()) &&
						!this.originalOFFLineBranchIdList.contains(bra.getId()))
					bra.setStatus(status);
				
			}
		}
		return true;
	}
	
	public HybidSimSubNetworkHelper getSubNetHelper() {
		return hsSubNetHelper;
	}

	public void setSubNetHelper(HybidSimSubNetworkHelper subNetHelper) {
		this.hsSubNetHelper = subNetHelper;
	}
	
	public SubNetworkProcessor getSubNetProcessor(){
		return this.subNetProcessor;
	}

	/**
	 * set the sequence admittance matrix
	 * @param seqYmatrixEqn
	 */
	public void setSeqYmatrix(ISparseEqnComplex seqYmatrixEqn){
		this.seqYmatrixEqn=seqYmatrixEqn;
	}
	
	/**
	 * process the sequence admittance matrix to exclude the impact of loads of
	 * the boundary buses as these loads are assumed to be modeled in the internal network
	 * 
	 * @param eqn
	 * @return
	 */
	private boolean reCalcBoundaryBusYii(ISparseEqnComplex eqn, SequenceCode seq){
		
		if(eqn instanceof SparseEqnComplexImpl){
			SparseEqnComplexImpl eqnImpl = (SparseEqnComplexImpl)eqn;
			
			
		for(String id:boundaryBusIdAry){
				AclfBus bus=net.getBus(id);
				int sortNum =bus.getSortNumber();
				double vmag = bus.getVoltageMag();
				
			  Complex busEquivLoadComplex =bus.getLoadPQ().conjugate().divide(vmag*vmag);
			   
			 // System.out.println("equivLoad @" +id+": "+busEquivLoadComplex.toString());
	           
			//  System.out.println("Yii before change="+eqn.getA(sortNum, sortNum));
			   
			  //  eqn.addToA(busEquivLoadComplex.multiply(-1), sortNum, sortNum);
		
				
				Complex busEquivYii = new Complex(0,0);
			
				for(Branch bra: bus.getBranchList()){
					if(bra.isActive() && bra instanceof AcscBranch){
						AcscBranch acscBra = (AcscBranch) bra;
						if(bra.getFromBus().getId().equals(id))
						    busEquivYii = busEquivYii.add(acscBra.yff(seq));
						else
							busEquivYii = busEquivYii.add(acscBra.ytt(seq));
					}
				}
				eqn.setA(busEquivYii, sortNum, sortNum);
				
				//System.out.println("Yii after change="+eqn.getA(sortNum, sortNum));
		    }
		
		
			return true;
		}
		else
		    return false;
	}
	
	
	/**
	 * calculate the sequence impedance matrix related to those boundary buses
	 * 
	 * @param boundaryBusIdAry
	 * @param code
	 * @return  a Complex[][] matrix corresponding to the order in the boundaryBusIdAry
	 * @throws IpssNumericException 
	 */
	public  Complex[][] calcInterfaceSeqZMatrix(SequenceCode code){
		
		if(this.networkType==1 && (code.equals(SequenceCode.NEGATIVE) ||code.equals(SequenceCode.ZERO)) ){
            throw new Error ("The network is of ACLF type, does not support negative or zero sequence");
		}
		else{
			int dim =this.boundaryBusIdAry.length;
			seqZMatrix = new Complex[dim][dim]; 
			
			switch (code) {
			case ZERO:
				if( ((BaseAcscNetwork<AcscBus, AcscBranch>)net).getZeroSeqYMatrix() !=null)
					seqYmatrixEqn = ((BaseAcscNetwork<AcscBus, AcscBranch>)net).getZeroSeqYMatrix();
				else
				  seqYmatrixEqn = ((BaseAcscNetwork<AcscBus, AcscBranch>)net).formYMatrix(SequenceCode.ZERO,false);
				 
				reCalcBoundaryBusYii(seqYmatrixEqn,SequenceCode.ZERO);
				break;
	        
			case NEGATIVE:	
				
				//setBoundaryGenLoadInactive();
				if( ((BaseAcscNetwork<AcscBus, AcscBranch>)net).getNegSeqYMatrix() !=null)
					seqYmatrixEqn = ((BaseAcscNetwork<AcscBus, AcscBranch>)net).getNegSeqYMatrix();
				else
				 seqYmatrixEqn = ((BaseAcscNetwork<AcscBus, AcscBranch>)net).formYMatrix(SequenceCode.NEGATIVE,false);
				 reCalcBoundaryBusYii(seqYmatrixEqn,SequenceCode.NEGATIVE);
				
				break;
				
			default:
				
				 if(this.networkType >1){
					 
					 // gen and loads are assumed to be modeled in the internal system.
					// setBoundaryGenLoadInactive();
					 if(net.getYMatrix()!=null)
						 seqYmatrixEqn  = net.getYMatrix();
					 else
					 seqYmatrixEqn = ((BaseAcscNetwork<AcscBus, AcscBranch>)net).formYMatrix(SequenceCode.POSITIVE,false);
					 
					 reCalcBoundaryBusYii(seqYmatrixEqn,SequenceCode.POSITIVE);
					
				 }
				else{
					throw new UnsupportedOperationException("Input Data must inlclude Acsc(sequence) and/or DStab data");
					// setBoundaryGenLoadInactive();
				  
				}
				
				break;
			
			}
			
			
			csSolver = new CSparseJEqnComplexSolver(seqYmatrixEqn);
			
			//LU factorize the YMaxtri, prepare it for calculating Z matrix;
		       try{
					csSolver.luMatrix(1.0e-8);// tolearance is not used actually.
				} catch (IpssNumericException e) {
					
					e.printStackTrace();
				} 
			
			
		
				for(int i = 0;i<dim;i++){
					String busId = boundaryBusIdAry[i];
					AclfBus bus = net.getBus(busId);
					int idx = bus.getSortNumber();
					seqYmatrixEqn.setB2Unity(idx); //unit current injection at bus of Idx only, the rest are zero
					try {
						csSolver.solveEqn();
						//seqYmatrixEqn.solveEqn();
					} catch (IpssNumericException e) {
						
						e.printStackTrace();
					}
	
				
					for(int j=0;j<dim;j++){
						busId = boundaryBusIdAry[j];
						AclfBus busj = net.getBus(busId);
						seqZMatrix[i][j]=seqYmatrixEqn.getX(busj.getSortNumber());
					}
						
					
				}
				if(code==SequenceCode.POSITIVE){
					posZMatrix=seqZMatrix;
				}
				else if(code==SequenceCode.NEGATIVE){
					negZMatrix =seqZMatrix;
				}
				else{
					zeroZMatrix =seqZMatrix;
				}
			
	    }
	  
	  return seqZMatrix;
	}
	/*
	private void setBoundaryGenLoadInactive(){
		for(String id:boundaryBusIdAry){
			AclfBus bus=net.getBus(id);
			
			for(AclfLoad load:bus.getLoadList()){
				load.setStatus(false);
			}
			
			for(AclfGen gen:bus.getGenList()){
				gen.setStatus(false);
			}
		}
		
	}
	*/
	
	/**
	 * calculate the sequence Y matrix of the boundary buses with
	 * through Y = inv(Z)
	 * @param code
	 * @return
	 */
	public Complex[][] calcInterfaceSeqYMatrix(SequenceCode code){
		Complex[][] zComplexs = calcInterfaceSeqZMatrix(code);
		
		try {
			return invMatrix(zComplexs);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
	/**
	 * calculate and return the abc coordinate impedance matrix related to those boundary buses
	 *      i     j    ...  n      
	 *  i |zabc  zabc ... zabc|
	 *  j | .                 |
	 *  . | .     .   ... zabc|
	 *  n | zabc  .   ... zabc|
	 * 
	 * 
	 * @param boundaryBusIdAry
	 * @return
	 */
	public  Complex[][] calcInterfaceZabcMatrix(){
		calcInterfaceSeqZMatrix(SequenceCode.POSITIVE);
		calcInterfaceSeqZMatrix(SequenceCode.NEGATIVE);
		calcInterfaceSeqZMatrix(SequenceCode.ZERO);
	     
	    int dim = boundaryBusIdAry.length;
	    abcZMatrix = new Complex[3*dim][3*dim];
	    for(int i=0;i<dim;i++){
	    	for(int j=0;j<dim;j++){
	    	     Complex[][] subZ120 = NumericalUtil4HybridSim.creatComplex2DArray(3, 3);
	    	     subZ120[0][0] = posZMatrix[i][j];
	    	     subZ120[1][1] = negZMatrix[i][j];
	    	     subZ120[2][2] = zeroZMatrix[i][j];
	    	     
	    	     //transform each 3*3 sub matrix to sequence matrix
	    	     //Zabc=T*Z120*T^-1
	    	    
	    	     Complex[][] zabc = Z120ToAbc(subZ120);
	    	     
	    	     //save the sub matrix into the boundary buses Zabc matrix
	    	     for(int k =0;k<3;k++){
	    	    	 abcZMatrix[i*3][j*3+k] = zabc[0][k];
	    	    	 abcZMatrix[i*3+1][j*3+k] = zabc[1][k];
	    	    	 abcZMatrix[i*3+2][j*3+k] = zabc[2][k];
	    	     }
	    	     
	    	}
	    }
	    return abcZMatrix;
	    
	}
	
	
	/**
	 * Get the self and mutual admittance of boundary buses in ABC coordinate 
	 * @param YabcMatrix 
	 * @return
	 */
	public Complex[][] getInterfaceYabcMatrix(){
		if(Y120Matrix == null) getInterfaceY120Matrix();
		
		int dim = boundaryBusIdAry.length;
		Complex[][] YabcMatrix = NumericalUtil4HybridSim.creatComplex2DArray(3*dim,3*dim);
		
	    for(int i=0;i<dim;i++){
	    	for(int j=0;j<dim;j++){
	    		 Complex[][] SubYabcMatrix = NumericalUtil4HybridSim.creatComplex2DArray(3,3);
	    		 SubYabcMatrix[0][0] = Y120Matrix[i*3][j*3];
	    		 SubYabcMatrix[1][1] = Y120Matrix[i*3+1][j*3+1];
	    		 SubYabcMatrix[2][2] = Y120Matrix[i*3+2][j*3+2];
	    		 /*
	    		 System.out.println("i,j="+i+","+j);
	    		 System.out.println("subY120=");
	    		 MyUtil.outputComplexMatrixToMatlab(SubYabcMatrix);
	    		 */
	    		 SubYabcMatrix = Z120ToAbc(SubYabcMatrix);
	    		 /*
	    		 System.out.println("subYabc=");
	    		 MyUtil.outputComplexMatrixToMatlab(SubYabcMatrix);
	    		*/
	    		 
	    		 for(int m=0;m<3;m++){
	    			 for(int n=0;n<3;n++){
	    				 YabcMatrix[3*i+m][3*j+n] = SubYabcMatrix[m][n];

	    			 }
	    		 }
	    	    
	    	    
	    	}     
	   }
		return YabcMatrix;
	}
	
	/**
	 * get the self and mutual admittance of boundary buses in 120 coordinate
	 * @return
	 */
	public Complex[][] getInterfaceY120Matrix(){
		Complex[][] posYMatrix  = calcInterfaceSeqYMatrix(SequenceCode.POSITIVE);
		Complex[][] negYMatrix = calcInterfaceSeqYMatrix(SequenceCode.NEGATIVE);
		Complex[][] zeroYMatrix = calcInterfaceSeqYMatrix(SequenceCode.ZERO);
	     
	    int dim = boundaryBusIdAry.length;
	    Y120Matrix = NumericalUtil4HybridSim.creatComplex2DArray(3*dim,3*dim);
	    for(int i=0;i<dim;i++){
	    	for(int j=0;j<dim;j++){
	    	     Y120Matrix[3*i][3*j] = posYMatrix[i][j];
	    	     Y120Matrix[3*i+1][3*j+1] = negYMatrix[i][j];
	    	     Y120Matrix[3*i+2][3*j+2] = zeroYMatrix[i][j];
	    	    
	    	}     
	   }
	    
	   return Y120Matrix;
	    
	}
	
	public Complex3x3[][] getNortonYABCMatrix(){
		if(this.nortonYABCMatrix!=null) return this.nortonYABCMatrix;
		
		if(this.Y120Matrix==null) getInterfaceY120Matrix();
		
		int dim = boundaryBusIdAry.length;
		if(this.Y120Matrix!=null && this.Y120Matrix.length==dim*3){
			this.nortonYABCMatrix = MatrixUtil.createComplex3x32DArray(dim, dim);
		   for(int i=0;i<dim;i++){
		    	for(int j=0;j<dim;j++){
		    		Complex3x3 y= new Complex3x3( Y120Matrix[3*i][3*j], Y120Matrix[3*i+1][3*j+1], Y120Matrix[3*i+2][3*j+2]);
		    		this.nortonYABCMatrix[i][j] = Complex3x3.z12_to_abc(y);
		    	}
		    	
		    }
		}
		  
		return this.nortonYABCMatrix;
	}
	
	public Complex3x1[] getNortonIABCVector(){
		if(this.INorton120!=null){
			int dim = boundaryBusIdAry.length;
			this.nortonIABCVector = MatrixUtil.createComplex3x1DArray(dim);
			 for(int i=0;i<dim;i++){ 
				 // order of Complex3x1 input: a_0  ;  b_1  ; c_2
				 Complex3x1 i012 = new Complex3x1(this.INorton120[3*i+2],this.INorton120[3*i],this.INorton120[3*i+1]);
				 this.nortonIABCVector[i] = Complex3x1.z12_to_abc(i012);
			 }
			
		} else{
			try {
				throw new Exception("The three-sequence INorton120 is not calculated before calling getNortonIABCVector()");
			} catch (Exception e) {
				
				e.printStackTrace();
			}
		}
		return this.nortonIABCVector;
		
	}
	
	
	public Hashtable<String,Complex3x1> get3PhaseNortonCurrentSourceTable(){
		
		Complex3x1[] INorthonAry = getNortonIABCVector();
		Hashtable<String,Complex3x1> nortonCurTable = new Hashtable<>();
		
		for(int i=0;i<boundaryBusIdAry.length;i++){
			String id = boundaryBusIdAry[i].replaceAll("Dummy", "");
			nortonCurTable.put(id, INorthonAry[i]);
		}
		
		return nortonCurTable;
		
	}
	
	

	
	
	/**
	 * Get the boundary bus self primitive admittance matrix
	 * @return
	 * @throws Exception
	 */
    public Hashtable<String, Complex[][]> getBoundaryBusSelfYabcMatrix() throws Exception{
    	
    	boundaryBusSelfYabcTable = new Hashtable<String, Complex[][]>();
    	
    	Y120Matrix = getInterfaceY120Matrix();
    	
    	FieldMatrix<Complex>  y120FMatrix= MatrixUtils.createFieldMatrix(Y120Matrix);
    	//System.out.println("Y120 = \n"+y120FMatrix);
	     
	    int dim = boundaryBusIdAry.length;
	   
	    for(int i=0;i<dim;i++){
	    	     //Complex[][] busYself120 = MyUtil.creatComplex2DArray(3, 3);
	    	     
	    	     FieldMatrix<Complex> busYself120 = y120FMatrix.getSubMatrix(3*i,  3*i+2,3*i, 3*i+2);
	    	     
	    	     for(int j=0;j<dim;j++){
	    	    	 if(j!=i){
	    	    		 busYself120 = busYself120.add(y120FMatrix.getSubMatrix(3*i, 3*i+2, 3*j, 3*j+2));
	    	    	 }
	    	     }
	    	     		 
	    	     //transform each 3*3 sequence sub matrix to an impedance matrix ABC-coordinate
	    	     //Zabc=T*Z120*T^-1
	    	    // System.out.println("busYself120 = \n"+busYself120);
	    	     
	    	     Complex[][] busYselfAbc = Z120ToAbc(busYself120.getData());
	    	    //System.out.println("busYselfabc = \n"+busYselfAbc[0][0]+","+busYselfAbc[0][1]+","+busYselfAbc[0][2]);
	    	    
	    	     //invert zabc to get the bus selfY matrix in ABC coordinate  
	    	     boundaryBusSelfYabcTable.put(boundaryBusIdAry[i], busYselfAbc);
	    }
	    
    	
	    return boundaryBusSelfYabcTable;
    	
    }
	
	
	
	public boolean checkPosNegUnbalance(double percntThreshold){
		return false;
	}
	
	/**
	 * Calculate the external network equivalents for three phase balanced fault study within the internal network
	 * 
	 * CVS Storage format:
	 * ------------------------------------------------
	 * Boundary Bus Id  //BaseVolt(kV) // Thevenin Voltage Mag(PU) // Thevenin Voltage Angle (deg) // Z BusId1 //Z BusId2 ...
	 * BusId1
	 * BusId2
	 *   .
	 *   .
	 * BusIdm
	 * ------------------------------------------------
	 * @param csvfileName
	 * @throws Exception
	 */
	public void calcNSavePositiveEquivParam(String csvfileName) throws Exception{
		if(!(csvfileName.contains(".csv")||csvfileName.contains(".CSV"))){
			csvfileName +=".csv";
		}
		CSVWriter writer = new CSVWriter(new FileWriter(csvfileName), ',');
	    
		//set the internal subNetwork status to false;
		if(!this.useSubNetProcessor)
		    setSubNetworkStatus(true, false);
		
		Complex[][] posTheveninZMatrix = calcTheveninEquivZMatrix(SequenceCode.POSITIVE);
		//commented out 1/22/2015
		//Complex[][] zeroTheveninZMatrix = calcTheveninEquivZMatrix(SequenceCode.ZERO);
		
		//negative is assumed to be equal to positive
		//Complex[][] negTheveninZMatrix = calcTheveninEquivZMatrix(SequenceCode.NEGATIVE);
		
		Hashtable<String, Complex> equivCurInjTable=calBoundaryBusEquivCurInj();
		Hashtable<String, Complex> theveninVoltTable = calcTheveninVoltSource(equivCurInjTable,SequenceCode.POSITIVE,null);
		
		 // feed in your array (or convert your data to an array)
		String[] entries = new String[boundaryBusIdAry.length*4+4];
		entries[0]="Boundary Bus Id";
		entries[1]="BaseVolt(kV)";
		entries[2]="Thevenin Voltage Mag(PU)";
		entries[3]="Thevenin Voltage Angle (deg)";
		for(int i=0;i<boundaryBusIdAry.length;i++){
			entries[2*i+4] ="Zpos Real #"+boundaryBusIdAry[i];
			entries[2*i+5] ="Zpos Imag #"+boundaryBusIdAry[i];
		}
		
		int offIdx=boundaryBusIdAry.length*2+4;

		writer.writeNext(entries);
		
		int idx=0;
		for(String busId: boundaryBusIdAry){
			entries = new String[boundaryBusIdAry.length*4+4];
			Bus bus=net.getBus(busId);
	        entries[0]=busId;
	        entries[1]=Double.toString(bus.getBaseVoltage()/1000.0);
	        entries[2]=Double.toString(theveninVoltTable.get(busId).abs());
	        entries[3]=Double.toString(ComplexFunc.arg(theveninVoltTable.get(busId))*180/Math.PI);
	        for(int i=0;i<boundaryBusIdAry.length;i++){
				entries[2*i+4] = Double.toString(posTheveninZMatrix[idx][i].getReal());
				entries[2*i+5] = Double.toString(posTheveninZMatrix[idx][i].getImaginary());
			}

	        
	        idx++;
	        writer.writeNext(entries);
		}

		 writer.close();
		
	}
	
	/**
	 * Calculate the three-phase thevenin equivalent and output csv file format:
	 * 
	 * Boundary Bus Id ,zaa(R),zaa(X) , zab(R),zab(X) ,zac(R),zac(X) ,VaMag, VaAngle // ZijPos(R),ZijPos(X) , ZijZero(R),ZijZero(X),... //
	 * 
	 * 
	 * 
	 * @param csvfileName
	 * @throws Exception
	 */
	public void calcNSaveBoundaryBus3PhaseEquivParam(String csvfileName) throws Exception{
		if(!(csvfileName.contains(".csv")||csvfileName.contains(".CSV"))){
			csvfileName +=".csv";
		}
		CSVWriter writer = new CSVWriter(new FileWriter(csvfileName), ',');
		
		if(!this.useSubNetProcessor){
		   //set the internal subNetwork status to false;
		   setSubNetworkStatus(true, false);
		}
	    
		//Step-1: calculate the phase thevenin voltage based on the phase current injection
        Hashtable<String, Complex> boundaryBusCurInjTable = calBoundaryBusEquivCurInj();
		
		
		//Step-2: calculate phase Thevenin equivalent impedance
		Complex3x1[] thZselfAry = NumericalUtil4HybridSim.creatComplex3x1Array(boundaryBusIdAry.length);
		
		int i=0;
		for(String id:boundaryBusIdAry){
			Complex[][] busYabc =getBoundaryBusSelfYabcMatrix().get(id);
			// Zaa = 1.0/YaaShunt and assume Zaa=zbb=zcc 
			
		
			thZselfAry[i].a_0 =new Complex(1,0).divide(busYabc[0][0].add(busYabc[0][1]).add(busYabc[0][2]));
			//zab =zba
			thZselfAry[i].b_1 =new Complex(-1,0).divide(busYabc[0][1]);
			//zac =zca
			thZselfAry[i].c_2 =new Complex(-1,0).divide(busYabc[0][2]);
			i++;
			
		}
		
		
		//Step-3: calculate the bus mutual impedance in sequence format,i.e., positive and negative sequence
		Complex[][] posTheveninZMatrix = calcTheveninEquivZMatrix(SequenceCode.POSITIVE);
		Complex[][] zeroTheveninZMatrix = calcTheveninEquivZMatrix(SequenceCode.ZERO);
		

		//Step-4 calculate the three phase Thevenin equivalent open circuit voltages
		
		Complex3x1[] v120 = NumericalUtil4HybridSim.creatComplex3x1Array(boundaryBusIdAry.length);
		Complex3x1[] busI120 = NumericalUtil4HybridSim.creatComplex3x1Array(boundaryBusIdAry.length);
		i=0;
		for(String id:boundaryBusIdAry){
			v120[i].b_1 = net.getBus(id).getVoltage();
			v120[i].c_2 = new Complex(0,0);
			v120[i].a_0 = new Complex(0,0);
			
			busI120[i].b_1 = boundaryBusCurInjTable.get(id);
			busI120[i].c_2 = new Complex(0,0);
			busI120[i].a_0 = new Complex(0,0);
			
			i++;
			
		}
		//calculate the three phase Thevenin open source voltage
		double[] thrPhaseVthAry =get3PhaseTheveninVoltSourceAry(v120, busI120);
		
		
		 // feed in your array (or convert your data to an array)
		String[] entries = new String[boundaryBusIdAry.length*4+10];
		entries[0]="Boundary Bus Id";
		entries[1]="BaseVolt(kV)";
		entries[2]="Phase A Vth Mag(kV)";
		entries[3]="Phase A Angle (deg)";
		entries[4]="Zaa(R) pu";
		entries[5]="Zaa(X) pu";
		entries[6]="Zab(R) pu";
		entries[7]="Zab(X) pu";
		entries[8]="Zac(R) pu";
		entries[9]="Zac(X) pu";
		for(i=0;i<boundaryBusIdAry.length;i++){
			entries[2*i+10] ="Zpos (R) #"+boundaryBusIdAry[i];
			entries[2*i+11] ="Zpos (X) #"+boundaryBusIdAry[i];
		}
		
		int offIdx=boundaryBusIdAry.length*2+10;
		
		for(i=0;i<boundaryBusIdAry.length;i++){
			entries[2*i+offIdx] ="Zzero (R) #"+boundaryBusIdAry[i];
			entries[2*i+1+offIdx] ="Zzero(X) #"+boundaryBusIdAry[i];
		}
		writer.writeNext(entries);
		
		int idx=0;
		for(String busId: boundaryBusIdAry){
			entries = new String[boundaryBusIdAry.length*4+10];
			entries[0] = busId;
			entries[1] = Double.toString(net.getBus(busId).getBaseVoltage()/1000.0);
			entries[2] = Double.toString(thrPhaseVthAry[idx*6]);
			entries[3] = Double.toString(thrPhaseVthAry[idx*6+1]);
			entries[4] = Double.toString(thZselfAry[idx].a_0.getReal());
			entries[5] = Double.toString(thZselfAry[idx].a_0.getImaginary());
			entries[6] = Double.toString(thZselfAry[idx].b_1.getReal());
			entries[7] = Double.toString(thZselfAry[idx].b_1.getImaginary());
			entries[8] = Double.toString(thZselfAry[idx].c_2.getReal());
			entries[9] = Double.toString(thZselfAry[idx].c_2.getImaginary());
			
	        for(i=0;i<boundaryBusIdAry.length;i++){
				entries[2*i+10] = Double.toString(posTheveninZMatrix[idx][i].getReal());
				entries[2*i+11] = Double.toString(posTheveninZMatrix[idx][i].getImaginary());
			}
	        for(i=0;i<boundaryBusIdAry.length;i++){
				entries[2*i+offIdx] = Double.toString(zeroTheveninZMatrix[idx][i].getReal());
				entries[2*i+1+offIdx] = Double.toString(zeroTheveninZMatrix[idx][i].getImaginary());
			}
	        
	        idx++;
	        writer.writeNext(entries);
			
		}
		writer.close();
	}
	

	
	/**
	 * calculate Thevenin Equivalent impedance matrix, based on the sequence Y matrix
	 * source zii = 1/Yii
	 * multual zij =1/Yij  
	 * @param code
	 * @return
	 * @throws Exception
	 */
	public Complex[][] calcTheveninEquivZMatrix(SequenceCode code) throws Exception{
		Complex[][] seqYMatrix = null;
		Complex[][] seqZMatrix = null;
		switch(code){
		case POSITIVE:
			if(posZMatrix == null){
				seqZMatrix = calcInterfaceSeqZMatrix(SequenceCode.POSITIVE);
				
			}
			else {
				seqZMatrix = posZMatrix;
			}
			break;
		case NEGATIVE:
			if(negZMatrix == null){
			  seqZMatrix= calcInterfaceSeqZMatrix(SequenceCode.NEGATIVE);
			}
			else {
				seqZMatrix = negZMatrix;
			}
			break;
		   
		default:
			if(zeroZMatrix == null){
			  seqZMatrix= calcInterfaceSeqZMatrix(SequenceCode.ZERO);
			}
			else {
				seqZMatrix = zeroZMatrix;
			}
			break;
			
		}
	
		int dim=boundaryBusIdAry.length;
	    //calculate the sequence Y matrix
	    seqYMatrix = invMatrix(seqZMatrix);
	
	    Complex[][] theveninEquivZMatrix = new Complex[dim][dim];
	    
        //calculate the self and mutual impedance between the open circuit voltage sources
	    for(int i=0;i<dim;i++ ){
		    for(int j=0;j<dim;j++){
			   theveninEquivZMatrix[i][j] = (i==j)?new Complex(1.0,0).divide(getSelfAdmittance(seqYMatrix,i)):
				   new Complex(-1.0,0).divide(seqYMatrix[i][j]); 
		    }
	    }
	    return theveninEquivZMatrix;
		
	}
	
	
	/**
	 * 
	 * Calculate the thevenin voltage source
	 * Hashtable<String, Complex[]>
	 * String : boundary busId
	 * Complex: Thevenin equivalent open circuit voltage in complex format
	 * 
	 * @return Thevenin equivalent open circuit voltage in a Hashtable<String, Complex[]>
	 * @throws Exception 
	 */
	public Hashtable<String, Complex> calcTheveninVoltSource(Hashtable<String, Complex> equivCurInjTable,SequenceCode code, Complex[] seqVoltAry) throws Exception{
		
		Hashtable<String, Complex>  thVoltTable = new Hashtable<>();
		Complex[][] seqZMatrix = null;
		int dim=boundaryBusIdAry.length;
		int i=0;
		//initialize seq actual voltage with the input the sequence voltage array
		Complex[] Vactual = seqVoltAry;
		/*
		if(!isInternalSubNetworkProcessed){
			throw new Exception("Internal subNetwork is not processed yet, while internal bus list is required during calculating Thevenin equivalents");
		}
		*/
		
		switch(code){
		  case POSITIVE:
		    if(posZMatrix == null){
		    	seqZMatrix = calcInterfaceSeqZMatrix(SequenceCode.POSITIVE);
		    }
		    else
		    	seqZMatrix = posZMatrix;
		    Vactual = new Complex[dim];
		    
			for(String busId:boundaryBusIdAry){
				//actual bus voltage 
				Complex volt =this.net.getBus(busId).getVoltage();
				Vactual[i++]= volt;
			}
		    break;
		  case NEGATIVE:
			  if(negZMatrix == null){
			  seqZMatrix = calcInterfaceSeqZMatrix(SequenceCode.NEGATIVE);
			  }
			  else  
				  seqZMatrix =negZMatrix;
			  
		  case ZERO:
			  if(zeroZMatrix == null){
			  seqZMatrix = calcInterfaceSeqZMatrix(SequenceCode.ZERO);
			  }
			  else
				  seqZMatrix =zeroZMatrix;
		}
		
		
		Complex[] currInjAry= new Complex[dim];
		
		i=0;
		for(String busId:boundaryBusIdAry){
			Complex curInj = equivCurInjTable.get(busId);
			currInjAry[i++]=curInj;
		}
		//FieldVector<Complex> currInjVector = MatrixUtils.createFieldVector(currInjAry);
		
		//Open circuit Voltage:  Vopen = Vactual -Zpos*Iemt
		Complex[]Vopen = vectorSubtract(Vactual,(matrixMultiplyVector(seqZMatrix,currInjAry)));
		
		//calculate the sequence Y matrix
		Complex[][] seqYMatrix = invMatrix(seqZMatrix);
		
		//Iopen = Yseq*Vopen
		Complex[] Iopen =matrixMultiplyVector(seqYMatrix, Vopen);
		i=0;
		for(String busId:boundaryBusIdAry){
			
			Complex yiiComplex=getSelfAdmittance(seqYMatrix,i);
			thVoltTable.put(busId, Iopen[i].divide(yiiComplex));
			i++;
		}
		
		return thVoltTable;
	}
	
	/**
	 * Get sequence Thevenin voltage source
	 * 
	 * @param equivCurInjTable
	 * @param code
	 * @param seqVoltAry  only for calculating the negative and zero sequence, for positive sequence, it is not required. 
	 * @return
	 * @throws Exception
	 */
	public double[] getSeqTheveninVoltSourceAry(Hashtable<String, Complex> equivCurInjTable,SequenceCode code,Complex[] seqVoltAry) throws Exception{
		Hashtable<String, Complex> vthHashtable= calcTheveninVoltSource(equivCurInjTable,code,seqVoltAry);
		
		double[] vthSourceAry= new double[vthHashtable.size()*2];
		int i=0;
		for(String busId:boundaryBusIdAry){
			Complex vth =vthHashtable.get(busId);
			double vthMagKV = vth.abs()*net.getBus(busId).getBaseVoltage()/1000.0;
			double vthAngleDeg = ComplexFunc.arg(vth)*180/Math.PI;
			vthSourceAry[i++]=vthMagKV;
			vthSourceAry[i++]=vthAngleDeg;
		}
		
		return vthSourceAry;
		
	}
	
	/**
	 * get the ABC phase Thevenin Voltage source array at boundary buses, based on the boundary bus sequence voltage 
	 * and sequence current injections.
	 * 
	 * @param boundaryBusSeqVoltAry
	 * @param threeSeqCurInjAry
	 * @return
	 * @throws Exception
	 */
	public double[] get3PhaseTheveninVoltSourceAry(Complex3x1[] boundaryBusSeqVoltAry, Complex3x1[] threeSeqCurInjAry) throws Exception{
		
		
		Hashtable<String, Complex3x1> thrPhaseVthTable = calc3PhaseTheveninVoltSource(boundaryBusSeqVoltAry, threeSeqCurInjAry);
		
		double[] thrPhaseVthAry = new double[6*boundaryBusIdAry.length];
		int k =0;
		
		for(String busId:boundaryBusIdAry){
			double busPhaseBasekV =net.getBus(busId).getBaseVoltage()/1000.0/Math.sqrt(3);
			Complex3x1 vthAbc = thrPhaseVthTable.get(busId);
			Complex vthA=vthAbc.a_0;
			Complex vthB=vthAbc.b_1;
			Complex vthC=vthAbc.c_2;
			
			//Phase-A
			//convert from pu to kV 
			thrPhaseVthAry[k++] =vthA.abs()*busPhaseBasekV;
			//angle in deg
			thrPhaseVthAry[k++] =ComplexFunc.arg(vthA)*180/Math.PI;
			
			//Phase-B
			//convert from pu to kV 
			thrPhaseVthAry[k++] =vthB.abs()*busPhaseBasekV;
			//angle in deg
			thrPhaseVthAry[k++] =ComplexFunc.arg(vthB)*180/Math.PI;
			
			//Phase-C
			//convert from pu to kV 
			thrPhaseVthAry[k++] =vthC.abs()*busPhaseBasekV;
			//angle in deg
			thrPhaseVthAry[k++] =ComplexFunc.arg(vthC)*180/Math.PI;
			
		}
		return thrPhaseVthAry;
		
		
	}
	
	/**
	 * calculate the three phase thevenin voltage source, i.e.,VThabc . VThabc of each bus is saved to a Complex3x1 object.
	 * The result of all boundary buses is returned through a Hashtable<String, Complex3x1>, with boundary bus Id as the key.
	 * 
	 * 
	 * @param boundaryBusSeqVoltAry
	 * @param threeSeqCurInjAry
	 * @return
	 * @throws Exception
	 */
	public Hashtable<String, Complex3x1> calc3PhaseTheveninVoltSource(Complex3x1[] boundaryBusSeqVoltAry, Complex3x1[] threeSeqCurInjAry) throws Exception{
		
		Hashtable<String, Complex3x1>  thrPhaseTheveninVolt = new Hashtable<>();
		//1. calculate three sequence thevenin open circuit voltage
		//For each sequence Vopen = Vactual_seq -Zseq*Iemt_seq
		
		int dim =boundaryBusIdAry.length;
		Complex[] Vactual_pos   = new Complex[dim];
		Complex[] Vactual_neg   = new Complex[dim];
		Complex[] Vactual_zero   = new Complex[dim];
		Complex[] posCurrInjAry = new Complex[dim];
		Complex[] negCurrInjAry = new Complex[dim];
		Complex[] zeroCurrInjAry = new Complex[dim];
		
		for(int i=0;i<dim;i++){
			Vactual_pos[i] =boundaryBusSeqVoltAry[i].b_1; 
			Vactual_neg[i] =boundaryBusSeqVoltAry[i].c_2;
			Vactual_zero[i] =boundaryBusSeqVoltAry[i].a_0;
			
			posCurrInjAry[i]= threeSeqCurInjAry[i].b_1;
			negCurrInjAry[i]= threeSeqCurInjAry[i].c_2;
			zeroCurrInjAry[i]= threeSeqCurInjAry[i].a_0;
			
		}
		// positive sequence
		if(posZMatrix == null)
			posZMatrix = calcInterfaceSeqZMatrix(SequenceCode.POSITIVE);
	    
		
		Complex[]VopenPos = vectorSubtract(Vactual_pos,(matrixMultiplyVector(posZMatrix,posCurrInjAry)));
		
		// negative sequence
		if(negZMatrix == null)
			negZMatrix = calcInterfaceSeqZMatrix(SequenceCode.NEGATIVE);
		
		Complex[]VopenNeg = vectorSubtract(Vactual_neg,(matrixMultiplyVector(negZMatrix,negCurrInjAry)));
		
		// zero sequence
		if(zeroZMatrix == null)
           zeroZMatrix = calcInterfaceSeqZMatrix(SequenceCode.ZERO);
		
		Complex[]VopenZero = vectorSubtract(Vactual_zero,(matrixMultiplyVector(zeroZMatrix,zeroCurrInjAry)));
		
		if(Y120Matrix==null){
			getInterfaceY120Matrix();
		}
		Complex[] V120_open = new Complex[3*dim];
		
		int k=0;
		for(int i=0;i<dim;i++){
			V120_open[k++]=VopenPos[i];
			V120_open[k++]=VopenNeg[i];
			V120_open[k++]=VopenZero[i];		
		}
		
		//2. I120 = Y120*V120_open
		INorton120 = matrixMultiplyVector(Y120Matrix,V120_open);
		

		if(boundaryBusSelfYabcTable == null){
			getBoundaryBusSelfYabcMatrix();
		}
			
		//Hashtable<String, Complex[][]> selfYabcMatrixHashtable = getBoundaryBusSelfYabcMatrix();
		
		k=0;
		for(String busId:boundaryBusIdAry){
			//for each 3*1 sub portion, perform BusIabc = S*BusI120
			Complex[] busI120 = new Complex[3];
			busI120[0] = INorton120[k++];
			busI120[1] = INorton120[k++];
			busI120[2] = INorton120[k++];
			
			Complex[] busIabc = convert120ToAbc(busI120);

			// Vabc_open = Iabc/SelfYabc
			Complex[][]selfYabc = boundaryBusSelfYabcTable.get(busId);
			
			//System.out.println("selfYabc # "+busId);
			//MyUtil.printComplexMatrix(selfYabc);
			
			Complex3x1 Vabc_open = new Complex3x1();
            
			
			// original implementation
			Vabc_open.a_0 =busIabc[0].divide(getSelfAdmittance(selfYabc, 0));
			
			Vabc_open.b_1 =busIabc[1].divide(getSelfAdmittance(selfYabc, 1));
			Vabc_open.c_2 =busIabc[2].divide(getSelfAdmittance(selfYabc, 2));
            
            

			
			thrPhaseTheveninVolt.put(busId, Vabc_open);
		}
		
		return thrPhaseTheveninVolt;
	}
	
	/**
	 * It merges all three sequence voltages of each boundary bus and saves them to a Complex3x1.
	 * Three sequence voltages of all boundary buses are returned in a Complex3x1 array.
	 * 
	 * @param zeroSeqVolt
	 * @param negSeqVolt
	 * @return
	 * @throws Exception
	 */
	public Complex3x1[] extractBoundaryBus3SeqVoltAry(Hashtable<String, Complex> zeroSeqVolt,Hashtable<String, Complex> negSeqVolt) throws Exception{
		int dim = boundaryBusIdAry.length;
		Complex3x1[] boundaryBus3SeqVoltAry= NumericalUtil4HybridSim.creatComplex3x1Array(dim);
		
		//make sure the zero and negative sequence voltage hashtable completely
		// include all boundary buses
		if(!zeroSeqVolt.keySet().containsAll(boundaryBusIdList)){
			throw new Exception("Not all entries in boundary bus List are included in zero sequence votlage hashtable!");
		}
		
		if(!negSeqVolt.keySet().containsAll(boundaryBusIdList)){
			throw new Exception("Not all entries in boundary bus List are included in negative sequence votlage hashtable!");
		}
		
		int i=0;
		for(String busId: boundaryBusIdAry){
			//zero sequence
			boundaryBus3SeqVoltAry[i].a_0 = zeroSeqVolt.get(busId);
			//positive sequence
			boundaryBus3SeqVoltAry[i].b_1 = net.getBus(busId).getVoltage();
			//negative sequence
			boundaryBus3SeqVoltAry[i].c_2 = negSeqVolt.get(busId);
			i++;
		}
		return boundaryBus3SeqVoltAry;
		
	}
	
	
	/**
	 * The input threeSeqCurrInjAry is in the form of {[bus1CurSubAry],...,[busNCurSubAry]}
	 * and each sub array is in the form of [busiCurMag_pos,busiCurAng_pos,busiCurMag_neg,busiCurAng_neg,busiCurMag_zero,busiCurAng_zero]
	 * 
	 * Thus the dimension of the input array must be six times of the number of boundary buses.
	 * 
	 * @param threeSeqCurrInjAry
	 * @return
	 * @throws Exception 
	 */
	public Complex3x1[] extractBoundaryBus3SeqCurrInjAry(double[] threeSeqCurrInjAry ) throws Exception{
		int dim = boundaryBusIdAry.length;
		Complex3x1[] boundaryBus3SeqCurrInjAry= NumericalUtil4HybridSim.creatComplex3x1Array(dim);
		
		//check the array dimension
		if(dim !=threeSeqCurrInjAry.length/6){
			throw new Exception("Current injection array dimension is not consistent with boundary bus number,"
					+ "it should be 6 times of the number of boundary buses");
		}
		
		//prepare the array 
		int k = 0;
		for(int i=0; i<dim;i++){
			
			//positive sequence 
			double IposMag = threeSeqCurrInjAry[k++];
			//angle in rad
			double IposAngle = threeSeqCurrInjAry[k++];
			
			//negative sequence
			double InegMag = threeSeqCurrInjAry[k++];
			double InegAngle = threeSeqCurrInjAry[k++];
			
			//zero sequence
			double IzeroMag = threeSeqCurrInjAry[k++];
			double IzeroAngle = threeSeqCurrInjAry[k++];
			
			//convert Imag/_angle to complex form 
			boundaryBus3SeqCurrInjAry[i].a_0 = ComplexFunc.polar(IzeroMag, IzeroAngle);
			boundaryBus3SeqCurrInjAry[i].b_1 = ComplexFunc.polar(IposMag, IposAngle);
			boundaryBus3SeqCurrInjAry[i].c_2 = ComplexFunc.polar(InegMag, InegAngle);
			
		}
		
		return boundaryBus3SeqCurrInjAry;
		
	}
	/**
	 * prepare the sequence current injection hashtable.
	 * 
	 * @param code
	 * @param threeSeqCurrInjAry
	 * @return
	 * @throws Exception 
	 */
	public Hashtable<String, Complex> prepSeqCurrInjHashTable(SequenceCode code,Complex3x1[] thrSeqCurrInjAry) throws Exception{
		
		Hashtable<String, Complex> seqCurInjTable = new Hashtable<>();
		
		
		int i=0;
		for(String busId: boundaryBusIdAry){
		   Complex seqCurrInj = null;
		   if(code==SequenceCode.NEGATIVE){
			   seqCurrInj =thrSeqCurrInjAry[i++].c_2; 
		   }
		   else if(code == SequenceCode.ZERO){
			   seqCurrInj =thrSeqCurrInjAry[i++].a_0;
		   }
		   else{
			   seqCurrInj =thrSeqCurrInjAry[i++].b_1;
		   }
		   seqCurInjTable.put(busId, seqCurrInj);
		}
		return seqCurInjTable;
	}
	
	/**
	 * extract only the positive sequence current array from the 
	 * three sequence current injection array
	 * @param threeSeqCurrInjAry
	 * @return
	 */
	public double[] extractPosSeqCurAry(double[] threeSeqCurrInjAry){
		int dim = boundaryBusIdAry.length;
		double[] posSeqCurAry = new double[dim*2];
		
		int k=0;
		for(int i=0; i<dim*2;i++){
			posSeqCurAry[i++] = threeSeqCurrInjAry[k++];
			posSeqCurAry[i]   = threeSeqCurrInjAry[k++];
			k=k+4; // skip the negative and zero sequence
		}
		return posSeqCurAry;
		
	}
	
	/**
	 * get a sequence self admittance of a bus if he input admittance is the system boundary admittance with 3m* 3m; 
	 * or  the phase primitive admittance, if the input matrix is a 3*3 self admittance matrix of a bus, with index i = 0->2, for phase a->c
	 * @param seqYMatrix
	 * @param i
	 * @return
	 */
	public Complex getSelfAdmittance(Complex[][] seqYMatrix,int i){
		int dim =seqYMatrix[0].length;
		Complex yiiComplex= new Complex(0,0);
		for(int j=0;j<dim;j++){
			yiiComplex=yiiComplex.add(seqYMatrix[i][j]);
		}
		return yiiComplex;
	}
	
	
	/**
	 * calculate the boundary bus equivalent Load
	 * @param internalBusList
	 * @return
	 */
	public Hashtable<String, Complex> calBoundaryBusEquivLoad(List<String> internalBusList){
      Hashtable<String, Complex> equivLoadTable = calBoundaryBusEquivCurInj();
    	
    	
    	for(String busId:boundaryBusIdAry){
    		AclfBus bus= net.getBus(busId);
    		Complex busVolt = bus.getVoltage();
    		Complex equivLoadComplex =busVolt.multiply(equivLoadTable.get(busId).conjugate()).multiply(-1);
    		equivLoadTable.put(busId, equivLoadComplex);
    	}
    	return equivLoadTable;
	}
	
	
	/**
	 * calculate boundary bus equivalent current injection into the external network
	 * @param internalBusList
	 * @return
	 */
    public Hashtable<String, Complex> calBoundaryBusEquivCurInj(){
    	Hashtable<String, Complex> equivCurInj = new Hashtable<>();
    	/*
    	 * Total bus current injection equals to the sum of the current flowing 
    	 * out of the bus into the external network, via the branches connected to the buses of the external network.
    	 */
    	
    	for(String busId:boundaryBusIdAry){
    		AclfBus bus= net.getBus(busId);
    		Complex busVolt = bus.getVoltage();
    		
    		//initialized the bus injection current at the boundary bus from the internal network side; 
    		Complex equivPowerInjComplex = new Complex(0,0);
    		Complex equivCurComplex = new Complex(0,0);
    		
    		if(bus.isActive()){
    		
    		  // iterate over the connected branch to sum up the injection current 
    		  // flowing through these connected branches into the external network
    		   for(Branch bra: bus.getBranchList()){
    			   if(bra instanceof AclfBranch){
    			    AclfBranch branch = (AclfBranch) bra;

    			    if(!bra.isGroundBranch() && bra.isActive()){
    			      
    			    	
    			    	// boundary bus-> external bus

    			       if(bra.getFromBus().getId().endsWith(busId) && !internalNetworkBusList.contains(bra.getToBus().getId())
    			    		   && !boundaryBusIdList.contains(bra.getToBus().getId())){

    			    	   equivPowerInjComplex = equivPowerInjComplex.add(branch.powerFrom2To());
    			    	//equivCurComplex = equivCurComplex.add((branch.powerFrom2To().divide(busVolt).conjugate()));
    			    	
    			       } 
    			       // external bus-> boundary bus
    			       else if(bra.getToBus().getId().endsWith(busId) && !internalNetworkBusList.contains(bra.getFromBus().getId())
    			    		   && !boundaryBusIdList.contains(bra.getFromBus().getId())){

    			    	   equivPowerInjComplex = equivPowerInjComplex.add(branch.powerTo2From());
    			    	   //equivCurComplex = equivCurComplex.add((branch.powerTo2From().divide(busVolt).conjugate()));
    			       }
    			       
    			       
    			    }
    		     }
    		   }
    		   
    		}
    		equivCurComplex =  equivPowerInjComplex.divide(busVolt).conjugate();
    		equivCurInj.put(busId, equivCurComplex);
    	}
    	return equivCurInj;
    }
    

	/**
	 * convert input current or voltage array in 120-coordinate to the corresponding ABC-coordinate
	 * 
	 * Iabc =s*I120
	 * 
	 * @return
	 */
    private Complex[] convert120ToAbc(Complex[] v120){
    	FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(T);
    	return fmT.operate(v120);
    }
    
    /**
     * transform impedance matrix in 120 coordinate to ABC coordinate
     * @param z120
     * @return
     */
	private Complex[][] Z120ToAbc(Complex[][] z120){
		//Zabc=T*Z120*T^-1
		FieldMatrix<Complex> fmZ120= MatrixUtils.createFieldMatrix(z120);
		FieldMatrix<Complex> fmT= MatrixUtils.createFieldMatrix(T);
		FieldMatrix<Complex> fmTinv= MatrixUtils.createFieldMatrix(Tinv);
		Complex[][] Zabc = fmT.multiply(fmZ120).multiply(fmTinv).getData();
	    return Zabc;
	}
	/**
	 * invert matrix which is input as 2-dimensional array
	 * @param m
	 * @return
	 * @throws Exception
	 */
	private Complex[][] invMatrix(final Complex[][] m) throws Exception{
		
		Complex[][] inv = null;
		//check dimension
		
		if(m.length == m[0].length){
			FieldMatrix<Complex> temp = MatrixUtils.createFieldMatrix(m);
			FieldLUDecomposition<Complex> lu = new FieldLUDecomposition<>(temp);
			inv=lu.getSolver().getInverse().getData();
			
		}
		else {
			throw new Exception("Input matrix is not a squre matrix, please check! row #, column # = "+m.length+" , "+m[0].length);
		}
		
		return inv;
	}
	
	/**
	 * matrix multiply a vector
	 * @param m
	 * @param cmplxVector
	 * @return
	 */
	private Complex[] matrixMultiplyVector(Complex[][] m, Complex[] cmplxVector){
		FieldMatrix<Complex> temp = MatrixUtils.createFieldMatrix(m);

		return temp.operate(cmplxVector);
	}
	
	
	private Complex[] vectorAdd(Complex[] cmplxVector1, Complex[] cmplxVector2){
		FieldVector<Complex> tempV1 = MatrixUtils.createFieldVector(cmplxVector1);
		FieldVector<Complex> tempV2 = MatrixUtils.createFieldVector(cmplxVector2);
		return tempV1.add(tempV2).toArray();
	}
	
	private Complex[] vectorSubtract(Complex[] cmplxVector1, Complex[] cmplxVector2){
		FieldVector<Complex> tempV1 = MatrixUtils.createFieldVector(cmplxVector1);
		FieldVector<Complex> tempV2 = MatrixUtils.createFieldVector(cmplxVector2);
		return tempV1.subtract(tempV2).toArray();
	}
	
	private void initComplexArray(Complex[][] m){
		for(int i=0;i<m.length;i++){
			for(int j=0;j<m[0].length;j++){
				m[i][j] = new Complex(0,0);
			}
		}
		
	}
	
	public List<String> getExternalNetBoundaryBusIdList(){
		return this.boundaryBusIdList;
	}

	
	public List<String> getInternalNetBoundaryBusIdList(){
		return this.internalBoundaryBusIdList;
	}
}
