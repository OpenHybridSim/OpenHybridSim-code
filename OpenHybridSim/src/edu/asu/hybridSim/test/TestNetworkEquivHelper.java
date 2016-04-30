package edu.asu.hybridSim.test;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.AclfOutFunc.BusIdStyle;
import org.interpss.display.impl.AclfOut_BusStyle;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.TestUtilFunc;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.ScBusVoltageType;
import com.interpss.core.algo.SimpleFaultAlgorithm;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSimu.NetworkEquivalentHelper;
import edu.asu.hybridSimu.HybidSimSubNetworkHelper;

public class TestNetworkEquivHelper {
	
	
	
	@Test
	public void testEquivHelper_IEEE9_Bus257() throws Exception{
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
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		System.out.println(AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_No));
		
		/*
		 * create subNetwork Helper for the study case and 
		 * predefined boundary buses
		 * 
		 */
	
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
		
		subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
		subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
		
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		equivHelper.buildNetWorkEquivalent(false,3);
		System.out.print(equivHelper.getSubNetHelper().getInternalNetworkBranchList().toString());
		equivHelper.calcNSaveBoundaryBus3PhaseEquivParam("output/ieee9_bus257_3p_equivalent_09122014.csv");
	}
	
	
	 //  @Test
		public void subNetProcessor_IEEE9_Bus257() throws Exception{
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
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			System.out.println(AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_No));
			
			/*
			 * create subNetwork Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
		
			SubNetworkProcessor subNetHelper = new SubNetworkProcessor(dsNet);
			
			
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
			
			//must run the splitting operation first
			subNetHelper.splitFullSystemIntoSubsystems(true);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
			
			equivHelper.calcNSavePositiveEquivParam("output/ieee9_bus257_pos_equiv_subNetProc_04272015.csv");
		}
	   
	   
	   //@Test
		public void subNetProcessor_IEEE9_Bus58() throws Exception{
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
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			System.out.println(AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_No));
			
			/*
			 * create subNetwork Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
		
			SubNetworkProcessor subNetHelper = new SubNetworkProcessor(dsNet);
			
			
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			subNetHelper.addSubNetInterfaceBranch("Bus8->Bus9(0)", true);
			
			//must run the splitting operation first
			subNetHelper.splitFullSystemIntoSubsystems(true);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
			
			equivHelper.calcNSavePositiveEquivParam("output/ieee9_bus58_pos_equiv_subNetProc.csv");
		}
	   
	   
	    @Test
		public void HSSubNetHelper_Equiv_IEEE9_Bus257() throws Exception{
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
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			System.out.println(AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_No));
			
			/*
			 * create subNetwork Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
		
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
			
			equivHelper.calcNSavePositiveEquivParam("output/ieee9_bus257_pos_equiv_HSSubNetHelper_04272015.csv");
		}
		
	
	//@Test
	public void ThreephaseTheveninEquivalent_IEEE9_Bus257() throws Exception{
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
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		System.out.println(AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_No));
		
		/*
		 * create subNetwork Helper for the study case and 
		 * predefined boundary buses
		 * 
		 */
	
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
		
		
		subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
		subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
		
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		
		equivHelper.calcNSaveBoundaryBus3PhaseEquivParam("output/ieee9_bus257_3p_equivalent_09122014.csv");
	}
	
	
	//@Test
	public void ThreephaseTheveninEquivalent_IEEE9_Bus457() throws Exception{
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
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		System.out.println(AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_No));
		
		/*
		 * create subNetwork Helper for the study case and 
		 * predefined boundary buses
		 * 
		 */
	
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
		subNetHelper.addSubNetInterfaceBranch("Bus1->Bus4(1)", false);
		subNetHelper.addSubNetInterfaceBranch("Bus4->Bus6(0)", true);
		subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
		subNetHelper.addSubNetInterfaceBranch("Bus2->Bus7(1)", false);
	
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		System.out.println(subNetHelper.getInternalNetworkBranchList());
		System.out.println(subNetHelper.getInternalNetworkBusList());
		equivHelper.calcNSavePositiveEquivParam("output/ieee9_bus457_pos_equivalent_09152014.csv");
		equivHelper.calcNSaveBoundaryBus3PhaseEquivParam("output/ieee9_bus457_3p_equivalent_09152014.csv");
		
		
		/*
		 * Three phase equivalent, test passed, 09/15/2014 : PSCAD CASE: IEEE9_Bus457_paper1_zl_091514
		 * 
		 * Boundary Bus Id	BaseVolt(kV)	Phase A Vth Mag(PU)	Phase A Angle (deg)	Zaa(R) pu	Zaa(X) pu	Zab(R) pu	Zab(X) pu	Zac(R) pu	Zac(X) pu	Zpos (R) #Bus4	Zpos (X) #Bus4	Zpos (R) #Bus7	Zpos (X) #Bus7	Zzero (R) #Bus4	Zzero(X) #Bus4	Zzero (R) #Bus7	Zzero(X) #Bus7
                  Bus4	230	87.88197061	2.11475942	6.41E-04	0.053685967	0.035790222	-0.431940156	0.035790222	-0.431940156	0.005747175	0.084890458	0.090255205	0.723977837	6.41E-04	0.053685967	1.605920473	5.392289508
                  Bus7	230	68.45296438	14.24295759	5.26E-04	0.056449647	0.021645209	-0.336253669	0.021645209	-0.336253669	0.090255205	0.723977837	0.009390949	0.112487511	1.605920473	5.392289508	5.26E-04	0.056449647

		 * 
		 * 
		 * ----------------------------------
		 * 
		 * positive sequence equivalent
		 * 
		 * Boundary Bus Id	BaseVolt(kV)	Thevenin Voltage Mag(PU)	Thevenin Voltage Angle (deg)	Zpos Real #Bus4	Zpos Imag #Bus4	Zpos Real #Bus7	Zpos Imag #Bus7	Zzero Real #Bus4	Zzero Imag #Bus4	Zzero Real #Bus7	Zzero Imag #Bus7
            Bus4	230	1.048800154	  -1.074443982	   0.005747175	0.084890458	0.090255205	0.723977837	6.41E-04	0.053685967	1.605920473	5.392289508
            Bus7	230	1.030759768	  10.00469295	   0.090255205	0.723977837	0.009390949	0.112487511	1.605920473	5.392289508	5.26E-04	0.056449647

		 * 
		 */
	}
	

}
