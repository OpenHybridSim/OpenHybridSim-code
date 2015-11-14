package edu.asu.hybridSim.test;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.Complex3x1;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSim.util.NumericalUtil4HybridSim;
import edu.asu.hybridSimu.HybridSimuHelper;
import edu.asu.hybridSimu.NetworkEquivalentHelper;
import edu.asu.hybridSimu.HybidSimSubNetworkHelper;

public class TestCalc3PhaseTheveninVoltSource {
	
	@Test
	public void test3PhaseTheveninVolt() throws Exception{
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
	  	
		Hashtable<String, Complex> boundaryBusCurInjTable = hySimHelper.procInternalSubNetwork();
		
		//Z matrix based on IEEE 9Bus system, with Bus5 as boundary bus
		Complex[][] y120        = hySimHelper.getNetEquivHelper().getInterfaceY120Matrix();	
		Complex[][] yabcComplex = hySimHelper.getNetEquivHelper().getInterfaceYabcMatrix();
		
		//NumericalUtil4HybridSim.printComplexMatrix(InterfaceVariableHelper.T);
		//NumericalUtil4HybridSim.printComplexMatrix(InterfaceVariableHelper.Tinv);
		
        System.out.println("y120: ");
		NumericalUtil4HybridSim.printComplexMatrix(y120); 
		
		
		
		System.out.println("yabc: ");
		NumericalUtil4HybridSim.printComplexMatrix(yabcComplex); 
		System.out.println("selfYabc: ");
		NumericalUtil4HybridSim.printComplexMatrix(hySimHelper.getNetEquivHelper().getBoundaryBusSelfYabcMatrix().get("Bus5"));
		Complex[][] yabc_Bus5 =hySimHelper.getNetEquivHelper().getBoundaryBusSelfYabcMatrix().get("Bus5");
		Complex zaa=new Complex(1,0).divide(yabc_Bus5[0][0].add(yabc_Bus5[0][1]).add(yabc_Bus5[0][2]));
		System.out.println("zaa = "+zaa.toString());
		
		
		//voltage set to be V1/2/0 = Vpos, 0,0
		Complex3x1[] v120 = NumericalUtil4HybridSim.creatComplex3x1Array(1);
		
		v120[0].b_1 = dsNet.getBus("Bus5").getVoltage();
		v120[0].c_2 = new Complex(0,0);
		v120[0].a_0 = new Complex(0,0);
				
		
		
		//Injection I120 = poscurrInj, 0.1 ,0.1
		Complex3x1[] busI120 = NumericalUtil4HybridSim.creatComplex3x1Array(1);
		busI120[0].b_1 = boundaryBusCurInjTable.get("Bus5");
		busI120[0].c_2 = new Complex(0,0);
		busI120[0].a_0 = new Complex(0,0);
		
		System.out.println("\n\nIinj of Bus5 ="+busI120[0].toString());
		
		//calculate the Vabc_open
		Hashtable<String, Complex3x1> Vabc_open =hySimHelper.calc3PhaseTheveninVoltSource(v120, busI120);
		
		Complex3x1 Vthabc_bus5 =Vabc_open.get("Bus5");
		System.out.println("\n\nVten(abc) of Bus5 ="+Vthabc_bus5.toString());
		assertTrue(Math.abs(Vthabc_bus5.a_0.getReal()-1.7499)<1.0E-4);
		assertTrue(Math.abs(Vthabc_bus5.a_0.getImaginary()-0.06882)<1.0E-4);
		
		assertTrue(Math.abs(Vthabc_bus5.b_1.getReal()+0.81535)<1.0E-4);
		assertTrue(Math.abs(Vthabc_bus5.b_1.getImaginary()+1.54986)<1.0E-4);

		double[] threePhaseThevenVolt = hySimHelper.get3PhaseTheveninVoltSourceAry(v120, busI120);
		System.out.println(Arrays.toString(threePhaseThevenVolt));
		assertTrue(Math.abs(threePhaseThevenVolt[0]-232.54931428592553)<1.0E-5);
		assertTrue(Math.abs(threePhaseThevenVolt[1]-2.2521872721312803)<1.0E-5);
		
		//System.out.println("\n\nVten(012) of Bus5 ="+Complex3x1.abc_to_z12(Vabc_open.get("Bus5")).toString());
	}
	
	//@Test
	public void testSinglePort3PhaseEquivDataOutPut()throws Exception{
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
			
			/*
			 * create Interface Variable Helper for the study case and 
			 * predefined boundary buses
			 * 
			 */
			//Bus5 as the only boundary bus
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			subNetHelper.addSubNetInterfaceBranch("Bus5->Bus7(0)", true);
			subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
		  	//HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
		  	
		  	/*
		  	 * process internal sub-system, to set the buses and branches in internal network
		  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
		  	 * by boundary bus current injection during Dstab simulation
		  	 * 
		  	 */
		  	
			//ifVHelper.procInternalSubNetwork(subNetHelper);

			
			equivHelper.calcNSaveBoundaryBus3PhaseEquivParam("output/IEEE9_Bus5_3Phase_Equiv.csv");
	}
	@Test
	public void testMultiPort3PhaseEquivDataOutPut()throws Exception{
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
		  	
		  	/*
		  	 * process internal sub-system, to set the buses and branches in internal network
		  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
		  	 * by boundary bus current injection during Dstab simulation
		  	 * 
		  	 */
		  	
			//ifVHelper.procInternalSubNetwork(subNetHelper);
			
			equivHelper.calcNSaveBoundaryBus3PhaseEquivParam("output/IEEE9_Bus257_3Phase_Equiv_netEquivHelper.csv");
	
	        /*
	         * Boundary Bus Id	BaseVolt(kV)	Phase A Vth Mag(PU)	Phase A Angle (deg)	Zaa(R) pu	Zaa(X) pu	Zab(R) pu	Zab(X) pu	Zac(R) pu	Zac(X) pu	Zpos (R) #Bus5	Zpos (X) #Bus5	Zpos (R) #Bus7	Zpos (X) #Bus7	Zzero (R) #Bus5	Zzero(X) #Bus5	Zzero (R) #Bus7	Zzero(X) #Bus7
               Bus5	           230	              223.1227002	-1.659933718	     0.028942278	0.286848496	0.139802999	1.654301245	0.139802999	1.654301245	0.017979431	0.188704256	0.028234432	0.145671275	0.028942278	0.286848496	0.079861789	0.396598638
               Bus7	           230	              230.8851325	 0.153734524	     0.069280092	0.581219245	1.279160058	2.186604709	1.279160058	2.186604709	0.028234432	0.145671275	0.099635941	0.338510496	0.079861789	0.396598638	0.069280092	0.581219245

	         */
			
			
	
	}

}
