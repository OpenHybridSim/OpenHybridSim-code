package edu.asu.hybridSim.test;

import static org.junit.Assert.assertTrue;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSimu.HybridSimuHelper;
import edu.asu.hybridSimu.NetworkEquivalentHelper;
import edu.asu.hybridSimu.HybidSimSubNetworkHelper;

public class TestCalc3PBalancedEquivParam {
	
	@Test
	public void ThreePhaseBalancedEquivSample(){
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
		try {
			assertTrue(aclfAlgo.loadflow());
		} catch (InterpssException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		/*
		 * create Interface Variable Helper for the study case and 
		 * predefined boundary buses
		 * 
		 */
		//TODO not include load equivalent of these boundary buses in the sequence Y matrix
		//dsNet.getBus("Bus5").getLoadList().get(0).setStatus(false);
		
		//String[] busIdAry =new String[]{"Bus5","Bus7"};
		
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
		//subNetHelper.setBoundaryBranchAsExternal(false);
		subNetHelper.addSubNetInterfaceBranch("Bus4->Bus5(0)", false);
		subNetHelper.addSubNetInterfaceBranch("Bus7->Bus8(0)", true);
		
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
	  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
	  	
		//Hashtable<String, Complex> equivCurInjTable=calBoundaryBusEquivCurInj(internalNetworkBusList);
		//Hashtable<String, Complex> theveninVoltTable = calcTheveninVoltSource(equivCurInjTable,SequenceCode.POSITIVE,null);
		
	  	
	  	/*
	  	 * process internal sub-system, to set the buses and branches in internal network
	  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
	  	 * by boundary bus current injection during Dstab simulation
	  	 * 
	  	 */
	  	try {
	  		Hashtable<String, Complex> equivCurInjTable= hySimHelper.procInternalSubNetwork();
	  		
	  		//System.out.println(hySimHelper.getInternalNetworkBusList());
	  		//System.out.println(hySimHelper.getInternalNetworkBranchList());
	  		Hashtable<String, Complex> theveninVoltTable = hySimHelper.calcTheveninVoltSource(equivCurInjTable,SequenceCode.POSITIVE,null);
			System.out.println("Vth of Bus5 ="+theveninVoltTable.get("Bus5").abs());
			System.out.println("Vth of Bus7 ="+theveninVoltTable.get("Bus7").abs());
	  		//ifVHelper.calcNSavePositiveEquivParam("output/IEEE9_bus257_equiv_boundarybranchAsExternal_04182014.csv");
			assertTrue(Math.abs(theveninVoltTable.get("Bus5").abs()-1.07375645277)<1.0E-3);
			assertTrue(Math.abs(theveninVoltTable.get("Bus7").abs()-0.99646826625)<1.0E-3);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		}
	  	/*
	  	 * OLD Result (boundary branch as part of the external network):
	  	 * Boundary Bus Id	BaseVolt(kV)	Thevenin Voltage Mag(PU)	Thevenin Voltage Angle (deg)	Zpos Real #Bus5	Zpos Imag #Bus5	Zpos Real #Bus7	Zpos Imag #Bus7	Zzero Real #Bus5	Zzero Imag #Bus5	Zzero Real #Bus7	Zzero Imag #Bus7
           Bus5	230	1.104761631	-1.34105277	0.017979431	0.188704256	0.028234432	0.145671275	0.028942278	0.286848496	0.079861789	0.396598638
           Bus7	230	1.048187533	-9.449875316	0.028234432	0.145671275	0.099635941	0.338510496	0.079861789	0.396598638	0.069280092	0.581219245

          //New result, boundary branches as part of the internal network 04/21/2014
			Boundary Bus Id	BaseVolt(kV)	Thevenin Voltage Mag(PU)	Thevenin Voltage Angle (deg)	Zpos Real #Bus5	Zpos Imag #Bus5	Zpos Real #Bus7	Zpos Imag #Bus7	Zzero Real #Bus5	Zzero Imag #Bus5	Zzero Real #Bus7	Zzero Imag #Bus7
			Bus5	230	1.073756453	-1.187863722	0.016984405	0.18345433	0.225660107	1.526500887	0.026559337	0.274901287	10.2095508	26.20633498
			Bus7	230	0.996468266	-8.619508897	0.225660107	1.526500887	0.090046118	0.323146736	10.2095508	26.20633498	0.058421155	0.534323003



	  	 */
	  	
	}
	
	
	

}
