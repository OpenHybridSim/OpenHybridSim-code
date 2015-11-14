package edu.asu.hybridSimu.pssl;

import java.util.List;

import org.interpss.pssl.plugin.cmd.json.AcscRunConfigBean;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;

import com.interpss.dstab.algo.DynamicSimuMethod;

import edu.asu.hybridSimu.HybridSimEquivalentType;
import edu.asu.hybridSimu.InteractionProtocol;

public class HybridSimuConfigBean extends DstabRunConfigBean{
	/*
	// common setting for Dstab simulation
	
    public String dynamicFileName = "";
	
	public String dstabOutputFileName = "";
	
	public AcscRunConfigBean acscConfigBean = new AcscRunConfigBean();
	
	public DynamicSimuMethod method = DynamicSimuMethod.MODIFIED_EULER;
	
	public double totalSimuTimeSec = 10.0;
	
	public double simuTimeStepSec = 0.005;
	
	//public double eventStartTimeSec = 1.0;
	
	//public double eventDurationSec = 0.01;
	
	public String referenceGeneratorId ="";
	
	public String[] monitoringBusAry ={};
	
	public String[] monitoringGenAry ={};
	
	public int outputPerNSteps =1;
	*/
	
	//==============================================================================
	// The following setting are added for hybrid simulation
	//==============================================================================
	public HybridSimuConfigBean(){
		   // set the default AclfDslRunner class name
		   this.dslRunnerClassName = "edu.asu.hybridSimu.pssl.HybridSimuDslRunner";
		}
	
	
	//1) tie-lines and boundary info
	public String[] tieLinebranchIdAry={};
	public Boolean[] boundaryAtFromBusSide ={};
	
	// 2) application type
	public HybridSimEquivalentType application_type = HybridSimEquivalentType.THREE_PHASE;
	
	//3) interaction and protocol
	// interaction protocol: 0 (parallel), 1(series),2(combined)

	public InteractionProtocol protocol_type = InteractionProtocol.Combined; // combined type, by default
	public int socket_port = 8990;
	public double socket_timeout_sec = 30.0; //30 sec, by default
	
	//4) output log file
	
	
	
	

}
