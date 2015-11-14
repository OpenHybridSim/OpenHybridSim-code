package edu.asu.hybridSim.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	
	TestCalc3PBalancedEquivParam.class,
	TestCalc3PhaseTheveninVoltSource.class,
	testCustomCurInjDstab.class,
	//TestHybridSimuHelper.class,
	TestNetworkEquivHelper.class,
	//TestSocketServerHelper.class,
	TestSubNetworkHelper.class,
	
})
public class HybridSimBasicTestSuite {

}
