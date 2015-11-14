package edu.asu.hybridSimu;

import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.FileUtil;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;

import edu.asu.hybridSim.util.NumericalUtil4HybridSim;
import edu.asu.hybridSimu.pssl.HybridSimuConfigBean;


/**
 * Hybrid Simulation helper is mainly defined for two functions:
 * 
 * 1) automatically create the internal network from the original full network
 * based on the provided boundary bus information and one internal bus, if any.
 * 
 * 2) calculate the Thevenin equivalents, both positive sequence and three-phase,
 * of the external network for EMT simulation.  
 * 
 *
 */
public class HybridSimuHelper {
	
	private DStabilityNetwork net = null;
 
	private String[] boundaryBusIdAry = null;
	private List<String> boundaryBusList =null;
	private List<String> internalNetworkBusList =null;
	
	private boolean isClientConnected = false;
	private boolean isPositiveSeqOnly = false;
	private UnitType  seqCurrentUnit = UnitType.PU;
	
	private Hashtable<String, Complex> boundaryBusCurInjTable =null;
	private Hashtable<String, Complex3x1> boundaryBus3SeqCurInjTable =null;
	
	private NetworkEquivalentHelper netEquivHelper = null;
	private SocketServerHelper ssHelper = null;
	private ProtocolSwitchHelper  protocolHelper = null;
	private SequenceNetworkSolver seqNetHelper = null;
	
	Hashtable<String, Complex> negSeqVoltTable = null;
    Hashtable<String, Complex> zeroSeqVoltTable = null; 
    
    private  Complex[] posSeqCurrAry = null;
    private  Complex3x1[] thrSeqCurrAry = null;
    private boolean debugMode = false;
    
    // for storing data of boundary current injection
    
    private int curInjDataSize = 0;
    private double[] curInjDataAry = null;
    
    // for storing data other than boundary current injection
    private int miscDataSize = 0;
    private double[] miscDataAry = null;
    

	public HybridSimuHelper(NetworkEquivalentHelper netEqvHelper){
		this.netEquivHelper = netEqvHelper;
		this.net = (DStabilityNetwork) netEquivHelper.getNetwork();
		
		if(this.netEquivHelper.getSubNetHelper()!=null){
		    this.boundaryBusIdAry = this.netEquivHelper.getSubNetHelper().getBoundaryBusIdAry();
		    this.internalNetworkBusList = this.netEquivHelper.getSubNetHelper().getInternalNetworkBusList();
	    }
		else{ // obtain it from this.netEquivHelper
			// only boundaryId is required as the procInternalNetwork is not necessary anymore
			this.boundaryBusIdAry = this.netEquivHelper.getExternalNetBoundaryBusIdList().toArray(new String[0]);
		}
		this.protocolHelper = new ProtocolSwitchHelper();
	}
	
	public void setDebugMode(boolean debug){
		this.debugMode = debug;
	}
	
	
	
	/**
	 * This is the main method for hybrid simulation, it can be extended to provide more control 
	 * over the processing of hybrid simulation as all the information stored in HybridSimuConfigBean 
	 * can be used. 
	 * @param dstabAlgo   dynamic simulation algorithm object, it refers to the Dstab network and stores all setting for TS simulation
	 * @param hsConfigBean  hybrid simulation configuration JSON bean
	 * @return
	 * @throws Exception 
	 */
	public boolean runHybridSimu(DynamicSimuAlgorithm dstabAlgo,HybridSimuConfigBean hsConfigBean) throws Exception{
		
		//initialize the EMT-TS interaction protocol helper
		this.protocolHelper.setInteractionTimeStep(dstabAlgo.getSimuStepSec()*1000.0);
		
		boolean runParallelMode = true;
		boolean isPingPongStage = true;
		int itrCnt = 0;
		int protocolType = 0; //parallel by default;
		int lastProtocol = 0; 
		
		int byPassSteps = (int)(0.0167/dstabAlgo.getSimuStepSec())+1;
		int byPassCounter = 0;
		boolean byPassMode = false;
		double faultStartTime = hsConfigBean.eventStartTimeSec;
		double faultClearTime = hsConfigBean.eventStartTimeSec+hsConfigBean.eventDurationSec;
		
		if(faultStartTime <=0.0 || faultClearTime<=0.0){
			faultStartTime = 9999;
			faultClearTime = 9999;
			 IpssLogger.getLogger().info("Either the fault starting or ending time is <=0.0, the fault setting is not valid!");
		}
		if(faultStartTime >= faultClearTime){
			faultStartTime = 9999;
			faultClearTime = 9999;
			 IpssLogger.getLogger().info("Either the fault starting time >= ending time , the fault setting is not valid!");
		}
		
		
		InteractionProtocol ip = hsConfigBean.protocol_type;
		
		
		if(ip== InteractionProtocol.Series){
			protocolType = 1;
			lastProtocol = 1; 
		}
		
		double[] sendVthAry =null;
		
		while(this.connectClient()){ // while the EMT simulator connects to this module and keep sending data
		
		  //There is a Ping pong stage at the very beginning, which is only for checking
	      //whether the TS side is ready for hybrid simulation and the communication is properly set up
			
		 if(!isPingPongStage){ 
			 
			 if(debugMode) System.out.print("\n\niteration ="+itrCnt);
			 IpssLogger.getLogger().info("\n\niteration ="+itrCnt++);
			 
		
			 /*
			  * step-1 receive data and update the boundary current injection table 
			  */
				recvDataAndUpdateCurrentInjectionTable() ;
			
			
			/*
			 * step-2 determine the protocol
			 */
			
			if(ip == InteractionProtocol.Combined){
			     protocolType = this.isPositiveSeqOnly?protocolHelper.determineNewProtocol(this.posSeqCurrAry)
					                                 :protocolHelper.determineNewProtocol(this.thrSeqCurrAry);
		  	 
				 
		  		  if (protocolType ==1) {
		  			  //System.out.println("using series protocol");
		  			if(debugMode) System.out.println(", protocol type = 1, series \n");
		  			IpssLogger.getLogger().info(", protocol type = 1, series \n");
		  			
		  		  }else{
		  			 // System.out.println("using parallel protocol");
		  			if(debugMode) System.out.println(", protocol type = 0, parallel \n");
		  			IpssLogger.getLogger().info(", protocol type = 0, parallel \n"); 
		  		
		  		  }
			}
			
	  		   if(protocolType ==1){  // run series mode
	  			  runParallelMode=false;
	  			  
		  			/*
		  		     * NOTE: Because of the inherent 1-cycle moving window property of FFT, the current injections 
		  		     *       obtained by the FFT within the first cycle after the critical state changes are not accurate,
		  		     *       thus, it is proposed to delay the TS simulation at these state change moments
		  		     */
		  		    if(Math.abs(dstabAlgo.getSimuTime()-faultStartTime)<1.0E-4 ||
		  		    		Math.abs(dstabAlgo.getSimuTime()-faultClearTime)<1.0E-4){
		  		    	
		  		    	IpssLogger.getLogger().info("Detailed Subsystem TS simulation starts bypassing at "+dstabAlgo.getSimuTime());
		  		    	byPassMode = true;
		  		    }	
		  		     
		  		    if(byPassMode){
		  		    	// start to count the steps that have been bypassed.  
		  		    	byPassCounter++;
		  		    	
		  		    	if(byPassCounter == byPassSteps){
		  		    		
		  		    		for(int i=0; i<byPassSteps;i++){
		  		    			 dstabAlgo.solveDEqnStep(true);
		  		    		}
		  		    		
		  		    		byPassCounter = 0;
		  		    		byPassMode = false;
		  		    	}
		  		    	
		  		    }
		  		    else{ // if not these two state changing moments, run as normal
		  			
		 	  		  dstabAlgo.solveDEqnStep(true);
		  		    }
	  		   }
	  		   else{
	  			 runParallelMode = true;
	  	       }
			
			/*
			 * step-3 run simulation and then calculate the network equivalent if protocol is series, 
			 * otherwise update the network equivalent first, then run dynamic simulation
			 */
				
			if(byPassCounter==0){
			
				// for three-phase generic applications, three-phase Thevenin equivalent voltage sources are updated!
				if(!this.isPositiveSeqOnly){  
		
			  		  //extract three sequence voltages of boundary buses.
			  		  Complex3x1[] thrSeqVoltAry = extractBoundaryBus3SeqVoltAry(this.zeroSeqVoltTable, this.negSeqVoltTable);
			  		  
			  		  //calculate the three phase Thevenin equivalent voltage source at the boundary buses
			  		  sendVthAry =get3PhaseTheveninVoltSourceAry(thrSeqVoltAry,this.thrSeqCurrAry);
					
				}
				else{ // calculate positive sequence network equivalent 
					   sendVthAry = getSeqTheveninVoltSourceAry(boundaryBusCurInjTable,SequenceCode.POSITIVE, null);
				}
			}	
			
			/*
			 * step-4 send data to the EMT software or the client side 
			 */
		  	 ssHelper.sendDoubleArrays(sendVthAry);
		  	 
		  	// if(debugMode) System.out.println("Sent Data: "+Arrays.toString(sendVthAry));
		  		  
		  		  
		  // run the dstab simulation at the last step if parallel interaction model is chosen for the present simulation step!	  
		    if(runParallelMode){ 
		    	//TODO commented out to test bypassing function after the fault is applied or cleared. 
		    	// dstabAlgo.solveDEqnStep(true);
		    	
		  	  
	  			/*
	  		     * NOTE: Because of the inherent 1-cycle moving window property of FFT, the current injections 
	  		     *       obtained by the FFT within the first cycle after the critical state changes are not accurate,
	  		     *       thus, it is proposed to delay the TS simulation at these state change moments
	  		     */
	  		    if(Math.abs(dstabAlgo.getSimuTime()-faultStartTime)<1.0E-4 ||
	  		    		Math.abs(dstabAlgo.getSimuTime()-faultClearTime)<1.0E-4){
	  		    	
	  		    	IpssLogger.getLogger().info("Detailed Subsystem TS simulation ttarts bypassing at "+dstabAlgo.getSimuTime());
	  		    			
	  		    	// start to count the steps that have been bypassed.  
	  		    	byPassCounter++;
	  		    	
	  		    	if(byPassCounter == byPassSteps){
	  		    		
	  		    		for(int i=0; i<byPassSteps;i++){
	  		    			 dstabAlgo.solveDEqnStep(true);
	  		    		}
	  		    		
	  		    		byPassCounter = 0;
	  		    	}
	  		    	
	  		    }
	  		    else{ // if not these two state changing moments, run as normal
	  			
	 	  		  dstabAlgo.solveDEqnStep(true);
	  		    }
		    	
		    	
		    	
		    }
		    
		    /*
		     *  step-5 check the simulation time to see whether the target total simulation time is met. If yes, stop the hybrid simulation
		     */
		    
	  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
	  			  IpssLogger.getLogger().info("Simulation Time is up, and simulation succefully ends!");
	  			  break;
	  		      
	  		  }
		    
		  }// isPingPongStage
		 else{
			 isPingPongStage = false; // pingpong stage ends, run hybrid simulation for the rest of the simulation time
			
			 ssHelper.sendString("Pong");
		 }
		} // end while-loop
		
		// end the sockect connection
		 this.ssHelper.closeSocket();
		 
		return true;
		
	}
	
	
	/**
	 * This is the main method for hybrid simulation, all configurations are set before calling this method 
	 * @param dstabAlgo
	 * @return
	 * @throws Exception 
	 */
	public boolean runHybridSimu(DynamicSimuAlgorithm dstabAlgo,InteractionProtocol ip) throws Exception{
		
		//initialize the EMT-TS interaction protocol helper
		this.protocolHelper.setInteractionTimeStep(dstabAlgo.getSimuStepSec()*1000.0);
		
		boolean runParallelMode = true;
		boolean isPingPongStage = true;
		int itrCnt = 0;
		int protocolType = 0; //parallel by default;
		int lastProtocol = 0; 
		
		
		if(ip== InteractionProtocol.Series){
			protocolType = 1;
			lastProtocol = 1; 
		}
		
		while(this.connectClient()){ // while the EMT simulator connects to this module and keep sending data
		
		  //There is a Ping pong stage at the very beginning, which is only for checking
	      //TS side is ready for hybrid simulation and the communication is properly set up
			
		 if(!isPingPongStage){ 
			 
			 if(debugMode) System.out.print("\n\niteration ="+itrCnt);
			 IpssLogger.getLogger().info("\n\niteration ="+itrCnt++);
			 
		
			 /*
			  * step-1 receive data and update the boundary current injection table 
			  */
				recvDataAndUpdateCurrentInjectionTable() ;
			
			
			/*
			 * step-2 determine the protocol
			 */
			
			if(ip == InteractionProtocol.Combined){
			     protocolType = this.isPositiveSeqOnly?protocolHelper.determineNewProtocol(this.posSeqCurrAry)
					                                 :protocolHelper.determineNewProtocol(this.thrSeqCurrAry);
		  	 
				 
		  		  if (protocolType ==1) {
		  			  //System.out.println("using series protocol");
		  			if(debugMode) System.out.println(", protocol type = 1, series \n");
		  			IpssLogger.getLogger().info(", protocol type = 1, series \n");
		  			
		  		  }else{
		  			 // System.out.println("using parallel protocol");
		  			if(debugMode) System.out.println(", protocol type = 0, parallel \n");
		  			IpssLogger.getLogger().info(", protocol type = 0, parallel \n"); 
		  		
		  		  }
			}
			
	  		   if(protocolType ==1){  // run series mode
	  			  runParallelMode=false;
	  			
	 	  		  dstabAlgo.solveDEqnStep(true);
	  		   }
	  		   else{
	  			 runParallelMode = true;
	  	       }
			
			/*
			 * step-3 run simulation and then calculate the network equivalent if protocol is series, 
			 * otherwise update the network equivalent first, then run dynamic simulation
			 */
				
			double[] sendVthAry =null;
			
			// for three-phase generic applications, three-phase Thevenin equivalent voltage sources are updated!
			if(!this.isPositiveSeqOnly){  
	
		  		  //extract three sequence voltages of boundary buses.
		  		  Complex3x1[] thrSeqVoltAry = extractBoundaryBus3SeqVoltAry(this.zeroSeqVoltTable, this.negSeqVoltTable);
		  		  
		  		  //calculate the three phase Thevenin equivalent voltage source at the boundary buses
		  		  sendVthAry =get3PhaseTheveninVoltSourceAry(thrSeqVoltAry,this.thrSeqCurrAry);
				
			}
			else{ // calculate positive sequence network equivalent 
				   sendVthAry = getSeqTheveninVoltSourceAry(boundaryBusCurInjTable,SequenceCode.POSITIVE, null);
			}
				
			
			/*
			 * step-4 send data to the EMT software or the client side 
			 */
		  	 ssHelper.sendDoubleArrays(sendVthAry);
		  	 
		  	// if(debugMode) System.out.println("Sent Data: "+Arrays.toString(sendVthAry));
		  		  
		  		  
		  // run the dstab simulation at the last step if parallel interaction model is chosen for the present simulation step!	  
		    if(runParallelMode){ 
		    	 dstabAlgo.solveDEqnStep(true);
		    }
		    
		    /*
		     *  step-5 check the simulation time to see whether the target total simulation time is met. If yes, stop the hybrid simulation
		     */
		    
	  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
	  			  IpssLogger.getLogger().info("Simulation Time is up, and simulation succefully ends!");
	  			  break;
	  		      
	  		  }
		    
		  }// isPingPongStage
		 else{
			 isPingPongStage = false; // pingpong stage ends, run hybrid simulation for the rest of the simulation time
			
			 ssHelper.sendString("Pong");
		 }
		} // end while-loop
		
		// end the sockect connection
		 this.ssHelper.closeSocket();
		 
		return true;
		
	}
	

	
	
	/**
	 * There are two modes supported,i.e., 1) positive-sequence only mode (three-phase balanced application), 2) three-phase generic mode 
	 *  (balanced and unbalanced system conditions or faults within the internal network are supported)
	 *  
	 * @param isPositiveSeqOnly
	 */
	public void setPosSeqEquivalentMode(boolean isPositiveSeqOnly){
		this.isPositiveSeqOnly =isPositiveSeqOnly;
		
		if(this.net.isPositiveSeqDataOnly()){
			if(!isPositiveSeqOnly)
				try {
					throw new Exception("The input mode indicates three-phase hybrid simulation, yet the network data includes only positive sequence data");
				} catch (Exception e) {
				
					e.printStackTrace();
				}
		}
		else{ // for three-phase generic hybrid simulation, the negative and zero-sequence data is required, which 
			  // is calculated by the sequence network helper
			this.seqNetHelper = new SequenceNetworkSolver(this.net, this.boundaryBusIdAry);
		}
	}
	
	
	
	/*
	 * -------------------------------------------------------------------------------------------------------------------------
	 *                 This part relates to socket server setting
	 * 
	 * --------------------------------------------------------------------------------------------------------------------------
	 */
	
	
	
	/**
	 * set up the server socket for communicating with the EMT simulator
	 * @param port
	 * @param timeOutms
	 */
	public void setupServerSocket(int port, int timeOutms){
		this.ssHelper = new SocketServerHelper();
		this.ssHelper.createServerSokect(port, timeOutms);
	}
	
	
	/**
	 * connect to the EMT simulator by accepting the socket
	 */ 
	public boolean connectClient() throws IOException{
		return this.isClientConnected = (this.ssHelper.getClientSocket()!=null);
	}
	
    /**
     * Set the unit of the sequence current injections from the EMT side. 
     * 
     * PU is the default unit type. If other unit is used, e.g., A, or KA, 
     * It is required to set the unit type here before starting the hybrid 
     * simulation.
     *   
     * @param unit  - the unit of the sequence current injections
     */
	public void setSeqCurrentUnit(UnitType unit){
		if(unit==UnitType.Amp || unit==UnitType.kAmp ||unit==UnitType.PU)
		      seqCurrentUnit = unit;
		else
			try {
				throw new Exception("The unit of the sequence current set from EMT simulator can be only A, kA or PU, "
						+ "others are not accepted!");
			} catch (Exception e) {
				
				e.printStackTrace();
			}
		
	}
	
	/**
	 * Currently two format of message is supported by the message parser implemented in the SocketServerHelper, i.e.,
	 * 1) purely comma separate values(csv) string
	 * 2) message size header, which indicates the size (byte array size) of the csv String, followed by the csv string.
	 * This design is based on the TLV (Type-length-value) format of binary protocol design, with the type information omitted. 
	 * For more info, see http://en.wikipedia.org/wiki/Type-length-value
	 * 
	 * @param flag
	 */
	public void enableMsgSizeHeader(boolean flag){
		this.ssHelper.enableMsgSizeHeader(flag);
	}
	
	/**
	 * receive the csv format data arrays  sent from the client side through the connected socket,
	 * Make sure the socket is set up and connected before calling this method, otherwise, error will occur.
	 * @return
	 */
	public double[] recieveCSVDoubleArrays(){
		if(!this.isClientConnected)
			try {
				throw new IOException("No client socket is connected yet, no Double arrays can be recieved!");
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		   return ssHelper.receiveDoubleArrays();
		
	}
	
	
	/*
	 * ------------------------------------------------------------------------------------------------------------------------
	 *           The following part is related to internal/external subNetwork processing
	 *    
	 *--------------------------------------------------------------------------------------------------------------------------    
	 */
	public Hashtable<String, Complex> procInternalSubNetwork() throws Exception{
		
		
		//set the status of buses and branches belongs to the internal network to false
		for(String busId:internalNetworkBusList){
			net.getBus(busId).setStatus(false);
		}
		for(String branchId:this.netEquivHelper.getSubNetHelper().getInternalNetworkBranchList()){
			net.getBranch(branchId).setStatus(false);
		}

		//set the boundary buses equivalent load, it is required that the load flow is converged
	     if(!net.isLfConverged()){
	    	 throw new Exception("Load flow is not converged yet before trying to calculate equivalent load of "
	    	 		+ "boundary buses, please check!");
	     }

	 
		//calculate the boundary bus current injection and return it for DStab simulation
		boundaryBusCurInjTable = this.netEquivHelper.calBoundaryBusEquivCurInj();
		
	     //Need to set the load/gen/switchShunt at the boundary bus to be offline
		  for(String busId:this.boundaryBusIdAry){
		  		DStabBus dsBus = net.getBus(busId);
		  		for(AclfLoad load: dsBus.getContributeLoadList()){
		  			load.setStatus(false);
		  		}
		  		for(AclfGen gen: dsBus.getContributeGenList()){
		  			gen.setStatus(false);
		  		}
		  	}
		  	
		 return boundaryBusCurInjTable;

	}
	
	public NetworkEquivalentHelper getNetEquivHelper() {
		return netEquivHelper;
	}



	public void setNetEquivHelper(NetworkEquivalentHelper netEquivHelper) {
		this.netEquivHelper = netEquivHelper;
	}
	
	public void setProcolSwitchHelper(ProtocolSwitchHelper swchHelper){
		 this.protocolHelper = swchHelper;
			
	}
	
	public ProtocolSwitchHelper getProtocolSwitchHelper(){
		return this.protocolHelper;
	}

	
	
	/*
	 * ------------------------------------------------------------------------------------------------------------------
	 *       The following part is related to network equivalent related data processing and calculation
	 * 
	 * 
	 * ---------------------------------------------------------------------------------------------------------------------
	 * 
	 */
	
	

	/**
	 * receive data and update the boundary current injection table used for dynamic simulation
	 * 
	 * @throws Exception
	 */
	private void recvDataAndUpdateCurrentInjectionTable() throws Exception{
		
		 double[] recvDataAry=recieveCSVDoubleArrays();
		 
		 //System.out.println("RECV Data: "+Arrays.toString(recvDataAry));
		// if(debugMode) System.out.println("RECV Data: "+Arrays.toString(recvDataAry));
 		  
		 if(this.isPositiveSeqOnly){
  		 //convert the sequence current data array to complex form and in pu 
		  posSeqCurrAry= buildPosSeqCurrInjectAry(recvDataAry,this.seqCurrentUnit);
	             
	  		
	  	 // use sequence current injection array to update bus injection table
	  	 updatePosSeqBusCurInjTable(posSeqCurrAry, boundaryBusCurInjTable);
	  	 
		 }
		 else{ // current is of three-sequence
			// System.out.print("Seq Current Unit:"+this.seqCurrentUnit);
			 //convert the input data to complex form
	  		 thrSeqCurrAry= build3SeqCurrInjectAry(recvDataAry,this.seqCurrentUnit);

	  		// System.out.println("three sequence currents : " + thrSeqCurrAry[0].toString() );
	  		
	  		 
	  		// create the complex form of currents, and use them to update bus injection table
	  		  updatePosSeqBusCurInjTable(thrSeqCurrAry, boundaryBusCurInjTable);
	  		  
	  		// calculate the negative- and zero sequence voltages at the boundary bus based on the current injections  
	 		  this.negSeqVoltTable = seqNetHelper.calcNegativeSeqVolt(prepSeqCurrInjHashTable(SequenceCode.NEGATIVE, thrSeqCurrAry));
	 		  this.zeroSeqVoltTable = seqNetHelper.calcZeroSeqVolt(prepSeqCurrInjHashTable(SequenceCode.ZERO, thrSeqCurrAry)); 
	 		  
	 		  
	 		  for(String id:this.boundaryBusIdAry){
	  			  Complex3x1 voltComplex3x1 = new Complex3x1();
	  		      voltComplex3x1.c_2= negSeqVoltTable.get(id);
	  		      voltComplex3x1.a_0= zeroSeqVoltTable.get(id);
	  		      voltComplex3x1.b_1= this.net.getBus(id).getVoltage();
	  		      seqNetHelper.getSeqVoltTable().get(id).add(voltComplex3x1);
	  		  }
			 
		 }
		

 		  
		 
		 this.net.setCustomBusCurrInjHashtable(boundaryBusCurInjTable);
		
		
	}
	
	/**
	 * Get the current injected into the internal subnetwork for TS simulation based on the current injection 
	 * table of the external subsystem. 
	 * This method is used only by the run2SubNetHybridSimu(...) method
	 * @return
	 */
	private Hashtable<String,Complex> getOppositePosSeqCurrentInjTable(Hashtable<String,Complex> CurrInjTable){
		Hashtable<String,Complex>  oppoBoundaryCurrInjTable = new Hashtable<>();
		
		for(Entry<String,Complex> nvPair: CurrInjTable.entrySet()){
			
			//NOTE: The external boundary bus Ids are named after the convention: <original busId>+"Dummy"
			String busId = nvPair.getKey();
			busId = busId.replaceAll("Dummy", "");
			Complex curInj = nvPair.getValue();
			// tieline current flowing into the internal network is opposite to that flowing into the external network 
			//                       busi            busiDummy
			//   internal subnetwork  |-<------------>-|  external subnetwork
			oppoBoundaryCurrInjTable.put(busId, curInj.multiply(-1.0));
		}
		
		return oppoBoundaryCurrInjTable;
	}
	
	
	private Hashtable<String,Complex3x1> getOpposite3SeqBoundaryCurrentInjTable(Hashtable<String,Complex3x1> CurrInjTable){
		Hashtable<String,Complex3x1>  oppoBoundaryCurrInjTable = new Hashtable<>();
		
		for(Entry<String,Complex3x1> nvPair: CurrInjTable.entrySet()){
			
			//NOTE: The external boundary bus Ids are named after the convention: <original busId>+"Dummy"
			String busId = nvPair.getKey();
			busId = busId.replaceAll("Dummy", "");
			Complex3x1 curInj = nvPair.getValue();
			// tieline current flowing into the internal network is opposite to that flowing into the external network 
			//                       busi            busiDummy
			//   internal subnetwork  |-<------------>-|  external subnetwork
			oppoBoundaryCurrInjTable.put(busId, curInj.multiply(-1.0));
		}
		
		return oppoBoundaryCurrInjTable;
	}
	
	private Hashtable<String,Complex3x1> getOpposite3PhaseBoundaryCurrentInjTable(Hashtable<String,Complex3x1> threeSeqCurrInjTable){
		Hashtable<String,Complex3x1>  oppoBoundaryCurrInjTable = new Hashtable<>();
		
		for(Entry<String,Complex3x1> nvPair: threeSeqCurrInjTable.entrySet()){
			
			//NOTE: The external boundary bus Ids are named after the convention: <original busId>+"Dummy"
			String busId = nvPair.getKey();
			busId = busId.replaceAll("Dummy", "");
			Complex3x1 curInj = nvPair.getValue();
			// tieline current flowing into the internal network is opposite to that flowing into the external network 
			//                       busi            busiDummy
			//   internal subnetwork  |-<------------>-|  external subnetwork
			oppoBoundaryCurrInjTable.put(busId, Complex3x1.z12_to_abc(curInj.multiply(-1.0)));
		}
		System.out.println("Opposite3PhaseBoundaryCurrentInjTable ="+oppoBoundaryCurrInjTable);
		
		return oppoBoundaryCurrInjTable;
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
		
		return this.netEquivHelper.extractBoundaryBus3SeqVoltAry(zeroSeqVolt, negSeqVolt);
		
	}
	
	/**
	 * extract the current data from the received current injection array and convert it to PU.
	 * 
	 * @param posSeqCurrInjAry  the current array is in the format of [Imag1, Iang1, Imag2,Iang2,...]
	 * @param originalUnit
	 * @return
	 * @throws Exception
	 */
    public Complex[] buildPosSeqCurrInjectAry(double[] posSeqCurrInjAry, UnitType originalUnit) throws Exception{
		
		int dim = boundaryBusIdAry.length;
		Complex[] boundaryBusPosSeqCurrInjAry= new Complex[dim];
		
		//check the array dimension
		if(dim !=posSeqCurrInjAry.length/2){
			throw new Exception("Current injection array dimension is not consistent with boundary bus number,"
					+ "it should be 6 times of the number of boundary buses");
		}
		
		//prepare the array 
		int k = 0;
		for(int i=0; i<dim;i++){
			
			//positive sequence 
			double IposMag = posSeqCurrInjAry[k++];
			//angle in rad
			double IposAngle = posSeqCurrInjAry[k++];
			
			
			/*
			 * 
			 * MVA	baseKV	Current base (KA)	Multiplier
               100	230	       0.251021856	    3.983716857
               100	500	       0.115470054	    8.660254038

			 */
			
			double basekV = this.net.getBus(boundaryBusIdAry[i]).getBaseVoltage()/1000.0;
			double mvaBase =this.net.getBaseMva();
			if(originalUnit==UnitType.kAmp){
				double multiplier = Math.sqrt(3)*basekV/mvaBase;
				IposMag = IposMag* multiplier;

				
			}
			else if(originalUnit==UnitType.Amp){
				double multiplier = Math.sqrt(3)*basekV/mvaBase/1000.0;
				IposMag = IposMag* multiplier;
			}
            else if(originalUnit==UnitType.PU){
				// no data conversion
			}
            else{
            	throw new Error("The original current unit is not vilid ");
            }
			
			//convert Imag/_angle to complex form 

			boundaryBusPosSeqCurrInjAry[i] = ComplexFunc.polar(IposMag, IposAngle);

			
		}
		
		return boundaryBusPosSeqCurrInjAry;
		
		
	}
	
	
	/**
	 * The input threeSeqCurrInjAry is in the form of {[bus1CurSubAry],...,[busNCurSubAry]}
	 * and each sub array is in the form of [busiCurMag_pos,busiCurAng_pos,busiCurMag_neg,busiCurAng_neg,busiCurMag_zero,busiCurAng_zero]
	 * 
	 * Thus the dimension of the input array must be six times of the number of boundary buses.
	 * 
	 * @param threeSeqCurrInjAry sequence current array from the EMT simulator side
	 * @param originalUnit       unit of the current sent from the EMT simulator side
	 * @return
	 * @throws Exception 
	 */
	public Complex3x1[] build3SeqCurrInjectAry(double[] threeSeqCurrInjAry, UnitType originalUnit) throws Exception{
		
		//return this.netEquivHelper.extractBoundaryBus3SeqCurrInjAry(threeSeqCurrInjAry);
		
		int dim = boundaryBusIdAry.length;
		Complex3x1[] boundaryBus3SeqCurrInjAry= NumericalUtil4HybridSim.creatComplex3x1Array(dim);
		
		//check the array dimension
		if(dim !=threeSeqCurrInjAry.length/6){
			throw new Exception("Current injection array dimension is not consistent with boundary bus number," 
					+ "it should be 6 times of the number of boundary buses"+", bus # ="+dim+", array length ="+threeSeqCurrInjAry.length);
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
			
			/*
			 * 
			 * MVA	baseKV	Current base (KA)	Multiplier
               100	230	       0.251021856	    3.983716857
               100	500	       0.115470054	    8.660254038

			 */
			
			double basekV = this.net.getBus(boundaryBusIdAry[i]).getBaseVoltage()/1000.0;
			double mvaBase =this.net.getBaseMva();
			if(originalUnit==UnitType.kAmp){
				double multiplier = Math.sqrt(3)*basekV/mvaBase;
				IposMag = IposMag* multiplier;
				InegMag = InegMag* multiplier;
				IzeroMag = IzeroMag* multiplier;
				
			}
			else if(originalUnit==UnitType.Amp){
				double multiplier = Math.sqrt(3)*basekV/mvaBase/1000.0;
				IposMag = IposMag* multiplier;
				InegMag = InegMag* multiplier;
				IzeroMag = IzeroMag* multiplier;
			}
            else if(originalUnit==UnitType.PU){
				// no data conversion
			}
            else{
            	throw new Error("The original current unit is not vilid ");
            }
			
			//convert Imag/_angle to complex form 
			boundaryBus3SeqCurrInjAry[i].a_0 = ComplexFunc.polar(IzeroMag, IzeroAngle);
			boundaryBus3SeqCurrInjAry[i].b_1 = ComplexFunc.polar(IposMag, IposAngle);
			boundaryBus3SeqCurrInjAry[i].c_2 = ComplexFunc.polar(InegMag, InegAngle);
			
		}
		
		return boundaryBus3SeqCurrInjAry;
		
		
	}
	
	
	/**
	 * prepare the sequence current injection hashtable for the boundary buses.
	 * 
	 * @param code
	 * @param threeSeqCurrInjAry
	 * @return
	 * @throws Exception 
	 */
	public Hashtable<String, Complex> prepSeqCurrInjHashTable(SequenceCode code,Complex3x1[] thrSeqCurrInjAry) throws Exception{
		
		Hashtable<String, Complex> seqCurInjTable = new Hashtable<>();
		
		
		int i=0;
		for(String busId: this.boundaryBusIdAry){
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
     * Update the positive sequence bus current injection
     * 
     * @param curInjAry  array storing boundary bus positive sequence current injections, in the form of Complex, in pu
     * @param curInjTable
     */
    public void updatePosSeqBusCurInjTable(Complex[] curInjAry,Hashtable<String, Complex> curInjTable){
    	int i=0;
    	for(String busId:boundaryBusIdAry){
    		curInjTable.put(busId, curInjAry[i++]);
    	}
    }
    
    /**
     * Update the positive sequence bus current injection. Boundary bus current injection array sent from PSCAD side is input as Complex3x1 array
     * Only the positive sequence will be used.
     * @param curInjAry  array of data storing boundary bus three sequence current injections
     * @param curInjTable
     */
    public void updatePosSeqBusCurInjTable(Complex3x1[] curInjAry,Hashtable<String, Complex> curInjTable){
    	int i=0;
    	for(String busId:boundaryBusIdAry){
    		curInjTable.put(busId, curInjAry[i++].b_1);
    	}
    }
    
    
    /**
     * Update the positive sequence bus current injection. Boundary bus current injection array sent from PSCAD side is input as Complex3x1 array
     * Only the positive sequence will be used.
     * @param curInjAry  array of data storing boundary bus three sequence current injections
     * @param curInjTable
     */
    public void update3SeqBusCurInjTable(Complex3x1[] curInjAry,Hashtable<String, Complex3x1> curInjTable){
    	int i=0;
    	for(String busId:boundaryBusIdAry){
    		curInjTable.put(busId, curInjAry[i++]);
    	}
    }

	public double[] get3PhaseTheveninVoltSourceAry(Complex3x1[] thrSeqVoltAry,
			Complex3x1[] thrSeqCurrAry) {
		double[] threePhaeTheveninVolt = null;
		try {
			threePhaeTheveninVolt = this.netEquivHelper.get3PhaseTheveninVoltSourceAry(thrSeqVoltAry, thrSeqCurrAry);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return threePhaeTheveninVolt;
	}

	public Hashtable<String, Complex> calcTheveninVoltSource(
			Hashtable<String, Complex> equivCurInjTable, SequenceCode code,
			Complex[] seqVoltAry) throws Exception {
	
		return this.netEquivHelper.calcTheveninVoltSource(equivCurInjTable, code, seqVoltAry);
	}

	public Hashtable<String, Complex3x1> calc3PhaseTheveninVoltSource(
			Complex3x1[] v120, Complex3x1[] busI120) throws Exception {
		
		return this.netEquivHelper.calc3PhaseTheveninVoltSource(v120, busI120);
	}

	public double[] getSeqTheveninVoltSourceAry(
			Hashtable<String, Complex> boundaryBusCurInjTable,
			SequenceCode code, Complex[] seqVoltAry) throws Exception {
		
		return this.netEquivHelper.getSeqTheveninVoltSourceAry(boundaryBusCurInjTable, code, seqVoltAry);
	}
	
    public void setMiscDataSize(int miscDataNumber){
    	this.miscDataSize = miscDataNumber;
    }
    
    public int getMiscDataSize(){
    	return this.miscDataSize;
    }
    
    public double[] getMiscDataAry(){
    	return this.miscDataAry;
    }

}
