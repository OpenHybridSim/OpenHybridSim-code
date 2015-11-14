package edu.asu.hybridSimu.pssl;

import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.common.IDStabSimuOutputHandler;

/**
 *  HybridSimuConfigRunner  reuse the  HybridSimuDslRunner runDstab() method to run hybrid simulation 
 *  based on the setting input through the form of HybridSimuConfigBean
 *  
 *  The major difference from the HybridSimuDslRunner class is that this class uses the DStabilityNetwork object during object creation
 *  and thus it does not require the data file definitions in the configuration file and there is no data import processing involved in 
 *  the runHybridSimu() method.
 * @author Qiuhua Huang
 *
 */
public class HybridSimuConfigRunner {
	
	 private DStabilityNetwork net;
	 
	
	 public HybridSimuConfigRunner(DStabilityNetwork net){
		 this.net = net;
	 }
	public IDStabSimuOutputHandler  runHybridSimu(HybridSimuConfigBean hsConfigBean) throws Exception{
		 return new HybridSimuDslRunner(this.net).runDstab(hsConfigBean);
	}

}
