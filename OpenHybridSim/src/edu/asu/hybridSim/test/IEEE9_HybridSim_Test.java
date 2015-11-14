package edu.asu.hybridSim.test;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSimu.HybridSimuHelper;
import edu.asu.hybridSimu.InteractionProtocol;
import edu.asu.hybridSimu.NetworkEquivalentHelper;
import edu.asu.hybridSimu.ProtocolSwitchHelper;
import edu.asu.hybridSimu.SequenceNetworkSolver;
import edu.asu.hybridSimu.SocketServerHelper;
import edu.asu.hybridSimu.HybidSimSubNetworkHelper;

public class IEEE9_HybridSim_Test {
	
	//@Test
	public void test_Single_Port_3PFault_Interface() throws Exception{
		double[] recvDataAry =null;
		double[] sendDataAry =null;
		
		/*
		 * load transient stability system data set into DynamicStabilityNetwork object
		 */
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();

	    /*
	     * run load flow to initialize the system
	     */
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
	  	dstabAlgo.setSimuStepSec(0.005);
	  	
	  	/*
	  	 * Total simulation time, again it must be the consistent with the PSCAD side
	  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
	  	 * a steady state of the system is achieved. With this considered, the following equation holds:
	  	 * 
	  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
	  	*/
	  	dstabAlgo.setTotalSimuTimeSec(1.0);
        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		

		
		StateVariableRecorder ssRecorder = new StateVariableRecorder(0.01);
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.0167,                                      // time steps for recording 
				100); 
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);                                      // total points to record 
		
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
				0.0167,                                      // time steps for recording 
				100);                                      // total points to record
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				StateVarRecType.MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);
		ssRecorder.addCacheRecords("Bus4",      // mach id 
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);
		ssRecorder.addCacheRecords("Bus5",      // mach id 
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VANG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);
		// set the output handler
		dstabAlgo.setSimuOutputHandler(ssRecorder);
	
		/*
		 * create Interface Variable Helper for the study case and 
		 * predefined boundary buses
		 * 
		 */
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
		
		subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
		subNetHelper.addSubNetInterfaceBranch("Bus5->Bus7(0)", true);
		
		
		//String[] busIdAry =new String[]{"Bus5"};
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
	  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
	  	
	  	/*
	  	 * process internal sub-system, to set the buses and branches in internal network
	  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
	  	 * by boundary bus current injection during Dstab simulation
	  	 * 
	  	 */
	  	Hashtable<String, Complex> boundaryBusCurInjTable = hySimHelper.procInternalSubNetwork();
	  	
	  	System.out.println(boundaryBusCurInjTable);
	  	
	    //Need to set the load/gen/switchShunt at the boundary bus to be offline
		  	for(String busId:subNetHelper.getBoundaryBusIdAry()){
		  		DStabBus dsBus = dsNet.getBus(busId);
		  		for(AclfLoad load: dsBus.getContributeLoadList()){
		  			load.setStatus(false);
		  		}
		  		for(AclfGen gen: dsBus.getContributeGenList()){
		  			gen.setStatus(false);
		  		}
		  	}
		  	

	  	boolean tsaInit = false;
	  	
		//initialization, and Ymatrix is created inside
        tsaInit=dstabAlgo.initialization();
        
	
	  	
	  	SocketServerHelper ssHelper = new SocketServerHelper();
	  	

	  	/*
	  	 * create the server socket, port number should be consistent with the 
	  	 * IP Port Number defined in the Socket_Component in the PSCAD side
	  	 * 
	  	 * Timeout set to 20 sec
	  	 */
	  	ssHelper.createServerSokect(7776, 30000);
	  	
	  	
	  	boolean initSent = true;
	  	boolean faultOn = true; 
	  	
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger()) ;
	  	// make sure the dstab algo is initialized successfully
	  	if(tsaInit){
	  		int itrCnt = 0;
	  		
	  	   while(ssHelper.getClientSocket()!=null){//
	  		  if(!initSent){
	  		   
	  			System.out.println("\n\n---------------------------------");
	  		    System.out.println("iteration ="+itrCnt++ );
	  		  //receive data from the PSCAD side
	  		  recvDataAry=ssHelper.receiveDoubleArrays();
	  		  
	  		  //TODO calculate the interfacing data for sending back to the PSCAD
	  		  
	  		  double currentMag = recvDataAry[0];
	  		  double currentAng = recvDataAry[1];
	  		  System.out.println("recv Iinj mag, angle ="+currentMag+","+currentAng);
	  		// create the complex form of inject current from the PSCAD data
				
	  		  Complex curInj = ComplexFunc.polar(currentMag, currentAng);
	  		  System.out.println("recv Iinj ="+curInj.toString());
	  		  
	  		  //add this injection into the network through addToBi
	  		  //dsNet.getYMatrix().addToB(curInj, dsNet.getDStabBus("Bus5").getSortNumber());
	  		  // boundaryBusCurInjTable.put("Bus5", curInj);
	  		   
	  		   dsNet.setCustomBusCurrInjHashtable(boundaryBusCurInjTable);
	  		  
	  		  
	  		   if(dstabAlgo.getSimuTime()>=0.1 &&dstabAlgo.getSimuTime()<0.2){
	  			  //Run TSA one-step simulation
	 	  		  dstabAlgo.solveDEqnStep(true);
	 	  		  faultOn=true;

	  		   }
	  		   else{
	  			   faultOn=false;
	  		   }
	  		   
	  		//the calculated Thevenin votlages are in pu (mag) and rad (angle)
	  		 Hashtable<String,Complex> vthTable=hySimHelper.calcTheveninVoltSource(boundaryBusCurInjTable,SequenceCode.POSITIVE,null);
	  		   
	  		  Complex Vth5 = vthTable.get("Bus5");
	  		  
	  		  
			  //System.out.println("Vth angle [deg] @Bus5 = "+ComplexFunc.arg(Vth5)*180/Math.PI);
			 // System.out.println("Vth @Bus5 = "+ComplexFunc.toMagAng(Vth5));

	  		  
	  		  double Vth_angle = ComplexFunc.arg(Vth5)*180/Math.PI; // in deg
	  		  double Vth_mag = Vth5.abs();
	  		  
	  		System.out.println("Vth angle [deg] @Bus5 = "+Vth_angle);
	  		   //TODO prepare sendDataAry
	  		  sendDataAry = new double[2];
	  		  sendDataAry[0] = Vth_mag*dsNet.getBus("Bus5").getBaseVoltage()/1000.0;
	  		  sendDataAry[1] = Vth_angle;
	  		  
	  		  //send data to the client side
	  		  ssHelper.sendDoubleArrays(sendDataAry);
	  		  
	  		  //Run TSA one-step simulation
	  		  if(!faultOn)dstabAlgo.solveDEqnStep(true);
	  		  
	  		  //check the simulation time if it reaches the total simulation time
	  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
	  			  System.out.println("Simulation Time is up, and simulation succefully ends!");
	  			  timer.end();
	  			  break;
	  		      
	  		  }
	  		  
	  		   
	  		  }
	  		  else{
	  		  initSent = false;
	  		  timer.start();
	  		  }
	  	  }
	  	   // end the sockect connection
	  	   ssHelper.closeSocket();
	  	   
	  	}
	  	
	  	/**
	  	 * Initialization Machine angle, ref Bus1-Mach1
	  	 * 
	  	 * Gen2: 57.5628
	  	 * Gen3: 50.6173
	  	 */
	 // output recorded simulation results
	 		List<StateRecord> list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
	 		System.out.println("\n\n Bus3 Machine Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
	 		System.out.println("\n\n Bus2 Machine Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println( Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
	 		System.out.println("\n\n Bus3 Machine PM");
	 		for (StateRecord rec : list) {
	 			System.out.println( Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
	 		System.out.println("\n\n Bus3 voltage mag");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus4", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
	 		System.out.println("\n\n Bus4 voltage mag");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		list = ssRecorder.getMachineRecords(
	 				"Bus5", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VANG);
	 		System.out.println("\n\n Bus5 voltage Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
		
	}
	
	//@Test
	public void test_Single_Port_3PFault_Interface_hybridSimuHelper() throws Exception{
		
			/*
			 * load transient stability system data set into DynamicStabilityNetwork object
			 */
			IpssCorePlugin.init();
			IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/IEEE9Bus/ieee9.raw",
					"testData/IEEE9Bus/ieee9.seq",
					"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();

		    /*
		     * run load flow to initialize the system
		     */
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			
			//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
		  	dstabAlgo.setSimuStepSec(0.005);
		  	
		  	/*
		  	 * Total simulation time, again it must be the consistent with the PSCAD side
		  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
		  	 * a steady state of the system is achieved. With this considered, the following equation holds:
		  	 * 
		  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
		  	*/
		  	dstabAlgo.setTotalSimuTimeSec(1.0);
	        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
	        // add the state monitor
 	 		StateMonitor sm = new StateMonitor(); 
 	 		

 	 		sm.addGeneratorStdMonitor(new String[]{"Bus4-mach1"});
 	
 	 		
 	 		String[] monitorBusAry =new String[]{"Bus2","Bus3","Bus4","Bus5"};

 	 		sm.addBusStdMonitor(monitorBusAry);
		
			/*
			 * create Interface Variable Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			subNetHelper.addSubNetInterfaceBranch("Bus5->Bus7(0)", true);
			
			
			//String[] busIdAry =new String[]{"Bus5"};
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
		  	
		  	/*
		  	 * process internal sub-system, to set the buses and branches in internal network
		  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
		  	 * by boundary bus current injection during Dstab simulation
		  	 * 
		  	 */
		  	hySimHelper.procInternalSubNetwork();
		  	
		  	//Set hybrid simulation mode,  positive sequence data based,three-phase balanced application
		  	hySimHelper.setPosSeqEquivalentMode(true);
		  	
 	        // set the sequence current unit sent from the pscad side
		  	
		  	hySimHelper.setSeqCurrentUnit(UnitType.PU); // default is pu
		  
		  	
		 	/*
		  	 * create the server socket, port number should be consistent with the 
		  	 * IP Port Number defined in the Socket_Component in the PSCAD side
		  	 * 
		  	 * Timeout set to 30 sec
		  	 */
		  	hySimHelper.setupServerSocket(7776, 30000);
		  	
		  	
		  	
		  	//Explicitly setting the msg format for communication 
		  	hySimHelper.enableMsgSizeHeader(false);

	        // 0.02 by default
		  	hySimHelper.getProtocolSwitchHelper().setMaximChangePerStep(0.02);
		  	
		  	hySimHelper.getProtocolSwitchHelper().setSeries2ParallelSwitchDelay(33);
		  	
		  	hySimHelper.setDebugMode(true);
		  	
		  	if(dstabAlgo.initialization()){
		  		hySimHelper.runHybridSimu(dstabAlgo,InteractionProtocol.Combined);
		  	   
		  	}
		  	else
		  		IpssLogger.getLogger().severe("Dynamic system is not properly initialized for TS simulation, "
		  				+ "hybrid simulaiton cann't be started");
		  	
				System.out.println(sm.toCSVString(sm.getMachAngleTable()));
				System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		  	
		
			
		}
	
	
	@Test
	public void test_single_port_unbalancedFault_Interface() throws Exception{
			double[] recvDataAry =null;
			double[] sendDataAry =null;
			
			
			/*
			 * load transient stability system data set into DynamicStabilityNetwork object
			 */
			IpssCorePlugin.init();
			IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/IEEE9Bus/ieee9.raw",
					"testData/IEEE9Bus/ieee9.seq",
					"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();

		    /*
		     * run load flow to initialize the system
		     */
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			
			//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
		  	dstabAlgo.setSimuStepSec(0.005);
		  	
		  	/*
		  	 * Total simulation time, again it must be the consistent with the PSCAD side
		  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
		  	 * a steady state of the system is achieved. With this considered, the following equation holds:
		  	 * 
		  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
		  	*/
		  	dstabAlgo.setTotalSimuTimeSec(1.0);
	        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			

			
			StateVariableRecorder ssRecorder = new StateVariableRecorder(0.001);
			ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
					0.01,                                      // time steps for recording 
					100);                                      // total points to record 
			
			ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
					0.01,                                      // time steps for recording 
					100);                                      // total points to record
			ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
					StateVarRecType.MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
					0.01,                                      // time steps for recording 
					100);
			ssRecorder.addCacheRecords("Bus4",      // mach id 
					StateVarRecType.BusState,    // record type
					DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
					0.01,                                      // time steps for recording 
					100);
			ssRecorder.addCacheRecords("Bus5",      // mach id 
					StateVarRecType.BusState,    // record type
					DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
					0.01,                                      // time steps for recording 
					100);
			// set the output handler
			dstabAlgo.setSimuOutputHandler(ssRecorder);
		
			/*
			 * create Interface Variable Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
			//String[] busIdAry =new String[]{"Bus5"};
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			subNetHelper.addSubNetInterfaceBranch("Bus5->Bus7(0)", true);
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
		  	
		  	
		  	/*
		  	 * process internal sub-system, to set the buses and branches in internal network
		  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
		  	 * by boundary bus current injection during Dstab simulation
		  	 * 
		  	 */
		  	Hashtable<String, Complex> boundaryBusPosCurInjTable = hySimHelper.procInternalSubNetwork();
		  	
		  	
		    //Need to set the load/gen/switchShunt at the boundary bus to be offline
		  	for(String busId:subNetHelper.getBoundaryBusIdAry()){
		  		DStabBus dsBus = dsNet.getBus(busId);
		  		for(AclfLoad load: dsBus.getContributeLoadList()){
		  			load.setStatus(false);
		  		}
		  		for(AclfGen gen: dsBus.getContributeGenList()){
		  			gen.setStatus(false);
		  		}
		  	}
		  	

		  	boolean tsaInit = false;
		  	
			//initialization, and Ymatrix is created inside
	        tsaInit=dstabAlgo.initialization();
	        
//			System.out.println(dsNet.net2String());
	        
	        //Complex[][] infZ1 = ifVHelper.calcInterfaceSeqZMatrix( SequenceCode.POSITIVE);
	        //System.out.println("Sequence impedance matrix: Z1:");
	        //NumericalUtil4HybridSim.printComplexMatrix(infZ1);
//			

		  	
		  	SocketServerHelper ssHelper = new SocketServerHelper();
		  	
		  	String[] monitorBusAry = new String[]{"Bus4","Bus5"};
		  	SequenceNetworkSolver seqNetHelper = new SequenceNetworkSolver(dsNet,monitorBusAry );
		  	
		  	
		  	/*
		  	 * create the server socket, port number should be consistent with the 
		  	 * IP Port Number defined in the Socket_Component in the PSCAD side
		  	 * 
		  	 * Timeout set to 20 sec
		  	 */
		  	ssHelper.createServerSokect(7776, 60000);
		  	
		  	ProtocolSwitchHelper protocolHelper = new ProtocolSwitchHelper(5);
		  	
		  	
		  	
		  	boolean initSent = true;
		  	boolean faultOn = true; 
		  	
		  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger()) ;
		  	// make sure the dstab algo is initialized successfully
		  	if(tsaInit){
		  		int itrCnt = 0;
		  		
		  	   while(ssHelper.getClientSocket()!=null){//
		  		  if(!initSent){
		  		   
		  		   //System.out.println("\n\n---------------------------------");
		  		   //System.out.println("iteration ="+itrCnt++ );
		  		   System.out.print("Simu time ="+dstabAlgo.getSimuTime() +",");
		  		    
		  		  //receive data from the PSCAD side
		  		  recvDataAry=ssHelper.receiveDoubleArrays();
		  		  
		  		  //convert the input data to complex form
		  		 Complex3x1[] thrSeqCurrAry= hySimHelper.build3SeqCurrInjectAry(recvDataAry,UnitType.PU);

		  		// System.out.println("three sequence currents : " + thrSeqCurrAry[0].toString() );
		  		
		  		 
		  		  // create the complex form of currents from the PSCAD data, and use them to update bus injection table
		  		  hySimHelper.updatePosSeqBusCurInjTable(thrSeqCurrAry, boundaryBusPosCurInjTable);

		  		  dsNet.setCustomBusCurrInjHashtable(boundaryBusPosCurInjTable);
		  		  
		  		  int protocolType = protocolHelper.determineNewProtocol(thrSeqCurrAry);
		  		  
		  		  if (protocolType ==1) {
		  			  //System.out.println("using series protocol");
		  			System.out.print(",1\n");
		  		  }else
		  			 // System.out.println("using parallel protocol");
		  			 System.out.print(",0\n"); 
		  		  
		  		  
		  		   if(protocolType == 1){//dstabAlgo.getSimuTime()>=0.1 &&dstabAlgo.getSimuTime()<0.2
		  			  //Run TSA one-step simulation
		 	  		  dstabAlgo.solveDEqnStep(true);
		 	  		  faultOn=true;

		  		   }
		  		   else{
		  			   faultOn=false;
		  		   }
		  		   
		  		  //solve the negative and zero sequence network
		  		   
		  		  Hashtable<String, Complex> negSeqVoltTable = seqNetHelper.calcNegativeSeqVolt(hySimHelper.prepSeqCurrInjHashTable(SequenceCode.NEGATIVE, thrSeqCurrAry));
		  		  Hashtable<String, Complex> zeroSeqVoltTable = seqNetHelper.calcZeroSeqVolt(hySimHelper.prepSeqCurrInjHashTable(SequenceCode.ZERO, thrSeqCurrAry)); 
		  		  
		  		  
		  		  for(String id:monitorBusAry){
		  			  Complex3x1 voltComplex3x1 = new Complex3x1();
		  		      voltComplex3x1.c_2= negSeqVoltTable.get(id);
		  		      voltComplex3x1.a_0= zeroSeqVoltTable.get(id);
		  		      voltComplex3x1.b_1= dsNet.getBus(id).getVoltage();
		  		      seqNetHelper.getSeqVoltTable().get(id).add(voltComplex3x1);
		  		  }
		  		  
		  		  //extract three sequence voltages of boundary buses.
		  		  Complex3x1[] thrSeqVoltAry = hySimHelper.extractBoundaryBus3SeqVoltAry(zeroSeqVoltTable, negSeqVoltTable);
		  		  
		  		  System.out.println("three sequence votlages : " + thrSeqVoltAry[0].toString() );
		  		  
		  		  
		  			  
		  		  double[] sendVthAry =hySimHelper.get3PhaseTheveninVoltSourceAry(thrSeqVoltAry,thrSeqCurrAry);
		  		  
		  		  //System.out.println("send vth ary =\n"+Arrays.toString(sendVthAry));
		  		  //prepare double data array and send it to the PSCAD (client) side
		  		  ssHelper.sendDoubleArrays(sendVthAry);
		  		  
		  		  //Run TSA one-step simulation
		  		  if(!faultOn)dstabAlgo.solveDEqnStep(true);
		  		  
		  		  //check the simulation time if it reaches the total simulation time
		  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
		  			  System.out.println("Simulation Time is up, and simulation succefully ends!");
		  			  timer.end();
		  			  break;
		  		      
		  		  }
		  		  
		  		   
		  		  }
		  		  else{
		  		     initSent = false;
		  		     timer.start();
		  		  }
		  	  }
		  	   // end the sockect connection
		  	   ssHelper.closeSocket();
		  	   
		  	}
		  	
		  	/**
		  	 * Initialization Machine angle, ref Bus1-Mach1
		  	 * 
		  	 * Gen2: 57.5628
		  	 * Gen3: 50.6173
		  	 */
		 // output recorded simulation results
		 		List<StateRecord> list = ssRecorder.getMachineRecords(
		 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
		 		System.out.println("\n\n Bus3 Machine Angle");
		 		for (StateRecord rec : list) {
		 			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		 		}

		 		
		 		list = ssRecorder.getMachineRecords(
		 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
		 		System.out.println("\n\n Bus3 Machine PM");
		 		for (StateRecord rec : list) {
		 			System.out.println( Number2String.toStr(rec.variableValue));
		 		}
		 		
		 		
		 		list = ssRecorder.getMachineRecords(
		 				"Bus3-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
		 		System.out.println("\n\n Bus3 voltage mag");
		 		for (StateRecord rec : list) {
		 			System.out.println(Number2String.toStr(rec.variableValue));
		 		}
		 		
		 		list = ssRecorder.getMachineRecords(
		 				"Bus4", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
		 		System.out.println("\n\n Bus4 positive voltage mag");
		 		for (StateRecord rec : list) {
		 			System.out.println(Number2String.toStr(rec.variableValue));
		 		}
		 		list = ssRecorder.getMachineRecords(
		 				"Bus5", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
		 		System.out.println("\n\n Bus5 voltage mag");
		 		for (StateRecord rec : list) {
		 			System.out.println(Number2String.toStr(rec.variableValue));
		 		}
		 		
		 		//output bus sequence voltage
		 		for(String id: monitorBusAry){
		 			System.out.println("\n\nSequence voltage of #"+id);
		 			System.out.println("Pos / Neg  / Zero\n-----------------------");
		 			for(Complex3x1 seqVolt: seqNetHelper.getSeqVoltTable().get(id)){
		 				System.out.println(seqVolt.b_1.abs()+","+seqVolt.c_2.abs()+","+seqVolt.a_0.abs());
		 			}
		 		}
		}
	
    
	/**
	 * Multi-port equivalents, bus5 and bus7 as boundary buses
	 * @throws Exception
	 */
	//@Test
	public void test_multi_port_3PFault_Interface() throws Exception{
		double[] recvDataAry =null;
		double[] sendDataAry =null;
		
		/*
		 * load transient stability system data set into DynamicStabilityNetwork object
		 */
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();

	    /*
	     * run load flow to initialize the system
	     */
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
	  	dstabAlgo.setSimuStepSec(0.005);
	  	
	  	/*
	  	 * Total simulation time, again it must be the consistent with the PSCAD side
	  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
	  	 * a steady state of the system is achieved. With this considered, the following equation holds:
	  	 * 
	  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
	  	*/
	  	dstabAlgo.setTotalSimuTimeSec(1.0);
        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		

		
		StateVariableRecorder ssRecorder = new StateVariableRecorder(0.01);
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);                                      // total points to record 
		
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
				0.0167,                                      // time steps for recording 
				100);                                      // total points to record
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				StateVarRecType.MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);
		ssRecorder.addCacheRecords("Bus4",      // mach id 
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);
		ssRecorder.addCacheRecords("Bus5",      // mach id 
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VANG,       // state variable name
				0.0167,                                      // time steps for recording 
				100);
		// set the output handler
		dstabAlgo.setSimuOutputHandler(ssRecorder);
	
		/*
		 * create Interface Variable Helper for the study case and 
		 * predefined boundary buses
		 * 
		 */
		
		//String[] busIdAry =new String[]{"Bus5","Bus7"};
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
		subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
		subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
		
		
		
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
	  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
	  	
	  	/*
	  	 * process internal sub-system, to set the buses and branches in internal network
	  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
	  	 * by boundary bus current injection during Dstab simulation
	  	 * 
	  	 */
	  	

	  	
	  	System.out.println(subNetHelper.getBoundaryBusIdAry()[0]+","+subNetHelper.getBoundaryBusIdAry()[1]);
	  	Hashtable<String, Complex> boundaryBusCurInjTable = hySimHelper.procInternalSubNetwork();
	  	
       System.out.println("calc Bus 5 Inj ="+boundaryBusCurInjTable.get("Bus5"));
       System.out.println("calc Bus 7 Inj ="+boundaryBusCurInjTable.get("Bus7"));
       
       //Need to set the load/gen/switchShunt at the boundary bus to be offline
	  	for(String busId:subNetHelper.getBoundaryBusIdAry()){
	  		DStabBus dsBus = dsNet.getBus(busId);
	  		for(AclfLoad load: dsBus.getContributeLoadList()){
	  			load.setStatus(false);
	  		}
	  		for(AclfGen gen: dsBus.getContributeGenList()){
	  			gen.setStatus(false);
	  		}
	  	}
	  	
	  	boolean tsaInit = false;
	  	
		//initialization, and Ymatrix is created inside
        tsaInit=dstabAlgo.initialization();
        
//		System.out.println(dsNet.net2String());
        
        //Complex[][] infZ1 = ifVHelper.calcInterfaceSeqZMatrix( SequenceCode.POSITIVE);
        //System.out.println("Sequence impedance matrix: Z1:");
        //NumericalUtil4HybridSim.printComplexMatrix(infZ1);

	  	
	  	SocketServerHelper ssHelper = new SocketServerHelper();
	  	

	  	/*
	  	 * create the server socket, port number should be consistent with the 
	  	 * IP Port Number defined in the Socket_Component in the PSCAD side
	  	 * 
	  	 * Timeout set to 20 sec
	  	 */
	  	ssHelper.createServerSokect(7776, 20000);
	  	
	  	
	  	boolean initSent = true;
	  	boolean faultOn = true; 
	  	
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger()) ;
	  	// make sure the dstab algo is initialized successfully
	  	if(tsaInit){
	  		int itrCnt = 0;
	  		
	  	   while(ssHelper.getClientSocket()!=null){//
	  		  if(!initSent){
	  		   
	  			System.out.println("\n\n---------------------------------");
	  		    System.out.println("iteration ="+itrCnt++ );
	  		  //receive data from the PSCAD side
	  		  recvDataAry=ssHelper.receiveDoubleArrays();
	  		  
	  		  //TODO calculate the interfacing data for sending back to the PSCAD
	  		  
	  		  double bus5CurrentMag = recvDataAry[0];
	  		  double bus5currentAng = recvDataAry[1];

	  		  System.out.println("recv Bus5 Iinj mag, angle ="+bus5CurrentMag+","+bus5currentAng);
	  		  
	  		 // create the complex form of currents from the PSCAD data, and use them to update bus injection table
	  		  hySimHelper.updatePosSeqBusCurInjTable(hySimHelper.buildPosSeqCurrInjectAry(recvDataAry, UnitType.PU), boundaryBusCurInjTable);
	  		  
	  		 System.out.println("Boundary Bus injection:");
	  		 System.out.println("Bus 5 Inj ="+boundaryBusCurInjTable.get("Bus5"));
	         System.out.println("Bus 7 Inj ="+boundaryBusCurInjTable.get("Bus7"));

	  		  dsNet.setCustomBusCurrInjHashtable(boundaryBusCurInjTable);
	  		  
	  		  
	  		   if(dstabAlgo.getSimuTime()>=0.1 &&dstabAlgo.getSimuTime()<2){
	  			  //Run TSA one-step simulation
	 	  		  dstabAlgo.solveDEqnStep(true);
	 	  		  faultOn=true;

	  		   }
	  		   else{
	  			   faultOn=false;
	  		   }
	  		   
	  		  System.out.println("Bus 5 voltage = "+dsNet.getBus("Bus5").getVoltageMag()+","+dsNet.getBus("Bus5").getVoltageAng());
	  		  //prepare double data array and send it to the PSCAD (client) side
	  		  ssHelper.sendDoubleArrays(hySimHelper.getSeqTheveninVoltSourceAry(boundaryBusCurInjTable,SequenceCode.POSITIVE,null));
	  		  
	  		  //Run TSA one-step simulation
	  		  if(!faultOn)dstabAlgo.solveDEqnStep(true);
	  		  
	  		  //check the simulation time if it reaches the total simulation time
	  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
	  			  System.out.println("Simulation Time is up, and simulation succefully ends!");
	  			  timer.end();
	  			  break;
	  		      
	  		  }
	  		  
	  		   
	  		  }
	  		  else{
	  		     initSent = false;
	  		     timer.start();
	  		  }
	  	  }
	  	   // end the socket connection
	  	   ssHelper.closeSocket();
	  	   
	  	}
	  	
	  	/**
	  	 * Initialization Machine angle, ref Bus1-Mach1
	  	 * 
	  	 * Gen2: 57.5628
	  	 * Gen3: 50.6173
	  	 */
	 // output recorded simulation results
	 		List<StateRecord> list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
	 		System.out.println("\n\n Bus3 Machine Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
	 		}

	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
	 		System.out.println("\n\n Bus3 Machine PM");
	 		for (StateRecord rec : list) {
	 			System.out.println( Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
	 		System.out.println("\n\n Bus3 voltage mag");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus4", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
	 		System.out.println("\n\n Bus4 voltage mag");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		list = ssRecorder.getMachineRecords(
	 				"Bus5", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VANG);
	 		System.out.println("\n\n Bus5 voltage Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	}
	
	
	//@Test
	public void test_multi_port_unbalancedFault_Bus57_hybridSimuHelper() throws Exception{
			
				
				
				/*
				 * load transient stability system data set into DynamicStabilityNetwork object
				 */
				IpssCorePlugin.init();
				IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
				PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
				assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
						"testData/IEEE9Bus/ieee9.raw",
						"testData/IEEE9Bus/ieee9.seq",
						"testData/IEEE9Bus/ieee9_dyn_exc.dyr"
				}));
				DStabModelParser parser =(DStabModelParser) adapter.getModel();
				
				
				SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
				if (!new ODMDStabParserMapper(msg)
							.map2Model(parser, simuCtx)) {
					System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
					return;
				}
				
				
			    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
			    

			    /*
			     * run load flow to initialize the system
			     */
				DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
				LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
				assertTrue(aclfAlgo.loadflow());
				System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
				
				
				//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
			  	dstabAlgo.setSimuStepSec(0.015);
			  	
			  	/*
			  	 * Total simulation time, again it must be the consistent with the PSCAD side
			  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
			  	 * a steady state of the system is achieved. With this considered, the following equation holds:
			  	 * 
			  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
			  	*/
			  	dstabAlgo.setTotalSimuTimeSec(1.0);
		        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		        
		        dsNet.setNetEqnIterationNoEvent(4);
		        dsNet.setNetEqnIterationWithEvent(6);
				
	            
		        // add the state monitor
	 	 		StateMonitor sm = new StateMonitor(); 
	 	 		
	 	 		sm.addGeneratorStdMonitor(new String[]{"Bus2-mach1","Bus3-mach1"});
	 	
	 	 		
	 	 		String[] monitorBusAry =new String[]{"Bus5","Bus4","Bus7"};

	 	 		sm.addBusStdMonitor(monitorBusAry);
	 	 		
	 	 		
	 	 		dstabAlgo.setSimuOutputHandler(sm);
			
				/*
				 * create sub network Helper for the study case and 
				 * predefined boundary buses
				 * 
				 */
		
				HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
				
				/*
				 *  Bus4->Bus5(0),false
					Bus7->Bus8(0),true
					Bus2->Bus7(1),false
				 */
				subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
				subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
				subNetHelper.addSubNetInterfaceBranch("Bus2->Bus7(1)",false);
				
				NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
			  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
			  	
			  	
			  	/*
			  	 * process internal sub-system, to set the buses and branches in internal network
			  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
			  	 * by boundary bus current injection during Dstab simulation
			  	 * 
			  	 */
			  	hySimHelper.procInternalSubNetwork();
			
			    
			  	/*
			  	 * three-phase generic application
			  	 */
				hySimHelper.setPosSeqEquivalentMode(false);
				
			  	
			  	// set up the socket server
			  	hySimHelper.setupServerSocket(7776, 50000);
			  	
			 	// set the sequence current unit sent from the pscad side
			  	
			  	hySimHelper.setSeqCurrentUnit(UnitType.PU); // default is pu
		        
			  	
			  	 // 0.02 by default
			  	hySimHelper.getProtocolSwitchHelper().setMaximChangePerStep(0.05);
			  	
			  	hySimHelper.getProtocolSwitchHelper().setSeries2ParallelSwitchDelay(33);
			  	
			  	hySimHelper.setDebugMode(true);
			  	
			  
			  	if( dstabAlgo.initialization()){
			  		hySimHelper.runHybridSimu(dstabAlgo,InteractionProtocol.Parallel);
			  	   
			  	}
			  	FileUtil.writeText2File("output/ieee9_bus57_busVolt_bybrid_combined.csv", sm.toCSVString(sm.getBusVoltTable()));
			  	FileUtil.writeText2File("output/ieee9_bus57_angle_combined.csv", sm.toCSVString(sm.getMachAngleTable()));
				FileUtil.writeText2File("output/ieee9_bus57_efd_combined.csv", sm.toCSVString(sm.getMachEfdTable()));
			  	
			  
			}
	
	
	//@Test
	public void test_multi_port_unbalancedFault_Bus257_hybridSimuHelper() throws Exception{
		
			
			
			/*
			 * load transient stability system data set into DynamicStabilityNetwork object
			 */
			IpssCorePlugin.init();
			IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/IEEE9Bus/ieee9.raw",
					"testData/IEEE9Bus/ieee9.seq",
					"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
		    

		    /*
		     * run load flow to initialize the system
		     */
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			
			//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
		  	dstabAlgo.setSimuStepSec(0.005);
		  	
		  	/*
		  	 * Total simulation time, again it must be the consistent with the PSCAD side
		  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
		  	 * a steady state of the system is achieved. With this considered, the following equation holds:
		  	 * 
		  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
		  	*/
		  	dstabAlgo.setTotalSimuTimeSec(1.0);
	        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			
            
	        // add the state monitor
 	 		StateMonitor sm = new StateMonitor(); 
 	 		
 	 		sm.addGeneratorStdMonitor(new String[]{"Bus2-mach1"});
 	
 	 		
 	 		String[] monitorBusAry =new String[]{"Bus5","Bus4","Bus7"};

 	 		sm.addBusStdMonitor(monitorBusAry);
 	 		
 	 		
 	 		dstabAlgo.setSimuOutputHandler(sm);
		
			/*
			 * create sub network Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
	
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
		  	
		  	
		  	/*
		  	 * process internal sub-system, to set the buses and branches in internal network
		  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
		  	 * by boundary bus current injection during Dstab simulation
		  	 * 
		  	 */
		  	hySimHelper.procInternalSubNetwork();
		
		    
		  	/*
		  	 * three-phase generic application
		  	 */
			hySimHelper.setPosSeqEquivalentMode(false);
			
		  	
		  	// set up the socket server
		  	hySimHelper.setupServerSocket(7776, 30000);
		  	
		 	// set the sequence current unit sent from the pscad side
		  	
		  	hySimHelper.setSeqCurrentUnit(UnitType.PU); // default is pu
	
		  	
		  
		  	if( dstabAlgo.initialization()){
		  		hySimHelper.runHybridSimu(dstabAlgo,InteractionProtocol.Combined);
		  	   
		  	}
		  	
		  
		}
	
	//@Test
	public void test_multi_port_unbalancedFault_Bus257() throws Exception{
			double[] recvDataAry =null;
			double[] sendDataAry =null;
			
			
			/*
			 * load transient stability system data set into DynamicStabilityNetwork object
			 */
			IpssCorePlugin.init();
			IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/IEEE9Bus/ieee9.raw",
					"testData/IEEE9Bus/ieee9.seq",
					"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
		    

		    /*
		     * run load flow to initialize the system
		     */
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			
			//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
		  	dstabAlgo.setSimuStepSec(0.005);
		  	
		  	/*
		  	 * Total simulation time, again it must be the consistent with the PSCAD side
		  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
		  	 * a steady state of the system is achieved. With this considered, the following equation holds:
		  	 * 
		  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
		  	*/
		  	dstabAlgo.setTotalSimuTimeSec(1.0);
	        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			

			
			StateVariableRecorder ssRecorder = new StateVariableRecorder(0.01);
			ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
					0.0167,                                      // time steps for recording 
					100);                                      // total points to record 
			
			ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
					0.0167,                                      // time steps for recording 
					100);                                      // total points to record
			ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
					StateVarRecType.MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
					0.0167,                                      // time steps for recording 
					100);
			ssRecorder.addCacheRecords("Bus4",      // mach id 
					StateVarRecType.BusState,    // record type
					DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
					0.0167,                                      // time steps for recording 
					100);
			ssRecorder.addCacheRecords("Bus5",      // mach id 
					StateVarRecType.BusState,    // record type
					DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
					0.0167,                                      // time steps for recording 
					100);
			// set the output handler
			dstabAlgo.setSimuOutputHandler(ssRecorder);
		
			/*
			 * create sub network Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
	
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
		  	
		  	
		  	/*
		  	 * process internal sub-system, to set the buses and branches in internal network
		  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
		  	 * by boundary bus current injection during Dstab simulation
		  	 * 
		  	 */
		  	Hashtable<String, Complex> boundaryBusPosCurInjTable = hySimHelper.procInternalSubNetwork();
		  	System.out.println(hySimHelper.getNetEquivHelper().getSubNetHelper().getBoundaryBusIdAry());
		  	System.out.println(hySimHelper.getNetEquivHelper().getSubNetHelper().getInternalNetworkBusList());
		  	
		    //Need to set the load/gen/switchShunt at the boundary bus to be offline
		  	for(String busId:subNetHelper.getBoundaryBusIdAry()){
		  		DStabBus dsBus = dsNet.getBus(busId);
		  		for(AclfLoad load: dsBus.getContributeLoadList()){
		  			load.setStatus(false);
		  		}
		  		for(AclfGen gen: dsBus.getContributeGenList()){
		  			gen.setStatus(false);
		  		}
		  	}
		  	

		  	boolean tsaInit = false;
		  	
			//initialization, and Ymatrix is created inside
	        tsaInit=dstabAlgo.initialization();
	        

		  	
		  	SocketServerHelper ssHelper = new SocketServerHelper();
		  	
		  	String[] monitorBusAry = new String[]{"Bus4","Bus5","Bus7","Bus8"};
		  	SequenceNetworkSolver seqNetHelper = new SequenceNetworkSolver(dsNet,monitorBusAry );
		  	
		  	
		  	/*
		  	 * create the server socket, port number should be consistent with the 
		  	 * IP Port Number defined in the Socket_Component in the PSCAD side
		  	 * 
		  	 * Timeout set to 20 sec
		  	 */
		  	ssHelper.createServerSokect(7776, 300000);
		  	
		  	ProtocolSwitchHelper protocolHelper = new ProtocolSwitchHelper(5);
		  	
		  	
		  	boolean initSent = true;
		  	boolean faultOn = true; 
		  	double startTime = 0;
		  	double endTime = 0;
		  	
		  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger()) ;
		  	// make sure the dstab algo is initialized successfully
		  	if(tsaInit){
		  		int itrCnt = 0;
		  		
		  	   while(ssHelper.getClientSocket()!=null){
		  		
		  		  if(!initSent){
		  			
		  			//System.out.println("\n\n---------------------------------");
		  		    //System.out.print("iteration ="+ itrCnt);
		  		    if(itrCnt == 0)	  startTime = System.currentTimeMillis();
		  		    
		  		   itrCnt++;
		  			System.out.print(dstabAlgo.getSimuTime()+",");
		  		  //receive data from the PSCAD side
		  		  recvDataAry=ssHelper.receiveDoubleArrays();
		  		  
		  		  //convert the input data to complex form
		  		 Complex3x1[] thrSeqCurrAry= hySimHelper.build3SeqCurrInjectAry(recvDataAry,UnitType.PU);

		  		 
		  		  // create the complex form of currents from the PSCAD data, and use them to update bus injection table
		  		  hySimHelper.updatePosSeqBusCurInjTable(thrSeqCurrAry, boundaryBusPosCurInjTable);

		  		  dsNet.setCustomBusCurrInjHashtable(boundaryBusPosCurInjTable);
		  		  
		  		  int protocolType = 1;
		  		  /*
                  protocolType = protocolHelper.determineNewProtocol(thrSeqCurrAry);
		  		  
		  		  if (protocolType ==1) {
		  			  //System.out.println("using series protocol");
		  			System.out.print(",1\n");
		  		  }else
		  			 // System.out.println("using parallel protocol");
		  			 System.out.print(",0\n"); 
		  		  */
		  		  
		  		   if(protocolType ==1){//dstabAlgo.getSimuTime()>=0.1 &&dstabAlgo.getSimuTime()<0.2
		  			  //Run TSA one-step simulation
		 	  		  dstabAlgo.solveDEqnStep(true);
		 	  		  faultOn=true;

		  		   }
		  		   else{
		  			   faultOn=false;
		  		   }
		  		   
		  		  //solve the negative and zero sequence network
		  		   
		  		  Hashtable<String, Complex> negSeqVoltTable = seqNetHelper.calcNegativeSeqVolt(hySimHelper.prepSeqCurrInjHashTable(SequenceCode.NEGATIVE, thrSeqCurrAry));
		  		  Hashtable<String, Complex> zeroSeqVoltTable = seqNetHelper.calcZeroSeqVolt(hySimHelper.prepSeqCurrInjHashTable(SequenceCode.ZERO, thrSeqCurrAry)); 
		  		  
		  		  
		  		  for(String id:monitorBusAry){
		  			  Complex3x1 voltComplex3x1 = new Complex3x1();
		  		      voltComplex3x1.c_2= negSeqVoltTable.get(id);
		  		      voltComplex3x1.a_0= zeroSeqVoltTable.get(id);
		  		      voltComplex3x1.b_1= dsNet.getBus(id).getVoltage();
		  		      seqNetHelper.getSeqVoltTable().get(id).add(voltComplex3x1);
		  		  }
		  		  
		  		  //extract three sequence voltages of boundary buses.
		  		  Complex3x1[] thrSeqVoltAry = hySimHelper.extractBoundaryBus3SeqVoltAry(zeroSeqVoltTable, negSeqVoltTable);
		  		  
		  		  //calculate the three phase Thevenin equivalent voltage source at the boundary buses
		  		  double[] sendVthAry =hySimHelper.get3PhaseTheveninVoltSourceAry(thrSeqVoltAry,thrSeqCurrAry);
		  		  
		  		  //System.out.println("send vth ary =\n"+Arrays.toString(sendVthAry));
		  		  //prepare double data array and send it to the PSCAD (client) side
		  		  ssHelper.sendDoubleArrays(sendVthAry);
		  		  
		  		  //Run TSA one-step simulation
		  		  if(!faultOn)dstabAlgo.solveDEqnStep(true);
		  		  
		  		  //check the simulation time if it reaches the total simulation time
		  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
		  			  System.out.println("Simulation Time is up, and simulation succefully ends!");
		  			 // timer.end();
		  			 endTime = System.currentTimeMillis();
		  			  System.out.println("total time (ms) = " + (endTime - startTime));
		  			  break;
		  		      
		  		  }
		  		  
		  		   
		  		  }
		  		  else{
		  		     initSent = false;
		  		     timer.start();
		  		  }
		  	  }
		  	   // end the socket connection
		  	   ssHelper.closeSocket();
		  	   
		  	}
		  	
		  
		}
	
	   // @Test
		public void test_multi_port_unbalancedFault_Bus457() throws Exception{
				double[] recvDataAry =null;
				double[] sendDataAry =null;
				
				
				/*
				 * load transient stability system data set into DynamicStabilityNetwork object
				 */
				IpssCorePlugin.init();
				IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
				PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
				assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
						"testData/IEEE9Bus/ieee9.raw",
						"testData/IEEE9Bus/ieee9.seq",
						"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				}));
				DStabModelParser parser =(DStabModelParser) adapter.getModel();
				
				
				SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
				if (!new ODMDStabParserMapper(msg)
							.map2Model(parser, simuCtx)) {
					System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
					return;
				}
				
				
			    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
			    
	
			    /*
			     * run load flow to initialize the system
			     */
				DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
				LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
				assertTrue(aclfAlgo.loadflow());
				System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
				
				
				//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
			  	dstabAlgo.setSimuStepSec(0.005);
			  	
			  	/*
			  	 * Total simulation time, again it must be the consistent with the PSCAD side
			  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
			  	 * a steady state of the system is achieved. With this considered, the following equation holds:
			  	 * 
			  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
			  	*/
			  	dstabAlgo.setTotalSimuTimeSec(1.0);
		        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
				

				
				StateVariableRecorder ssRecorder = new StateVariableRecorder(0.01);
				ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
						MachineState,    // record type
						DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
						0.0167,                                      // time steps for recording 
						100);                                      // total points to record 
				
				ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
						MachineState,    // record type
						DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
						0.0167,                                      // time steps for recording 
						100);                                      // total points to record
				ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
						StateVarRecType.MachineState,    // record type
						DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
						0.0167,                                      // time steps for recording 
						100);
				ssRecorder.addCacheRecords("Bus4",      // mach id 
						StateVarRecType.BusState,    // record type
						DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
						0.0167,                                      // time steps for recording 
						100);
				ssRecorder.addCacheRecords("Bus5",      // mach id 
						StateVarRecType.BusState,    // record type
						DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
						0.0167,                                      // time steps for recording 
						100);
				// set the output handler
				dstabAlgo.setSimuOutputHandler(ssRecorder);
			
				/*
				 * create sub network Helper for the study case and 
				 * predefined boundary buses
				 * 
				 */
		
				HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
				
				//subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
				//subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
				
				
				subNetHelper.addSubNetInterfaceBranch("Bus1->Bus4(1)", false);
				subNetHelper.addSubNetInterfaceBranch("Bus4->Bus6(0)", true);
				subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
				subNetHelper.addSubNetInterfaceBranch("Bus2->Bus7(1)", false);
				
				NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
			  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
			  	
			  	
			  	/*
			  	 * process internal sub-system, to set the buses and branches in internal network
			  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
			  	 * by boundary bus current injection during Dstab simulation
			  	 * 
			  	 */
			  	Hashtable<String, Complex> boundaryBusPosCurInjTable = hySimHelper.procInternalSubNetwork();
			  	System.out.println(hySimHelper.getNetEquivHelper().getSubNetHelper().getBoundaryBusIdAry());
			  	System.out.println(hySimHelper.getNetEquivHelper().getSubNetHelper().getInternalNetworkBusList());
			  	
			    //Need to set the load/gen/switchShunt at the boundary bus to be offline
			  	for(String busId:subNetHelper.getBoundaryBusIdAry()){
			  		DStabBus dsBus = dsNet.getBus(busId);
			  		for(AclfLoad load: dsBus.getContributeLoadList()){
			  			load.setStatus(false);
			  		}
			  		for(AclfGen gen: dsBus.getContributeGenList()){
			  			gen.setStatus(false);
			  		}
			  	}
			  	

			  	boolean tsaInit = false;
			  	
				//initialization, and Ymatrix is created inside
		        tsaInit=dstabAlgo.initialization();
		        

			  	
			  	SocketServerHelper ssHelper = new SocketServerHelper();
			  	
			  	String[] monitorBusAry = new String[]{"Bus4","Bus5","Bus7","Bus8"};
			  	SequenceNetworkSolver seqNetHelper = new SequenceNetworkSolver(dsNet,monitorBusAry );
			  	
			  	
			  	/*
			  	 * create the server socket, port number should be consistent with the 
			  	 * IP Port Number defined in the Socket_Component in the PSCAD side
			  	 * 
			  	 * Timeout set to 20 sec
			  	 */
			  	ssHelper.createServerSokect(7776, 300000);
			  	
			  	ProtocolSwitchHelper protocolHelper = new ProtocolSwitchHelper(5);
			  	
			  	
			  	boolean initSent = true;
			  	boolean faultOn = true; 
			  	
			  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger()) ;
			  	// make sure the dstab algo is initialized successfully
			  	if(tsaInit){
			  		int itrCnt = 0;
			  		
			  	   while(ssHelper.getClientSocket()!=null){//
			  		 timer.start();
			  	
			  		   
			  		  if(!initSent){
			  		   
			  			//System.out.println("\n\n---------------------------------");
			  		    //System.out.print("iteration ="+itrCnt++ );
			  			System.out.print(dstabAlgo.getSimuTime()+",");
			  		  //receive data from the PSCAD side
			  		  recvDataAry=ssHelper.receiveDoubleArrays();
			  		  
			  		  //convert the input data to complex form
			  		 Complex3x1[] thrSeqCurrAry= hySimHelper.build3SeqCurrInjectAry(recvDataAry,UnitType.PU);

			  		 
			  		  // create the complex form of currents from the PSCAD data, and use them to update bus injection table
			  		  hySimHelper.updatePosSeqBusCurInjTable(thrSeqCurrAry, boundaryBusPosCurInjTable);

			  		  dsNet.setCustomBusCurrInjHashtable(boundaryBusPosCurInjTable);
			  		  
			  		  
	                  int protocolType = protocolHelper.determineNewProtocol(thrSeqCurrAry);
			  		  
			  		  if (protocolType ==1) {
			  			  //System.out.println("using series protocol");
			  			System.out.print(",1\n");
			  		  }else
			  			 // System.out.println("using parallel protocol");
			  			 System.out.print(",0\n"); 
			  		  
			  		  
			  		   if(protocolType ==1){//dstabAlgo.getSimuTime()>=0.1 &&dstabAlgo.getSimuTime()<0.2
			  			  //Run TSA one-step simulation
			 	  		  dstabAlgo.solveDEqnStep(true);
			 	  		  faultOn=true;

			  		   }
			  		   else{
			  			   faultOn=false;
			  		   }
			  		   
			  		  //solve the negative and zero sequence network
			  		   
			  		  Hashtable<String, Complex> negSeqVoltTable = seqNetHelper.calcNegativeSeqVolt(hySimHelper.prepSeqCurrInjHashTable(SequenceCode.NEGATIVE, thrSeqCurrAry));
			  		  Hashtable<String, Complex> zeroSeqVoltTable = seqNetHelper.calcZeroSeqVolt(hySimHelper.prepSeqCurrInjHashTable(SequenceCode.ZERO, thrSeqCurrAry)); 
			  		  
			  		  
			  		  for(String id:monitorBusAry){
			  			  Complex3x1 voltComplex3x1 = new Complex3x1();
			  		      voltComplex3x1.c_2= negSeqVoltTable.get(id);
			  		      voltComplex3x1.a_0= zeroSeqVoltTable.get(id);
			  		      voltComplex3x1.b_1= dsNet.getBus(id).getVoltage();
			  		      seqNetHelper.getSeqVoltTable().get(id).add(voltComplex3x1);
			  		  }
			  		  
			  		  //extract three sequence voltages of boundary buses.
			  		  Complex3x1[] thrSeqVoltAry = hySimHelper.extractBoundaryBus3SeqVoltAry(zeroSeqVoltTable, negSeqVoltTable);
			  		  
			  		  //calculate the three phase Thevenin equivalent voltage source at the boundary buses
			  		  double[] sendVthAry =hySimHelper.get3PhaseTheveninVoltSourceAry(thrSeqVoltAry,thrSeqCurrAry);
			  		  
			  		  //System.out.println("send vth ary =\n"+Arrays.toString(sendVthAry));
			  		  //prepare double data array and send it to the PSCAD (client) side
			  		  ssHelper.sendDoubleArrays(sendVthAry);
			  		  
			  		  //Run TSA one-step simulation
			  		  if(!faultOn)dstabAlgo.solveDEqnStep(true);
			  		  
			  		  //check the simulation time if it reaches the total simulation time
			  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
			  			  System.out.println("Simulation Time is up, and simulation succefully ends!");
			  			  timer.end();
			  			  System.out.println("total time = " + timer.getDuration());
			  			  break;
			  		      
			  		  }
			  		  
			  		   
			  		  }
			  		  else{
			  		     initSent = false;
			  		     timer.start();
			  		  }
			  	  }
			  	   // end the socket connection
			  	   ssHelper.closeSocket();
			  	   
			  	}
			  	
			  	/**
			  	 * Initialization Machine angle, ref Bus1-Mach1
			  	 * 
			  	 * Gen2: 57.5628
			  	 * Gen3: 50.6173
			  	 */
			 // output recorded simulation results
			  	
			  	/*
			 		List<StateRecord> list = ssRecorder.getMachineRecords(
			 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
			 		System.out.println("\n\n Bus3 Machine Angle");
			 		for (StateRecord rec : list) {
			 			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			 		}

			 		
			 		list = ssRecorder.getMachineRecords(
			 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
			 		System.out.println("\n\n Bus3 Machine PM");
			 		for (StateRecord rec : list) {
			 			System.out.println( Number2String.toStr(rec.variableValue));
			 		}
			 		
			 		
			 		list = ssRecorder.getMachineRecords(
			 				"Bus3-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
			 		System.out.println("\n\n Bus3 voltage mag");
			 		for (StateRecord rec : list) {
			 			System.out.println(Number2String.toStr(rec.variableValue));
			 		}
			 		
			 		list = ssRecorder.getMachineRecords(
			 				"Bus4", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
			 		System.out.println("\n\n Bus4 positive voltage mag");
			 		for (StateRecord rec : list) {
			 			System.out.println(Number2String.toStr(rec.variableValue));
			 		}
			 		list = ssRecorder.getMachineRecords(
			 				"Bus5", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
			 		System.out.println("\n\n Bus5 voltage mag");
			 		for (StateRecord rec : list) {
			 			System.out.println(Number2String.toStr(rec.variableValue));
			 		}
			 		
			 		//output bus sequence voltage
			 		for(String id: monitorBusAry){
			 			System.out.println("\n\nSequence voltage of #"+id);
			 			System.out.println("Pos / Neg  / Zero\n-----------------------");
			 			for(Complex3x1 seqVolt: seqNetHelper.getSeqVoltTable().get(id)){
			 				System.out.println(seqVolt.b_1.abs()+","+seqVolt.c_2.abs()+","+seqVolt.a_0.abs());
			 			}
			 		}
			 		*/
			}
		
		//@Test
		public void test_multi_port_unbalancedFault_Bus457_hybridSimuHelper() throws Exception{
				
					
					
					/*
					 * load transient stability system data set into DynamicStabilityNetwork object
					 */
					IpssCorePlugin.init();
					IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
					PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
					assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
							"testData/IEEE9Bus/ieee9.raw",
							"testData/IEEE9Bus/ieee9.seq",
							"testData/IEEE9Bus/ieee9_dyn_exc.dyr"
					}));
					DStabModelParser parser =(DStabModelParser) adapter.getModel();
					
					
					SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
					if (!new ODMDStabParserMapper(msg)
								.map2Model(parser, simuCtx)) {
						System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
						return;
					}
					
					
				    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
				    

				    /*
				     * run load flow to initialize the system
				     */
					DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
					LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
					assertTrue(aclfAlgo.loadflow());
					System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
					
					
					//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
				  	dstabAlgo.setSimuStepSec(0.015);
				  	
				  	/*
				  	 * Total simulation time, again it must be the consistent with the PSCAD side
				  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
				  	 * a steady state of the system is achieved. With this considered, the following equation holds:
				  	 * 
				  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
				  	*/
				  	dstabAlgo.setTotalSimuTimeSec(1.0);
			        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			        
			        dsNet.setNetEqnIterationNoEvent(6);
			        dsNet.setNetEqnIterationWithEvent(6);
					
		            
			        // add the state monitor
		 	 		StateMonitor sm = new StateMonitor(); 
		 	 		
		 	 		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		 	
		 	 		
		 	 		String[] monitorBusAry =new String[]{"Bus1","Bus4","Bus7"};

		 	 		sm.addBusStdMonitor(monitorBusAry);
		 	 		
		 	 		
		 	 		dstabAlgo.setSimuOutputHandler(sm);
				
					/*
					 * create sub network Helper for the study case and 
					 * predefined boundary buses
					 * 
					 */
			
					HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
					
					/*
					 *  Bus1->Bus4(1), false
					 *  Bus4->Bus6(0),false
						Bus7->Bus8(0),true
						Bus2->Bus7(1),false
					 */
					subNetHelper.addSubNetInterfaceBranch("Bus1->Bus4(1)", false);
					subNetHelper.addSubNetInterfaceBranch("Bus4->Bus6(0)", true);
					subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
					subNetHelper.addSubNetInterfaceBranch("Bus2->Bus7(1)", false);
					
					NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
				  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
				  	
				  	
				  	/*
				  	 * process internal sub-system, to set the buses and branches in internal network
				  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
				  	 * by boundary bus current injection during Dstab simulation
				  	 * 
				  	 */
				  	hySimHelper.procInternalSubNetwork();
				
				    
				  	/*
				  	 * three-phase generic application
				  	 */
					hySimHelper.setPosSeqEquivalentMode(false);
					
				  	
				  	// set up the socket server
				  	hySimHelper.setupServerSocket(7776, 50000);
				  	
				 	// set the sequence current unit sent from the pscad side
				  	
				  	hySimHelper.setSeqCurrentUnit(UnitType.PU); // default is pu
			        
				  	
				  	 // 0.02 by default
				  	hySimHelper.getProtocolSwitchHelper().setMaximChangePerStep(0.02);
				  	
				  	hySimHelper.getProtocolSwitchHelper().setSeries2ParallelSwitchDelay(33);
				  	
				  	hySimHelper.setDebugMode(true);
				  	
				  
				  	if( dstabAlgo.initialization()){
				  		hySimHelper.runHybridSimu(dstabAlgo,InteractionProtocol.Parallel);
				  	   
				  	}
				  	FileUtil.writeText2File("output/ieee9_bus457_busVolt_bybrid_combined.csv", sm.toCSVString(sm.getBusVoltTable()));
				  	FileUtil.writeText2File("output/ieee9_bus457_QGen_combined.csv", sm.toCSVString(sm.getMachQgenTable()));
					FileUtil.writeText2File("output/ieee9_bus457_efd_combined.csv", sm.toCSVString(sm.getMachEfdTable()));
				  	
				  
				}
}
