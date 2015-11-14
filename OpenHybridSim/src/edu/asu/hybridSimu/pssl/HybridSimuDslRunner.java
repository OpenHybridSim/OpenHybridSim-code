package edu.asu.hybridSimu.pssl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.plugin.cmd.AclfDslRunner;
import org.interpss.pssl.plugin.cmd.BaseDStabDslRunner;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;
import org.interpss.pssl.simu.IpssDStab;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;

import edu.asu.hybridSimu.HybridSimEquivalentType;
import edu.asu.hybridSimu.HybridSimuHelper;
import edu.asu.hybridSimu.NetworkEquivalentHelper;
import edu.asu.hybridSimu.ProtocolSwitchHelper;
import edu.asu.hybridSimu.SequenceNetworkSolver;
import edu.asu.hybridSimu.SocketServerHelper;
import edu.asu.hybridSimu.HybidSimSubNetworkHelper;
/**
 * 
 * @author Qiuhua Huang
 *
 */
public class HybridSimuDslRunner extends BaseDStabDslRunner{
	
	
    private HybridSimuHelper  hsHelper;
	
	/**
	 * constructor
	 * 
	 * @param net DStabilityNetwork object
	 */
	public HybridSimuDslRunner(DStabilityNetwork net) {
		this.net = net;
		
	}
	
	public HybridSimuDslRunner() {
		// TODO Auto-generated constructor stub
	}

	@Override
	protected  IDStabSimuOutputHandler  runDstab(DstabRunConfigBean dstabConfigBean) throws Exception{
		
		HybridSimuConfigBean hsConfigBean =(HybridSimuConfigBean) dstabConfigBean;
		
        IpssDStab dstabDSL = new IpssDStab(net);
        
        
        //step-1 run power flow to for network initialization in the following steps
        if(!net.isLfConverged()){
	        try {
				boolean converged = new AclfDslRunner()
				                        .setNetwork(net)
						                .run(hsConfigBean.acscConfigBean.runAclfConfig);
				
				if(!converged) {
					throw new Error("Load flow is not coverged! Cannot proceed to the initialization step of hybrid simulation");
				}
				
				
			} catch (InterpssException e) {
				
				e.printStackTrace();
			}
        }
        
        /*
		 * step-2 create sub-network helper for the study case and  define boundary buses
		 * 
		 */
		HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(net);
		
		String[] interfaceBranches = hsConfigBean.tieLinebranchIdAry;
		Boolean[] boundaryBusSides = hsConfigBean.boundaryAtFromBusSide;
		
		if(interfaceBranches.length!=boundaryBusSides.length){
			 try {
				throw new Exception("The two internal network boundary configuration arrays are NOT of equal length!");
			} catch (Exception e) {
			
				e.printStackTrace();
			}
		}
		
		for(int i=0;i<interfaceBranches.length;i++){
		      subNetHelper.addSubNetInterfaceBranch(interfaceBranches[i], boundaryBusSides[i]);
		}
	
		
		
		NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
	  	HybridSimuHelper hySimHelper = new HybridSimuHelper(equivHelper);
	  	
	  	

      /*
       *  step-3 hybrid simulation setting
       */
     // 1) common dstab simulation setting
	
		dstabDSL.setTotalSimuTimeSec(hsConfigBean.totalSimuTimeSec)
		        .setSimuTimeStep(hsConfigBean.simuTimeStepSec)
		        .setIntegrationMethod(hsConfigBean.dynMethod)
		        .setRefMachine(hsConfigBean.referenceGeneratorId);
		
		
		StateMonitor sm = new StateMonitor();
		sm.addBusStdMonitor(hsConfigBean.monitoringBusAry);
		sm.addGeneratorStdMonitor(hsConfigBean.monitoringGenAry);
		
		// set the output handler
		dstabDSL.setDynSimuOutputHandler(sm)
		        .setSimuOutputPerNSteps(hsConfigBean.outputPerNSteps);
		
		/*
	  	 * process internal sub-system, to set the buses and branches in internal network
	  	 * to out of service; set boundary buses equivalent load to zero, which will be represented 
	  	 * by boundary bus current injection during Dstab simulation
	  	 * 
	  	 */
	  	try {
			hySimHelper.procInternalSubNetwork();
		} catch (Exception e) {
			e.printStackTrace();
		}
	  	
	  	//Set hybrid simulation mode,  positive sequence data based,three-phase balanced application
	  	hySimHelper.setPosSeqEquivalentMode(hsConfigBean.application_type ==HybridSimEquivalentType.POSITIVE_SEQUENCE);
	  	
	        // set the sequence current unit sent from the pscad side
	  	
	  	hySimHelper.setSeqCurrentUnit(UnitType.PU); // default is pu
	  
	  	
	 	/*
	  	 * create the server socket, port number should be consistent with the 
	  	 * IP Port Number defined in the Socket_Component in the PSCAD side
	  	 * 
	  	 */
	  	hySimHelper.setupServerSocket(hsConfigBean.socket_port, (int)(hsConfigBean.socket_timeout_sec*1000.0));
	  	
	  	
	  	
	  	//Explicitly setting the msg format for communication 
	  	hySimHelper.enableMsgSizeHeader(false);


	  	if(dstabDSL.initialize()){
	
				hySimHelper.runHybridSimu(dstabDSL.getDstabAlgo(),hsConfigBean);
			
	  	}
	  	else
	  		IpssLogger.getLogger().severe("Dynamic system is not properly initialized for TS simulation, "
	  				+ "hybrid simulaiton cann't be started");
	  
	
		  return dstabDSL.getOutputHandler();
		
		
	}
	@Override
	public BaseJSONBean loadConfigBean(String beanFileName) {
		
		try {
			dstabBean = BaseJSONBean.toBean(beanFileName, HybridSimuConfigBean.class);
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
		return dstabBean;
	}
	

}
