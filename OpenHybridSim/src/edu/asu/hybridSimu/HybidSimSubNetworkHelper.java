package edu.asu.hybridSimu;

import java.util.ArrayList;
import java.util.List;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.Bus;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetworkFactory;
import com.interpss.dstab.DStabilityNetwork;
/**
 * 
 * @author Qiuhua Huang
 * School of Electrical, Computer and Energy Engineering
 * Ira A. Fulton Schools of Engineering
 * Arizona State University
 * Email: qhuang24@asu.edu
 *
 */
public class HybidSimSubNetworkHelper {
	
	private BaseAclfNetwork<? extends AclfBus,? extends AclfBranch> net = null;
	private List<ChildNetInterfaceBranch> cutSetList = null;
	List<String> cutSetBranchIdList = null;
	private String[] boundaryBusIdAry = null;
	private List<String> boundaryBusIdList =null;
	private List<String> internalNetworkBusList =null;
	private List<String> internalNetworkBranchList =null;
	private boolean onlyActiveBranch = true;
	private boolean boundaryBranchAsExternal = false;
	private boolean subNetworkSearched = false;
	


	public HybidSimSubNetworkHelper(BaseAclfNetwork<?,?> net ){
		this.net = net;
		cutSetList = new ArrayList();
		cutSetBranchIdList = new ArrayList();
		boundaryBusIdList = new ArrayList();
		internalNetworkBusList = new ArrayList();
		internalNetworkBranchList = new ArrayList();

		
	}
	/**
	 * create an interface branch with information of which side is close to the sub-network
	 * @param branchId
	 * @param subNetFromBusSide
	 * @return
	 */
	public ChildNetInterfaceBranch  addSubNetInterfaceBranch(String branchId, boolean subNetFromBusSide){
		
		if(this.net.getBranch(branchId)!=null){
			ChildNetInterfaceBranch intBranch = ChildNetworkFactory.eINSTANCE.createChildNetInterfaceBranch();
			intBranch.setBranchId(branchId);
			intBranch.setChildNetSide(subNetFromBusSide? BranchBusSide.FROM_SIDE: BranchBusSide.TO_SIDE);
			this.cutSetList.add(intBranch);
			// add the branch id to the branch Id list
			this.cutSetBranchIdList.add(branchId);
			
			return intBranch;
		}
		else{
			IpssLogger.getLogger().severe("The branchId is invalid # " +branchId);
		}
		return null;
		
	}
	
	/**
	 * search the sub network and determine the boundary buses, internal network buses and branches
	 * set the status of buses and branches in the master (full) network  which  are now within the internal network
	 * to be false/ out-of-service
	 * 
	 * @return
	 */
	public boolean searchSubNetwork(){
		/*
		 * starting from one interface (or cutset) branch to search all the internal network. The
		 * search is bounded by the interface branches
		 * 
		 */

		if(cutSetList.size()>0){
			
			//get the boundary bus list
			for(ChildNetInterfaceBranch branch: cutSetList){
				Bus boundaryBus = branch.getChildNetSide()==BranchBusSide.FROM_SIDE? 
						this.net.getBranch(branch.getBranchId()).getFromAclfBus():
							this.net.getBranch(branch.getBranchId()).getToAclfBus();
				
				if(!this.boundaryBusIdList.contains(boundaryBus.getId())){
				    this.boundaryBusIdList.add(boundaryBus.getId());
				    
				}
			}
			
			/*
			 * NOTE: 02/09/2015
			 * this implementation only works under the condition with all the detailed system are directly connected
			 * Need to iterate over all boundary buses 
			
			// Use the DFS search to process the internal network
			ChildNetInterfaceBranch intBranch = cutSetList.get(0);
			Bus startBus = intBranch.getChildNetSide()==BranchBusSide.FROM_SIDE? 
					this.net.getBranch(intBranch.getBranchId()).getFromAclfBus():
						this.net.getBranch(intBranch.getBranchId()).getToAclfBus();
			
			DFS(startBus.getId());
			 */
			for( String busId:this.boundaryBusIdList){
				if(!this.net.getBus(busId).isVisited()){
					DFS(busId);
				}
			}
			
		
		}
		return subNetworkSearched=true;
		
	}
	private boolean DFS(String busId) {
		boolean isToBus = true;
      
		Bus source = this.net.getBus(busId);
        //System.out.println("BusId, Name, kV: "+busId+","+source.getName()+","+source.getBaseVoltage()*0.001);
        
		for (Branch bra : source.getBranchList()) {

			if (!this.cutSetBranchIdList.contains(bra.getId()) && !bra.isGroundBranch() 
					&& bra instanceof AclfBranch) {
				isToBus = bra.getFromBus().getId().equals(busId);
				String nextBusId = isToBus ? bra.getToBus().getId() : bra.getFromBus().getId();

				if (!bra.isVisited() && (onlyActiveBranch? bra.isActive():true)) { // fromBusId-->buId
					
					// update the visit state
					this.net.getBus(nextBusId).setVisited(true);
					
					bra.setVisited(true);
					
					// the lines connecting boundary buses will be excluded from the internal network branch list
					if(boundaryBranchAsExternal){
						if(!(this.boundaryBusIdList.contains(bra.getFromBus().getId()) && 
								this.boundaryBusIdList.contains(bra.getToBus().getId())))
						      this.internalNetworkBranchList.add(bra.getId());
						}
					else
						 this.internalNetworkBranchList.add(bra.getId());
					
					if(!this.internalNetworkBusList.contains(nextBusId) 
							&& !this.boundaryBusIdList.contains(nextBusId))
				    this.internalNetworkBusList.add(nextBusId);
					
				    //DFS searching
				    DFS(nextBusId);
					
				}
			}
		}

		return true;
	}
	

	public BaseAclfNetwork<?, ?> getNet() {
		return net;
	}

	public void setNet(BaseAclfNetwork<?, ?> net) {
		this.net = net;
	}

	public List<ChildNetInterfaceBranch> getCutSetList() {
		return cutSetList;
	}

	public void setCutSetList(List<ChildNetInterfaceBranch> cutSetList) {
		this.cutSetList = cutSetList;
	}

	public String[] getBoundaryBusIdAry() {
		return boundaryBusIdAry = boundaryBusIdList.toArray(new String[]{"1"});
	}


	public List<String> getBoundaryBusIdList() {
		return boundaryBusIdList;
	}

	public List<String> getInternalNetworkBusList() {
		return internalNetworkBusList;
	}


	public List<String> getInternalNetworkBranchList() {
		return internalNetworkBranchList;
	}


	
	public boolean isBoundaryBranchAsExternal() {
		return boundaryBranchAsExternal;
	}
	public void setBoundaryBranchAsExternal(boolean boundaryBranchAsExternal) {
		this.boundaryBranchAsExternal = boundaryBranchAsExternal;
	}
	
	public boolean isSubNetworkSearched() {
		return subNetworkSearched;
	}
	public void setSubNetworkSearched(boolean subNetworkSearched) {
		this.subNetworkSearched = subNetworkSearched;
	}
	
}
