package edu.asu.hybridSim.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSimu.pssl.HybridSimuConfigBean;
import edu.asu.hybridSimu.pssl.HybridSimuDslRunner;

public class TestHybridSimuDslRunner {
	
	@Test
	public void testHybridSimuDslRunner() throws InterpssException{
		
		
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonSpringFactory.getIpssMsgHub();
	
	    HybridSimuDslRunner hsDslRunner = new HybridSimuDslRunner();
	    hsDslRunner.run(hsDslRunner.loadConfigBean("testData/json/ieee9_hs_config_bus5.json"));
	}
	
	@Test
	public void testHybridSimuConfig(){
		try {
			HybridSimuConfigBean hsBean = BaseJSONBean.toBean("testData/json/ieee9_hs_config_bus5.json",HybridSimuConfigBean.class);
			System.out.println(hsBean.toString());
			assertTrue(hsBean.tieLinebranchIdAry[0].equals("Bus5->Bus7(0)"));
			assertTrue(hsBean.tieLinebranchIdAry[1].equals("Bus4->Bus5(0)"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
