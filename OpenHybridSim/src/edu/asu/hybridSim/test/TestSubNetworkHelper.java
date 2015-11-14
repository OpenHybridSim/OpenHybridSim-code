package edu.asu.hybridSim.test;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSimu.HybidSimSubNetworkHelper;

public class TestSubNetworkHelper {
	
	
	@Test
	public void testOnePortEquiv() throws Exception{
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
		
		subNetHelper.searchSubNetwork();
		assertTrue(subNetHelper.getBoundaryBusIdAry().length==1);
		assertTrue(subNetHelper.getBoundaryBusIdAry()[0].equals("Bus5"));
		assertTrue(subNetHelper.getInternalNetworkBusList().size()==0);
		
		
	}
	
	@Test
	public void testTwoPortEquiv() throws Exception{
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
		subNetHelper.addSubNetInterfaceBranch("Bus6->Bus9(0)", true);
		
		subNetHelper.searchSubNetwork();
		assertTrue(subNetHelper.getBoundaryBusIdAry().length==2);
		assertTrue(subNetHelper.getBoundaryBusIdAry()[0].equals("Bus5"));
		assertTrue(subNetHelper.getBoundaryBusIdAry()[1].equals("Bus6"));
		assertTrue(subNetHelper.getInternalNetworkBusList().size()==2);
		assertTrue(subNetHelper.getInternalNetworkBranchList().size()==3);
		//assertTrue(subNetHelper.getInternalNetworkBranchList().get(0).equals(anObject));
		
		
	}

}
