package edu.asu.hybridSimu;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;

/**
 * The default protocol is parallel, and hybrid simulation is intent to switch to series protocol when there is large
 * change in the current injections from the EMT simulation side.
 * 
 * The criteria for switching is based on the rate of change, i.e., ChangeRate, which is calculated as follows:
 * 
 * (i) deltaImax =max{max( (I(seq,t,i)-I(seq,t-dt,i))/I(seq,t-dt,i)), seq), i}
 * 
 * (ii) ChangeRate = deltaImax/simuStep
 * 
 * 
 * NOTE: parallel protocol -> 0 and  series protocol  -> 1
 * 
 * @author Qiuhua Huang
 * School of Electrical, Computer and Energy Engineering
 * Ira A. Fulton Schools of Engineering
 * Arizona State University
 * Email: qhuang24@asu.edu
 *
 *
 */
public class ProtocolSwitchHelper {
	

	private int last_step_protocol= 0;
	private int new_protocol= 0;
	private double time_counter = 0;
	private double threshold = 0.004;
	private double hybrid_time_step = 0;
	private Complex3x1[] last_step_threeSeqCurrAry  = null;
	private Complex[] last_step_posSeqCurrAry  = null;
	private double series_to_parallel_delay_ms = 33.0; //2 cycles
	private boolean isPositiveSeqOnly = false;
	private double max_step_change_per_step = 0.02;
	private double rate_of_change = 0;
	
	public ProtocolSwitchHelper(double time_step_in_ms){
		this.hybrid_time_step = time_step_in_ms;
		this.threshold=this.max_step_change_per_step/hybrid_time_step;
	}
	
	public ProtocolSwitchHelper(double time_step_in_ms, boolean positiveSeqOnly){
		this.hybrid_time_step = time_step_in_ms;
		this.threshold=threshold/hybrid_time_step;
		this.isPositiveSeqOnly = positiveSeqOnly;
	}
	
	public ProtocolSwitchHelper() {
	
	}
	
 
    
    

	/**
	 *  determine the new protocol based on the max rate of change of the three-sequence current injections
	 * @param threeSeqCurrAry
	 * @return
	 */
	public int determineNewProtocol(Complex3x1[] threeSeqCurrAry){
		double deltaImax = 0;
		
		if (last_step_threeSeqCurrAry  == null){
		   last_step_threeSeqCurrAry  = threeSeqCurrAry;
		   return last_step_protocol = 0; // for the first step, use the default parallel protocol
		}
		else {
			
			  for(int i = 0; i <threeSeqCurrAry.length;i++){
				  Complex3x1 new_i3x1 = threeSeqCurrAry[i];
			      Complex3x1 old_i3x1 = last_step_threeSeqCurrAry[i];
				  double deltaI0 = new_i3x1.a_0.subtract(old_i3x1.a_0).divide(old_i3x1.b_1).abs(); 
				  double deltaI1 = new_i3x1.b_1.subtract(old_i3x1.b_1).divide(old_i3x1.b_1).abs(); 
				  double deltaI2 = new_i3x1.c_2.subtract(old_i3x1.c_2).divide(old_i3x1.b_1).abs(); 
						
				  double tempI120Max = Math.max(deltaI2, Math.max(deltaI0,deltaI1));
				  if (tempI120Max > deltaImax) deltaImax = tempI120Max;
			   }			
						
				last_step_threeSeqCurrAry  = threeSeqCurrAry;	
				
				rate_of_change = deltaImax/hybrid_time_step;
				
				//System.out.println("max rate of change of I120 is " + rate_of_change);
				//System.out.print(","+ rate_of_change);
				
				if (rate_of_change> threshold){
					resetTimeCounter();
					return last_step_protocol = 1;
					
				}
				
				// if last step protocol is series, and rate of change is less than the threshold, two circles delay is required before changing back to parallel protocol
				else if(last_step_protocol == 1){
					if(getTime_counter() >= series_to_parallel_delay_ms){  
						 resetTimeCounter();
						 return last_step_protocol = 0;
					}
					else{
						//System.out.println("--->two circles delay count down");
						updateTime_counter();
						return last_step_protocol = 1;
					}
				}
			}
		
		return last_step_protocol = 0;
		
	
	}
	public int determineNewProtocol(Complex[] posSeqCurrAry){
		
       double deltaImax = 0;
		
		if (last_step_posSeqCurrAry  == null){
		   last_step_posSeqCurrAry  = posSeqCurrAry;
		   return last_step_protocol = 0; // for the first step, use the default parallel protocol
		}
		else {
			
			  for(int i = 0; i <posSeqCurrAry.length;i++){
				  Complex iNow = posSeqCurrAry[i];
			      Complex iOld = last_step_posSeqCurrAry[i];
				  
				  double deltaI1 = iNow.subtract(iOld).divide(iOld).abs(); 
			
				
				  if (deltaI1 > deltaImax) deltaImax = deltaI1;
			   }			
						
				last_step_posSeqCurrAry  = posSeqCurrAry;	
				
				rate_of_change = deltaImax/hybrid_time_step;
				
				//System.out.println("max rate of change of I120 is " + rate_of_change);
				//System.out.print( rate_of_change);
				
				if (rate_of_change> threshold){
					resetTimeCounter();
					return last_step_protocol = 1;
					
				}
				
				// if last step protocol is series, and rate of change is less than the threshold, two circles delay is required before changing back to parallel protocol
				else if(last_step_protocol == 1){
					if(getTime_counter() >= series_to_parallel_delay_ms){  
						 resetTimeCounter();
						 return last_step_protocol = 0;
					}
					else{
						//System.out.println("--->two circles delay count down");
						updateTime_counter();
						return last_step_protocol = 1;
					}
				}
			}
		
		return last_step_protocol = 0;
		
	}
	
    public double getMaxRateOfChange(){
    	return this.rate_of_change;
    }

	public int getLast_step_protocol() {
		return last_step_protocol;
	}

	public void setLast_step_protocol(int last_step_protocol) {
		this.last_step_protocol = last_step_protocol;
	}

	public int getNew_protocol() {
		return new_protocol;
	}

	public void setNew_protocol(int new_protocol) {
		this.new_protocol = new_protocol;
	}

	public double getTime_counter() {
		return this.time_counter;
	}

	public void setTime_counter(double time_counter) {
		this.time_counter = time_counter;
	}
	
	public void updateTime_counter() {
		this.time_counter += hybrid_time_step;
	}
	
	public void resetTimeCounter(){
		this.time_counter =0;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}
	
	
    public void setInteractionTimeStep(double time_step_in_ms){
    	this.hybrid_time_step = time_step_in_ms;
		this.threshold=this.max_step_change_per_step/hybrid_time_step;
    }
		
    public void  setSeries2ParallelSwitchDelay(double delay_time_ms){
    	this.series_to_parallel_delay_ms = delay_time_ms;
    }
    
    /**
     * Define the maximum change allowed when using the parallel time interaction
     * @param maxChangePerStep
     */
    public void setMaximChangePerStep(double maxChangePerStep){
    	this.max_step_change_per_step = maxChangePerStep;
    }
	
	
	
	
}
