# OpenHybridSim-code

OpenHybridSim is the first-ever, open source tool for EMT and phasor domain hybrid simulation. It is developed by Qiuhua Huang from Arizona State University (ASU), USA.

This is the source code part of the OpenHybridSim project. For the released tool (released as a jar lib), please refer to :
https://github.com/OpenHybridSim/Tool-release

##Installation
First of all, the tool is developed based upon InterPSS (Webpage: www.interpss.org   Code: https://github.com/InterPSS-Project ). Thus, it relies on the InterPSS libs as well as some third party libs. You need to first check out (or download) these libs from https://github.com/InterPSS-Project/ipss-common.
Once these dependent libs are check out, the following steps are recommended:

1) Install a development IDE. Eclipse (http://www.eclipse.org/downloads/ ) is recommended here.

2) Download and import both the ipss.lib and ipss.3rdPty.lib projects (available from https://github.com/InterPSS-Project/ipss-common) into the IDE

3) check out or download this project <OpenHybridSim-code> and import it into the IDE.

4) run the test suite file under the "edu.asu.hybridSim.test" package to make sure all tests are passed, which means the development environment has been properly set up.


##Core classes:

1) HybridSimuHelper.java : it manages and controls the excution logic of and data exchanging among moudules for hybrid simulaiton

2) HybidSimSubNetworkHelper.java: it basically perform network splitting and boundary information management

3) NetworkEquivalentHelper.java: it performs network equivalencing after the full network is split into internal (detailed) and external sub-systems.

4) SocketServerHelper.java : OpenHybridSim interfaces with EMT simulators through TCP/IP socket communication. The socket server is on OpenHybridSim side. And this class provides the functions to manage (create, close) the socket server. It also manage the data processing invovling in sending data or recieving data. 


## Usage and development
Users are recommend to walk through the classes and  test cases to get a basic idea of how the calsses are organized.
In particular, there are corresponding test cases for all the core classes, which are available from https://github.com/OpenHybridSim/OpenHybridSim-code/tree/master/OpenHybridSim/src/edu/asu/hybridSim/test. These test cases serve mainly two objectives: 1) to verify the implementation, 2) to provide user simple examples of how to use a specific class and its APIs 
The IEEE9_HybridSim_Test.java class provide concret and different levels of application examples to users. 

## References
If you use or reference this tool for your work, please cite the following papers:

@ARTICLE{huang2016hyridsimFIDVR,
  author={Q. {Huang} and V. {Vittal}},
  journal={IEEE Transactions on Power Systems}, 
  title={Application of Electromagnetic Transient-Transient Stability Hybrid Simulation to FIDVR Study}, 
  year={2016},
  volume={31},
  number={4},
  pages={2634-2646},}
  
 @inproceedings{huang2016openhybridsim,
  title={OpenHybridSim: An open source tool for EMT and phasor domain hybrid simulation},
  author={Huang, Qiuhua and Vittal, Vijay},
  booktitle={2016 IEEE Power and Energy Society General Meeting (PESGM)},
  pages={1--5},
  year={2016},
  organization={IEEE}
}

## Report issue or any development question
Please contact the developer at qhuang24 AT ASU DOT EDU 





