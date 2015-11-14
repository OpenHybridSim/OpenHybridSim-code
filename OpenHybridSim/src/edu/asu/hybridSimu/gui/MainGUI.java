package edu.asu.hybridSimu.gui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.display.AclfOutFunc.BusIdStyle;
import org.interpss.display.impl.AclfOut_BusStyle;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.pssl.plugin.cmd.DStabDslRunner;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.simu.BaseDSL;
import org.interpss.pssl.simu.IpssAclf.LfAlgoDSL;
import org.interpss.pssl.simu.IpssDStab;
import org.interpss.util.FileUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.algo.AclfMethod;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.spring.CoreCommonSpringFactory;

import edu.asu.hybridSimu.HybidSimSubNetworkHelper;
import edu.asu.hybridSimu.HybridSimEquivalentType;
import edu.asu.hybridSimu.InteractionProtocol;
import edu.asu.hybridSimu.NetworkEquivalentHelper;
import edu.asu.hybridSimu.pssl.HybridSimuConfigBean;
import edu.asu.hybridSimu.pssl.HybridSimuConfigRunner;
import edu.asu.hybridSimu.pssl.HybridSimuDslRunner;

public class MainGUI {

	private JFrame frmOpenhybridsim;
	private JComboBox formatComboBox;
	private JComboBox versionComboBox;
	private JComboBox pfMethodComboBox;
	private JComboBox integrationMethodComboBox;
	private JTextField pfFileTextField;
	private JTextField dynFileTextField;
	private JTextField seqFileTextField;
	private JTextField maxItrTextField;
	private JTextField tolTextField;
	private JTextField totalTimeTextField;
	private JTextField timeStepTextField;
	private JTable busTable;
	private JTable genTable;
	private JTextField socketPortTextField;
	private JTextField socketTimeOutTextField;
	private JTextField outputStepsTextField;
	private JTextField txtForPsseOnly;
	
	private JTextArea msgLog;
	private JFileChooser fc;
	
	private JTextField jsonTextField;
	private JTextField refMachTextField;
	private JTextArea monBusTextArea;
	private JTextArea monGenTextArea;
	
	
	// hybrid simu setting panel
	private JTable branchTable;
	private JTextArea boundaryBranchTextArea;
	private JComboBox protocolComboBox;
	private JComboBox hybridSimTypeCmboBox;
	
	//results panel
	private JCheckBox chckbxPGenMW = null;
	private JCheckBox chckbxQgenMVAr = null;
	private JCheckBox chckbxGenVolt = null;
	private JCheckBox chckbxGenAngle;
	private JCheckBox chckbxGenEfd = null;
	private JCheckBox chckbxGenPmech = null;
	private JCheckBox chckbxGenSpeed = null;
	private JCheckBox chckbxBusVolt =null;
	private JCheckBox chckbxBusFreq = null;
	private JRadioButton rdbtnSummary = null;
	private JRadioButton rdbtnDetailedPFResult = null;
	
	
	private DStabilityNetwork dsNet;
	private IpssAdapter.FileFormat inputDataFmt = IpssAdapter.FileFormat.PSSE;// by default
	private IpssAdapter.PsseVersion inputDataVersion = IpssAdapter.PsseVersion.PSSE_30;// by default
	private boolean isSimuBasedOnSetting = true;
	private String  jsonConfigFileFullPath = "";
	private BaseJSONBean jsonConfig =null;
	private IDStabSimuOutputHandler  dynOutput  = null;
	private boolean isDataImported =false;
	
	
	static private final String newline = "\n";
	static private final double DEFAULT_total_Simu_Time_Sec = 5.0;
	static private final double DEFAULT_simu_Time_Step_Sec = 0.005;
	static private final int DEFAULT_SOCKET_PORT = 8089;
	static private final double DEFAULT_SOCKET_TIME_OUT_SEC = 60.0;
	
	

	

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		// init InterPSS runtime env
		IpssCorePlugin.init();
		BaseDSL.setMsgHub(CoreCommonSpringFactory.getIpssMsgHub());	
		
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainGUI window = new MainGUI();
					window.frmOpenhybridsim.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public MainGUI() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmOpenhybridsim = new JFrame();
		frmOpenhybridsim.setFont(new Font("Dialog", Font.BOLD, 12));
		frmOpenhybridsim.setTitle("OpenHybridSim");
		frmOpenhybridsim.setBounds(100, 100, 912, 744);
		frmOpenhybridsim.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmOpenhybridsim.getContentPane().setLayout(null);
		
		/**
		 *  -----------------------------------------------
		 *                 Msg log area at the button of the GUI
		 *  -----------------------------------------------
		 */
		msgLog = new JTextArea();
		msgLog.setBackground(new Color(224, 255, 255));
		msgLog.setLineWrap(true);
		msgLog.setSelectedTextColor(Color.blue);
		//msgLog.setBounds(0, 557, 771, 47);
		JScrollPane logPane = new JScrollPane(msgLog);
		logPane.setBounds(0, 580, 896, 115);
		frmOpenhybridsim.getContentPane().add(logPane);
		
		logPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		logPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBounds(0, 0, 896, 580);
		frmOpenhybridsim.getContentPane().add(tabbedPane);
		
		JPanel settingPanel = new JPanel();
		settingPanel.setToolTipText("Setting");
		tabbedPane.addTab("Setting", null, settingPanel, null);
		settingPanel.setLayout(null);
		
		JTabbedPane DataTabbedPane = new JTabbedPane(JTabbedPane.TOP);
		DataTabbedPane.setBounds(0, 0, 891, 552);
		settingPanel.add(DataTabbedPane);
		
		/*-------------------------------------------------------------------------
		 *                   data setting panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */

		
		initDataSettingPanel(DataTabbedPane);

				

		/*-------------------------------------------------------------------------
		 *                    Power flow setting panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */		
				
				
		initPowerflowSettingPanel(DataTabbedPane);
		
		
		/*-------------------------------------------------------------------------
		 *                    Transient stability setting panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */
		
	
		initTransientStabilitySettingPanel(DataTabbedPane);
		
		
		/*-------------------------------------------------------------------------
		 *                    Hybrid simulation setting panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */
		
		initHybridSimuSettingPanel(DataTabbedPane);
		
		
		/**
		 * -----------------------------------------------------------------------
		 *                         Setting import/export panel
		 * -----------------------------------------------------------------------                         
		 */
		
		/*-------------------------------------------------------------------------
		 *                    Data viewer panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */
		
//		JPanel ModelPanel = new JPanel();
//		tabbedPane.addTab("Data viewer", null, ModelPanel, null);
		
		
		/*-------------------------------------------------------------------------
		 *                    Simulation panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */
		
		initSimulationPanel(tabbedPane);
		
		
		/*-------------------------------------------------------------------------
		 *                   Results panel
		 *-------------------------------------------------------------------------                    
		 * 
		 */
		
		
		initSaveResultsPanel(tabbedPane);
		
		
		
	}

	private void initSaveResultsPanel(JTabbedPane tabbedPane) {
		JPanel ResultPanel = new JPanel();
		tabbedPane.addTab("Results", null, ResultPanel, null);
		ResultPanel.setLayout(null);
		
		
		JButton btnSaveDynGenResults = new JButton("Save Generator Results");
		btnSaveDynGenResults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				try {
					saveDynGenResults();
				} catch (IOException e1) {
					
					e1.printStackTrace();
					msgLog.append("Error during saving Generator results, caused  by ->" +e1.getMessage());
				}
				
			}
		});
		btnSaveDynGenResults.setBounds(403, 96, 180, 33);
		ResultPanel.add(btnSaveDynGenResults);
		
		JLabel lblGenerators = new JLabel("Generator Results");
		lblGenerators.setFont(new Font("Tahoma", Font.PLAIN, 14));
		lblGenerators.setBounds(51, 20, 163, 23);
		ResultPanel.add(lblGenerators);
		
		JLabel lblBuses_1 = new JLabel("Bus Results");
		lblBuses_1.setFont(new Font("Tahoma", Font.PLAIN, 14));
		lblBuses_1.setBounds(51, 240, 132, 23);
		ResultPanel.add(lblBuses_1);
		
		this.chckbxPGenMW = new JCheckBox("Real power(pu on machine base)");
		chckbxPGenMW.setBounds(86, 54, 225, 23);
		ResultPanel.add(chckbxPGenMW);
		
		this.chckbxQgenMVAr = new JCheckBox("Reactive power(pu on machine base)");
		chckbxQgenMVAr.setBounds(86, 80, 268, 23);
		ResultPanel.add(chckbxQgenMVAr);
		
		this.chckbxGenAngle = new JCheckBox("Angle (rad)");
		chckbxGenAngle.setBounds(86, 106, 151, 23);
		ResultPanel.add(chckbxGenAngle);
		
		this.chckbxGenSpeed = new JCheckBox("Speed (pu)");
		chckbxGenSpeed.setBounds(86, 132, 97, 23);
		ResultPanel.add(chckbxGenSpeed);
		
		this.chckbxGenEfd = new JCheckBox("Efd (exciter output, pu)");
		chckbxGenEfd.setBounds(86, 159, 191, 23);
		ResultPanel.add(chckbxGenEfd);
		
		this.chckbxGenPmech = new JCheckBox("Mechanical power (pu on machine base)");
		chckbxGenPmech.setBounds(86, 185, 279, 23);
		ResultPanel.add(chckbxGenPmech);
		
		
		JSeparator separator = new JSeparator();
		separator.setBounds(0, 215, 881, 14);
		ResultPanel.add(separator);
		
		JButton btnSaveDynBusResults = new JButton("Save Bus Results");
		btnSaveDynBusResults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				try {
					saveDynBusResults();
				} catch (IOException e1) {
					e1.printStackTrace();
					msgLog.append("Error during saving Bus results, caused  by ->" +e1.getMessage());
				}
			}
		});
		
		btnSaveDynBusResults.setBounds(403, 282, 180, 33);
		ResultPanel.add(btnSaveDynBusResults);
		
		this.chckbxBusVolt = new JCheckBox("Voltage magnitude");
		chckbxBusVolt.setBounds(86, 282, 151, 23);
		ResultPanel.add(chckbxBusVolt);
		
		this.chckbxBusFreq = new JCheckBox("Frequency (Hz)");
		chckbxBusFreq.setBounds(86, 308, 151, 23);
		ResultPanel.add(chckbxBusFreq);
		

		
		JSeparator separator_1 = new JSeparator();
		separator_1.setBounds(10, 385, 871, 14);
		ResultPanel.add(separator_1);
		
		JLabel lblPowerFlowResults = new JLabel("Power flow results");
		lblPowerFlowResults.setFont(new Font("Tahoma", Font.PLAIN, 14));
		lblPowerFlowResults.setBounds(51, 401, 132, 23);
		ResultPanel.add(lblPowerFlowResults);
		
		this.rdbtnSummary = new JRadioButton("Summary");
		rdbtnSummary.setBounds(74, 431, 109, 23);
		ResultPanel.add(rdbtnSummary);
		
		this.rdbtnDetailedPFResult = new JRadioButton("Detailed bus and branch results");
		rdbtnDetailedPFResult.setBounds(74, 457, 251, 23);
		ResultPanel.add(rdbtnDetailedPFResult);
		
         ButtonGroup btnGroup = new ButtonGroup();
		
		btnGroup.add(rdbtnSummary);
		btnGroup.add(rdbtnDetailedPFResult );
		
		JButton btnSavePfResults = new JButton("Save power flow results");
		btnSavePfResults.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				savePowerflowResults();
			}
		});
		btnSavePfResults.setBounds(403, 414, 191, 33);
		ResultPanel.add(btnSavePfResults);
		
		
	}

	private void initSimulationPanel(JTabbedPane tabbedPane) {
		JPanel SimulationPanel = new JPanel();
		tabbedPane.addTab("Simulation", null, SimulationPanel, null);
		SimulationPanel.setLayout(null);
		
		JRadioButton rdbtnSetting = new JRadioButton("Based on Setting");
		rdbtnSetting.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				isSimuBasedOnSetting = true;
				msgLog.append("Simulation is based on Setting."+newline);
			}
		});
		rdbtnSetting.setBounds(40, 7, 186, 23);
		rdbtnSetting.setSelected(true);
		SimulationPanel.add(rdbtnSetting);
		
		
		JRadioButton rdbtnJSONConfig = new JRadioButton("Based on JSON configuration file");
		rdbtnJSONConfig.setBounds(40, 29, 263, 23);
		SimulationPanel.add(rdbtnJSONConfig);
		rdbtnJSONConfig.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				isSimuBasedOnSetting = false;
				jsonTextField.setEnabled(true);
				msgLog.append("Simulation is based on JSON configuraiton file."+newline);
			}
		});
		
		ButtonGroup btnGroup = new ButtonGroup();
		
		btnGroup.add(rdbtnSetting);
		btnGroup.add(rdbtnJSONConfig);
	
		
		
		JButton btnRunPowerFlow = new JButton("Power flow");
		btnRunPowerFlow.setBounds(51, 109, 143, 35);
		SimulationPanel.add(btnRunPowerFlow);
		btnRunPowerFlow.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				runPowerFlow();
			}
		});
		
		JButton btnRunTransientStability = new JButton("Transient stability");
		btnRunTransientStability.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				runTransientStability();
			}
		});
		btnRunTransientStability.setBounds(51, 160, 143, 35);
		SimulationPanel.add(btnRunTransientStability);
		
		JButton btnRunHybridSimulation = new JButton("Hybrid Simulation");
		btnRunHybridSimulation.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				msgLog.append("Start hybrid simulation..."+newline);
				
				//runHybridSimulation();
				//use swingworker to separate the swing GUI and long-time-running background application
				new  HybridSimuWorker().execute();
				//msgLog.append("Hybrid simulation completed"+newline);
			}
		});
		btnRunHybridSimulation.setBounds(51, 206, 143, 35);
		SimulationPanel.add(btnRunHybridSimulation);
		
		jsonTextField = new JTextField();
		jsonTextField.setBounds(50, 59, 376, 20);
		SimulationPanel.add(jsonTextField);
		jsonTextField.setColumns(10);
		jsonTextField.setEnabled(false);
		
		JButton btnSelectConfigurationFile = new JButton("select configuration file");
		btnSelectConfigurationFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// only select the JSON file
				
				JFileChooser jsonFc =  new JFileChooser(".");
				FileFilter json = new FileNameExtensionFilter("JSON Config", "json");
				 jsonFc.addChoosableFileFilter(json);
				 
				 int returnVal = jsonFc.showOpenDialog(frmOpenhybridsim);
				 
		            if (returnVal == JFileChooser.APPROVE_OPTION) {
		                File file = jsonFc.getSelectedFile();
		                jsonTextField.setText(file.getAbsolutePath());
		                
		                //This is where a real application would open the file.
		                msgLog.append("Selected json config file: " + file.getName() + "." + newline);
		            }
				 
			}
		});
		btnSelectConfigurationFile.setBounds(482, 58, 186, 23);
		SimulationPanel.add(btnSelectConfigurationFile);
		
		JButton btnNetworkEquivalent = new JButton("Build Equivalent");
		btnNetworkEquivalent.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				try {
					buildExternalSystemEquivalent();
				} catch (Exception e) {
					
					e.printStackTrace();
					msgLog.append("Error in building external system equivalent, caused by ->"+e.getMessage());
				}
			}
		});
		btnNetworkEquivalent.setBounds(51, 263, 145, 37);
		SimulationPanel.add(btnNetworkEquivalent);
	}

	private void initHybridSimuSettingPanel(JTabbedPane DataTabbedPane) {
		JPanel HybridSimuPanel = new JPanel();
		DataTabbedPane.addTab("Hybrid simulation", null, HybridSimuPanel, null);
		HybridSimuPanel.setLayout(null);
		//HybridSimuPanel.setLayout(new GridLayout(1,1));
		
		
		JLabel lblInteractionProtocol = new JLabel("Interaction Protocol");
		lblInteractionProtocol.setBounds(37, 52, 123, 14);
		HybridSimuPanel.add(lblInteractionProtocol);
		
		protocolComboBox = new JComboBox();
		protocolComboBox.setBounds(170, 49, 129, 20);
		HybridSimuPanel.add(protocolComboBox);
		
		
		JLabel lblSocketPort = new JLabel("Socket port");
		lblSocketPort.setBounds(37, 83, 105, 14);
		HybridSimuPanel.add(lblSocketPort);
		
		socketPortTextField = new JTextField();
		socketPortTextField.setBounds(170, 80, 55, 20);
		HybridSimuPanel.add(socketPortTextField);
		socketPortTextField.setText(Integer.toString(DEFAULT_SOCKET_PORT));
		
		JLabel lblSocketTimeOut = new JLabel("Socket time out (s)");
		lblSocketTimeOut.setBounds(37, 114, 113, 14);
		HybridSimuPanel.add(lblSocketTimeOut);
		
		socketTimeOutTextField = new JTextField();
		socketTimeOutTextField.setBounds(170, 111, 55, 20);
		HybridSimuPanel.add(socketTimeOutTextField);
		socketTimeOutTextField.setText(Double.toString(DEFAULT_SOCKET_TIME_OUT_SEC));
		
		JLabel lblNewLabel_7 = new JLabel("Application type");
		lblNewLabel_7.setBounds(37, 27, 95, 14);
		HybridSimuPanel.add(lblNewLabel_7);
		
		hybridSimTypeCmboBox = new JComboBox();
		hybridSimTypeCmboBox.setBounds(170, 24, 160, 20);
		HybridSimuPanel.add(hybridSimTypeCmboBox);
		
		
//		JTextArea txtrPositiveSequenceOnly = new JTextArea();
//		txtrPositiveSequenceOnly.setBounds(346, 11, 511, 44);
//		txtrPositiveSequenceOnly.setLineWrap(true);
//		txtrPositiveSequenceOnly.setText("Positive sequence only uses 1-phase  Thevenin equivalent, "
//				+ "while 3-phase application requires a generic 3-phase Thevenin equivalent");
//		HybridSimuPanel.add(txtrPositiveSequenceOnly);
		
		

		branchTable = new JTable();
		branchTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
		JScrollPane branchScrollPane = new JScrollPane(branchTable);
		branchScrollPane.setBounds(21, 188, 614, 224);
		HybridSimuPanel.add(branchScrollPane);
		branchScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		branchScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		boundaryBranchTextArea = new JTextArea();
		//boundaryBranchTextArea.setBounds(695, 237, 149, 215);
		JScrollPane selectedBranchScrollPane = new JScrollPane(boundaryBranchTextArea);
		selectedBranchScrollPane.setBounds(740, 188, 136, 224);
		HybridSimuPanel.add(selectedBranchScrollPane);
		selectedBranchScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		selectedBranchScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		
		JButton btnNewButton_1 = new JButton("Select");
		btnNewButton_1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int[] selectedRows = branchTable.getSelectedRows();
				int [] selectedRowsInModel= new int[selectedRows.length];
				for(int i =0;i<selectedRows.length;i++){
					selectedRowsInModel[i]=branchTable.convertRowIndexToModel(selectedRows[i]);
					String selectedBranchId = (String) branchTable.getModel().getValueAt(selectedRowsInModel[i], 0); // idx =0 busID, 
					String boundarySide = Boolean.toString((boolean)branchTable.getModel().getValueAt(selectedRowsInModel[i], 4)); //idx=5,boundaryAtFromBusSide
					
					boundaryBranchTextArea.append(selectedBranchId+","+boundarySide+newline);
				}
				
			}
		});
		btnNewButton_1.setBounds(653, 334, 77, 23);
		HybridSimuPanel.add(btnNewButton_1);
		
		JLabel lblNewLabel_6 = new JLabel("Boundary Branches");
		lblNewLabel_6.setBounds(752, 163, 124, 14);
		HybridSimuPanel.add(lblNewLabel_6);
		
		JLabel lblDefineTheDetailed = new JLabel("Select interface branches connecting the detailed  and external system");
		lblDefineTheDetailed.setBounds(34, 163, 421, 14);
		HybridSimuPanel.add(lblDefineTheDetailed);
		
		

		
		for(InteractionProtocol p:InteractionProtocol.values()){
			protocolComboBox.addItem(p);
		}
		
		for(HybridSimEquivalentType type: HybridSimEquivalentType.values())
		    hybridSimTypeCmboBox.addItem(type);
	}

	private void initTransientStabilitySettingPanel(JTabbedPane DataTabbedPane) {
		JPanel DynamicSettingPanel = new JPanel();
		DataTabbedPane.addTab("Transient stability", null, DynamicSettingPanel, null);
		DynamicSettingPanel.setLayout(null);
		
		JLabel lblNewLabel_4 = new JLabel("Integration Method");
		lblNewLabel_4.setBounds(23, 11, 115, 14);
		DynamicSettingPanel.add(lblNewLabel_4);
		
		JLabel lblNewLabel_5 = new JLabel("Total simulation time (s)");
		lblNewLabel_5.setBounds(23, 44, 144, 14);
		DynamicSettingPanel.add(lblNewLabel_5);
		
		integrationMethodComboBox = new JComboBox();
		integrationMethodComboBox.setBounds(177, 8, 115, 20);
		DynamicSettingPanel.add(integrationMethodComboBox);
		integrationMethodComboBox.addItem(DynamicSimuMethod.MODIFIED_EULER); // now only modified euler is implemented
		
		
		totalTimeTextField = new JTextField();
		totalTimeTextField.setBounds(177, 41, 86, 20);
		DynamicSettingPanel.add(totalTimeTextField);
		totalTimeTextField.setText(Double.toString(this.DEFAULT_total_Simu_Time_Sec));
		
		JLabel lblSimulationTimeStep = new JLabel("Simulation time step (s)");
		lblSimulationTimeStep.setBounds(23, 72, 144, 14);
		DynamicSettingPanel.add(lblSimulationTimeStep);
		
		timeStepTextField = new JTextField();
		timeStepTextField.setBounds(177, 69, 86, 20);
		DynamicSettingPanel.add(timeStepTextField);
		timeStepTextField.setText(Double.toString(this.DEFAULT_simu_Time_Step_Sec));
		
		JLabel lblReferenceGeneratorId = new JLabel("Reference machine Id");
		lblReferenceGeneratorId.setBounds(332, 44, 127, 14);
		DynamicSettingPanel.add(lblReferenceGeneratorId);
		
		JLabel lblMonitoringBus = new JLabel("Monitoring Bus");
		lblMonitoringBus.setBounds(711, 300, 96, 14);
		DynamicSettingPanel.add(lblMonitoringBus);
		
		JButton btnAddMonitoringBus = new JButton("Add>>");
		btnAddMonitoringBus.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				//add a monitoring bus
				int[] selectedRows = busTable.getSelectedRows();
				int [] selectedRowsInModel= new int[selectedRows.length];
				for(int i =0;i<selectedRows.length;i++){
					selectedRowsInModel[i]=busTable.convertRowIndexToModel(selectedRows[i]);
					String selectedBusId = (String) busTable.getModel().getValueAt(selectedRowsInModel[i], 0);
					monBusTextArea.append(selectedBusId+newline);
				}
				

			}
		});
		btnAddMonitoringBus.setBounds(552, 386, 76, 23);
		DynamicSettingPanel.add(btnAddMonitoringBus);
		
		
		busTable = new JTable();
		busTable.setBackground(Color.WHITE);
		//busTable.setBounds(45, 147, 347, 150);
		//DynamicSettingPanel.add(busTable);
		JScrollPane busTablePane = new JScrollPane(busTable);
		//DynamicSettingPanel.add(busTable);
		DynamicSettingPanel.add(busTablePane);
		busTablePane.setBounds(45, 325, 442, 150);
		busTablePane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		busTablePane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		busTable.setFillsViewportHeight(true); 
		
		
		JLabel lblMonitoringGenerators = new JLabel("Monitoring Generators");
		lblMonitoringGenerators.setBounds(697, 109, 127, 14);
		DynamicSettingPanel.add(lblMonitoringGenerators);
		
		//DynamicSettingPanel.add(new JScrollPane(genTable));
		JScrollPane genTablePane = new JScrollPane();
		genTablePane.setBounds(45, 144, 442, 145);
		//DynamicSettingPanel.add(genTable);
		DynamicSettingPanel.add(genTablePane);
		genTablePane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		genTablePane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		genTable = new JTable();
		genTablePane.setViewportView(genTable);
		genTable.setFillsViewportHeight(true); 
		
		
		JButton btnAddMonitoringGen = new JButton("Add>>");
		btnAddMonitoringGen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				//add a monitoring generator
				int[] selectedRows = genTable.getSelectedRows();
				int [] selectedRowsInModel= new int[selectedRows.length];
				for(int i =0;i<selectedRows.length;i++){
					selectedRowsInModel[i]=genTable.convertRowIndexToModel(selectedRows[i]);
					String selectedBusId = (String) genTable.getModel().getValueAt(selectedRowsInModel[i], 0); // idx =0 busID, idx=2,genId
					String selectedGenId = (String) genTable.getModel().getValueAt(selectedRowsInModel[i], 2);
					DStabGen dsGen= (DStabGen) dsNet.getDStabBus(selectedBusId).getContributeGen(selectedGenId);
				
					monGenTextArea.append(dsGen.getMach().getId()+newline);
				}
				
			}
		});
		btnAddMonitoringGen.setBounds(552, 224, 76, 23);
		DynamicSettingPanel.add(btnAddMonitoringGen);
		
		JLabel lblOutputPerN = new JLabel("Output per N steps");
		lblOutputPerN.setBounds(344, 11, 115, 14);
		DynamicSettingPanel.add(lblOutputPerN);
		
		outputStepsTextField = new JTextField();
		outputStepsTextField.setBounds(469, 8, 104, 20);
		DynamicSettingPanel.add(outputStepsTextField);
		outputStepsTextField.setText("1");
		
		JLabel lblBuses = new JLabel("Buses");
		lblBuses.setBounds(45, 300, 115, 14);
		DynamicSettingPanel.add(lblBuses);
		
		JLabel lblNewLabel_9 = new JLabel("Generators");
		lblNewLabel_9.setBounds(45, 109, 118, 14);
		DynamicSettingPanel.add(lblNewLabel_9);
		
		refMachTextField = new JTextField();
		refMachTextField.setBounds(469, 41, 104, 20);
		DynamicSettingPanel.add(refMachTextField);
		refMachTextField.setColumns(10);
		
		JButton btnSelectRefMach = new JButton("Select As Reference");
		btnSelectRefMach.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				int selectedRow = genTable.getSelectedRow();
				int selectedRowsInModel=0;
			
					selectedRowsInModel=genTable.convertRowIndexToModel(selectedRow);
					String selectedBusId = (String) genTable.getModel().getValueAt(selectedRowsInModel, 0); // idx =0 busID, idx=2,genId
					String selectedGenId = (String) genTable.getModel().getValueAt(selectedRowsInModel, 2);
					DStabGen dsGen= (DStabGen) dsNet.getDStabBus(selectedBusId).getContributeGen(selectedGenId);
				
					refMachTextField.setText(dsGen.getMach().getId());
				}
			
		});
		btnSelectRefMach.setBounds(517, 161, 151, 23);
		DynamicSettingPanel.add(btnSelectRefMach);
		
		monBusTextArea = new JTextArea();
		//monBusTextArea.setBounds(663, 130, 189, 145);
		JScrollPane monBusPane = new JScrollPane(monBusTextArea);
		monBusPane.setBounds(678, 330, 160, 145);
		DynamicSettingPanel.add(monBusPane);
		monBusPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		monBusPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		monGenTextArea = new JTextArea();
		//monGenTextArea.setBounds(663, 320, 189, 145);
		JScrollPane monGenPane = new JScrollPane(monGenTextArea);
		monGenPane.setBounds(678, 139, 160, 145);
		DynamicSettingPanel.add(monGenPane);
		monGenPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		monGenPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
	}

	private void initPowerflowSettingPanel(JTabbedPane DataTabbedPane) {
		JPanel PFSettingPanel = new JPanel();
		DataTabbedPane.addTab("Power flow", null, PFSettingPanel, null);
		PFSettingPanel.setLayout(null);
		
		JLabel lblNewLabel_1 = new JLabel("Solution method");
		lblNewLabel_1.setBounds(97, 36, 69, 22);
		PFSettingPanel.add(lblNewLabel_1);
		
		JLabel lblNewLabel_2 = new JLabel("Maximum iterations");
		lblNewLabel_2.setBounds(69, 69, 124, 14);
		PFSettingPanel.add(lblNewLabel_2);
		
		pfMethodComboBox = new JComboBox();
		pfMethodComboBox.setBounds(238, 37, 86, 20);
		PFSettingPanel.add(pfMethodComboBox);
		// populate the methods
		for(AclfMethod m:AclfMethod.values()){
			pfMethodComboBox.addItem(m);
		}
		pfMethodComboBox.setSelectedItem(AclfMethod.PQ); // use fast decoupled as the default method
		
		maxItrTextField = new JTextField();
		maxItrTextField.setBounds(238, 66, 86, 20);
		PFSettingPanel.add(maxItrTextField);
		maxItrTextField.setColumns(10);
		maxItrTextField.setText("20");
		
		JLabel lblNewLabel_3 = new JLabel("Tolerance");
		lblNewLabel_3.setBounds(97, 110, 59, 14);
		PFSettingPanel.add(lblNewLabel_3);
		
		tolTextField = new JTextField();
		tolTextField.setBounds(238, 107, 86, 20);
		PFSettingPanel.add(tolTextField);
		tolTextField.setColumns(10);
		tolTextField.setText("1.0E-5");
		
		JRadioButton rdbtnInitBusVoltage = new JRadioButton("Flat start");
		rdbtnInitBusVoltage.setBounds(239, 150, 109, 23);
		PFSettingPanel.add(rdbtnInitBusVoltage);
		
		JRadioButton rdbtnEnableNonDiverge = new JRadioButton("Enable non-divergent");
		rdbtnEnableNonDiverge.setBounds(238, 176, 146, 23);
		PFSettingPanel.add(rdbtnEnableNonDiverge);
		
		JRadioButton rdbtnAutoAdjustment = new JRadioButton("Enable auto-adjustment");
		rdbtnAutoAdjustment.setBounds(238, 202, 166, 23);
		PFSettingPanel.add(rdbtnAutoAdjustment);
		
		JLabel lblNewLabel_10 = new JLabel("Control and adjustment");
		lblNewLabel_10.setBounds(69, 159, 151, 40);
		PFSettingPanel.add(lblNewLabel_10);
	}

	private void initDataSettingPanel(JTabbedPane DataTabbedPane) {
		
		JPanel ImportExportPanel = new JPanel();
		DataTabbedPane.addTab("Import/Export Setting", null, ImportExportPanel, null);
		ImportExportPanel.setLayout(null);
		
		JButton btnSaveConfig = new JButton("Save configuration");
		btnSaveConfig.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				saveConfig2JSON();
			}
		});
		btnSaveConfig.setBounds(260, 161, 162, 30);
		ImportExportPanel.add(btnSaveConfig);
		
		JButton btnImportConfig = new JButton("Import configuration");
		btnImportConfig.setBounds(260, 102, 162, 30);
		ImportExportPanel.add(btnImportConfig);
		btnImportConfig.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				importJSONConfiguration();
			}
		});
		JPanel dataInputPanel = new JPanel();
		DataTabbedPane.addTab("Case data", null, dataInputPanel, null);
		dataInputPanel.setLayout(null);
		
		JLabel lblNewLabel = new JLabel("Data format");
		lblNewLabel.setBounds(38, 8, 94, 14);
		dataInputPanel.add(lblNewLabel);
		
		formatComboBox = new JComboBox();
		formatComboBox.setBounds(155, 5, 126, 20);
		dataInputPanel.add(formatComboBox);
		// populate the combo box
		for(IpssAdapter.FileFormat fmt: IpssAdapter.FileFormat.values()){
			formatComboBox.addItem(fmt);
		}

		formatComboBox.setSelectedItem(inputDataFmt);
		
		versionComboBox = new JComboBox();
		versionComboBox.setBounds(375, 5, 100, 20);
		dataInputPanel.add(versionComboBox);
		// populate the version
		for(PsseVersion v: PsseVersion.values()){
			versionComboBox.addItem(v);
		}
		versionComboBox.setSelectedItem(inputDataVersion);
		
		JLabel lblPowerFlow = new JLabel("Power flow");
		lblPowerFlow.setBounds(38, 41, 77, 14);
		dataInputPanel.add(lblPowerFlow);
		
		JLabel lblSequenceNetwork = new JLabel("Sequence (optional)");
		lblSequenceNetwork.setBounds(24, 130, 119, 14);
		dataInputPanel.add(lblSequenceNetwork);
		
		JLabel lblDynamic = new JLabel("Dynamic");
		lblDynamic.setBounds(39, 82, 57, 14);
		dataInputPanel.add(lblDynamic);
		
        // data files
		pfFileTextField = new JTextField();
		pfFileTextField.setBounds(155, 36, 380, 20);
		dataInputPanel.add(pfFileTextField);
		pfFileTextField.setColumns(10);
		
		dynFileTextField = new JTextField();
		dynFileTextField.setBounds(155, 79, 380, 20);
		dataInputPanel.add(dynFileTextField);
		dynFileTextField.setColumns(10);
		
		seqFileTextField = new JTextField();
		seqFileTextField.setBounds(155, 127, 381, 20);
		dataInputPanel.add(seqFileTextField);
		seqFileTextField.setColumns(10);
		
	
		
		// file chooser for selecting the data files to import
		fc =  new JFileChooser(".");
		 FileFilter psse = new FileNameExtensionFilter("PSS/E", new String[] { "raw", "dyr", "seq" });
	     FileFilter ieee = new FileNameExtensionFilter("IEEE",new String[] { "cf", "txt", "dat" });
	     FileFilter pwd = new  FileNameExtensionFilter("PowerWorld","aux");
	     FileFilter bpa = new FileNameExtensionFilter("BPA", new String[] {"txt", "dat","swi" });
	     FileFilter odm = new FileNameExtensionFilter("IEEE-ODM", new String[] {"odm", "xml" });
	        
        fc.addChoosableFileFilter(psse);
        fc.addChoosableFileFilter(ieee);
        fc.addChoosableFileFilter(pwd);
        fc.addChoosableFileFilter(bpa);
        fc.addChoosableFileFilter(odm);
		
		
		JButton btnPowerflowFile = new JButton("Browse...");
		btnPowerflowFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
		        if(formatComboBox.getSelectedItem().equals(IpssAdapter.FileFormat.PSSE))
		            fc.setFileFilter(psse);
		        else if(formatComboBox.getSelectedItem().equals(IpssAdapter.FileFormat.IEEECommonFormat)){
		        	 fc.setFileFilter(ieee);
		        }
		        else if(formatComboBox.getSelectedItem().equals(IpssAdapter.FileFormat.PWD)){
		       	 fc.setFileFilter(pwd);
		        }
		        else if(formatComboBox.getSelectedItem().equals(IpssAdapter.FileFormat.BPA)){
		          	 fc.setFileFilter(bpa);
		           }
		        else if(formatComboBox.getSelectedItem().equals(IpssAdapter.FileFormat.IEEE_ODM)){
		         	 fc.setFileFilter(odm);
		          }
			        	        
				int returnVal = fc.showOpenDialog(frmOpenhybridsim);
				 
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            pfFileTextField.setText(file.getAbsolutePath());
            //This is where a real application would open the file.
            msgLog.append("Selected power flow file: " + file.getName() + "." + newline);
        }
			}
		});
		
		
		btnPowerflowFile.setBounds(563, 35, 94, 23);
		dataInputPanel.add(btnPowerflowFile);
		
		JButton btnDynamicFile = new JButton("Browse...");
		btnDynamicFile.setBounds(563, 78, 94, 23);
		dataInputPanel.add(btnDynamicFile);
		btnDynamicFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				int returnVal = fc.showOpenDialog(frmOpenhybridsim);
				 
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            dynFileTextField.setText(file.getAbsolutePath());
            //This is where a real application would open the file.
            msgLog.append("Selected dynamic file: " + file.getName() + "." + newline);
        }
			}
		});
		
		
		
		JButton btnSequenceFile = new JButton("Browse...");
		btnSequenceFile.setBounds(563, 126, 94, 23);
		dataInputPanel.add(btnSequenceFile);
		btnSequenceFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				
				int returnVal = fc.showOpenDialog(frmOpenhybridsim);
				 
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            seqFileTextField.setText(file.getAbsolutePath());
            //This is where a real application would open the file.
            msgLog.append("Selected sequence data file: " + file.getName() + "." + newline);
        }
			}
		});
		
		
		JButton btnImportData = new JButton("Import data");
		btnImportData.setFont(new Font("Tahoma", Font.PLAIN, 14));
		btnImportData.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				//call DSL import data method
				importData();
				
				//initialize the bus and gen table in the transient stability setting panel
				initBusTable();
				//frmOpenhybridsim.validate();
				initGenTable();
				
				initBranchTable();
				//frmOpenhybridsim.validate();
			}
		});
		btnImportData.setBounds(280, 207, 140, 46);
		dataInputPanel.add(btnImportData);
		
		JLabel lblNewLabel_8 = new JLabel("Version");
		lblNewLabel_8.setBounds(302, 8, 58, 14);
		dataInputPanel.add(lblNewLabel_8);
		
		txtForPsseOnly = new JTextField();
		txtForPsseOnly.setBackground(Color.LIGHT_GRAY);
		txtForPsseOnly.setText("For PSS/E data only");
		txtForPsseOnly.setBounds(501, 5, 134, 20);
		dataInputPanel.add(txtForPsseOnly);
		txtForPsseOnly.setColumns(10);
	}
	
	private boolean importData(){
		  // import the file(s)
		if(this.dynFileTextField.getText()==null ||this.dynFileTextField.getText().equals("")){
			JOptionPane.showMessageDialog(null, "Dynamic file is required","File error!",JOptionPane.ERROR_MESSAGE);
		    return false;
		}
		 String[] dataFileAry  = null;
		 
		 if( this.seqFileTextField.getText()==null || this.seqFileTextField.getText().equals("")){
			 dataFileAry =new String[]{this.pfFileTextField.getText(),
					 this.dynFileTextField.getText()};
			 
		 }
		 else{
			 dataFileAry =new String[]{this.pfFileTextField.getText(),
					 this.seqFileTextField.getText(),
					 this.dynFileTextField.getText()};
		 }
		 
				FileImportDSL inDsl =  new FileImportDSL();
				inDsl.setFormat((FileFormat) this.formatComboBox.getSelectedItem())
					 .setPsseVersion((PsseVersion) this.versionComboBox.getSelectedItem())
				     .load(NetType.DStabNet,dataFileAry);
				
		try {
			dsNet = inDsl.getImportedObj();
		} catch (InterpssException e) {
			
			e.printStackTrace();
			return false;
		}
		//update the <isdataImported> flag
		this.isDataImported = true;
		
		msgLog.append("Data is successfully imported!"+newline);
		
		
		return true;
	}
	
	class BusTableModel extends AbstractTableModel {
		
		  Vector cache; // will hold String[] objects . . .

		  String[] columnNames={"BusId","Name","RatedKV","Status","Area","Zone","isLoad","isGen"};
		  
		  int colCount = 8; 
		  
		  DStabilityNetwork net = null;
		  
		  public BusTableModel() {
			    cache = new Vector();
		  }
		  
		 public void setDStabilityNetwork( DStabilityNetwork dstabNet){
			 this.net = dstabNet;
		 }
		 
		 public void populateTableModel(){
			 for(DStabBus bus: this.net.getBusList()){
				 String[] record = new String[this.colCount];
				 record[0] = bus.getId();
				 record[1] = bus.getName();
				 record[2] = Double.toString(bus.getBaseVoltage()/1000.0);
				 record[3] = Boolean.toString(bus.isActive());
				 record[4] = bus.getArea()!=null?bus.getArea().getId():"";
				 record[5] = bus.getZone()!=null?bus.getZone().getId():"";
				 record[6] = Boolean.toString(bus.isLoad());
				 record[7] = Boolean.toString(bus.isGen());
				 
				 cache.addElement(record);
				 		 
			 }  
		 }
	 
		@Override
		public int getColumnCount() {
			
			return this.colCount;
		}

		@Override
		public int getRowCount() {
			 return cache.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			
			return ((String[]) cache.elementAt(rowIndex))[columnIndex];
		}
		
		@Override
		public String getColumnName(int column) {
		    return columnNames[column];
		}
	}
	
    class GenTableModel	extends AbstractTableModel {
		
		  Vector cache; // will hold String[] objects . . .

		  String[] columnNames={"BusId","BusName","GenId","RatedKV","Status","Area","Zone"};
		  
		  int colCount = 7; 
		  
		  DStabilityNetwork net = null;
		  
		  public GenTableModel() {
			    cache = new Vector();
			   
		  }
		  
		 public void setDStabilityNetwork( DStabilityNetwork dstabNet){
			 this.net = dstabNet;
		 }
		 
		 public void populateTableModel(){
			 for(DStabBus bus: this.net.getBusList()){
				 if(bus.isActive() && bus.isGen()){
				 for(AclfGen gen:bus.getContributeGenList()){
					 if(gen.isActive()){
						 DStabGen dsGen = (DStabGen) gen;
						 String[] record = new String[this.colCount];
						 record[0] = bus.getId();
						 record[1] = bus.getName();
						 record[2] = dsGen.getId();
						 record[3] = Double.toString(bus.getBaseVoltage()/1000.0);
						 record[4] = Boolean.toString(dsGen.isActive());
						 record[5] = bus.getArea()!=null?bus.getArea().getId():"";
						 record[6] = bus.getZone()!=null?bus.getZone().getId():"";
					     cache.addElement(record);
					 }
				 }
				 }
				 		 
			 }  
		 }

		@Override
		public int getColumnCount() {
			
			return this.colCount;
		}

		@Override
		public int getRowCount() {
			 return cache.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			
			return ((String[]) cache.elementAt(rowIndex))[columnIndex];
		}
		
		@Override
		public String getColumnName(int column) {
		    return columnNames[column];
		}
    }

    class BranchTableModel extends DefaultTableModel {
        
    	  DStabilityNetwork net = null;
        public BranchTableModel() {
          super(new String[]{"BranchID", "From Bus", "To Bus","circuitId","Boundary At FromBus","Status"}, 0);
        }
        
   	     public void setDStabilityNetwork( DStabilityNetwork dstabNet){
		     this.net = dstabNet;
	     }
   	     
   	     
         public void initModel(){
        	 for(DStabBranch bra:net.getBranchList()){
        		 
        	     this.addRow(new Object[]{bra.getId(),bra.getFromBus().getName(),bra.getToBus().getName(),bra.getCircuitNumber(),false,bra.isActive()});
    
        	 }
         }
   	     
        @Override
        public Class<?> getColumnClass(int columnIndex) {
          Class clazz = String.class;
          switch (columnIndex) {
            
            case 4:
              clazz = Boolean.class;
              break;
            case 5:
              clazz = Boolean.class;
              break;
          }
          return clazz;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
          return column == 4;
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
          if (aValue instanceof Boolean && column == 4) {
           // System.out.println(aValue);
            Vector rowData = (Vector)getDataVector().get(row);
            rowData.set(4, (boolean)aValue);
            fireTableCellUpdated(row, column);
          }
        }

      }
	
    /*populate the bus table*/
	private void initBusTable(){
		
		BusTableModel busModel = new BusTableModel();
		busModel.setDStabilityNetwork(this.dsNet);
		busModel.populateTableModel();
		busTable.setModel(busModel);
		
	}
    private void initGenTable(){
    	GenTableModel genModel = new GenTableModel();
		genModel.setDStabilityNetwork(this.dsNet);
		genModel.populateTableModel();
		genTable.setModel(genModel);
	}
	private void initBranchTable(){
		BranchTableModel branchModel = new BranchTableModel();
		branchModel.setDStabilityNetwork(dsNet);
		branchModel.initModel();
		branchTable.setModel(branchModel);
		
	}
    
	private boolean runPowerFlow(){
		if(!isDataImported){
			msgLog.append("PF Simulation can NOT be performed, as data is not imported yet!"+newline);
		    return false;
		}
		if (this.dsNet == null){
			msgLog.append("PF Simulation can NOT be performed, as network model is null!"+newline);
			return false;
		}
		
		if(this.isSimuBasedOnSetting){
			
			msgLog.append("Starting running Power flow based on Settings !"+newline);
			// DSL load flow
			LfAlgoDSL lfDsl = new LfAlgoDSL(this.dsNet);
			try {
			   lfDsl.lfMethod((AclfMethod) this.pfMethodComboBox.getSelectedItem())
				     .maxIterations(Integer.parseInt(this.maxItrTextField.getText()))		 
				     .tolerance(Double.parseDouble(this.tolTextField.getText()), UnitType.PU)
				     .runLoadflow();
			} catch (NumberFormatException | InterpssException e) {
				msgLog.append("Power flow error:"+e.getMessage()+newline);
				e.printStackTrace();
			}
			
			msgLog.append("Power flow converged!"+newline);
			
			
		}else{// run AclfDSL with json config
			msgLog.append("Starting running Power flow with JSON configuraiton !"+newline);
		}
		
		return dsNet.isLfConverged();
		
		
	}
	private void runTransientStability(){
		if(!isDataImported){
			msgLog.append("Dynamic Simulation can NOT be performed, as data is not imported yet!");
		    return;
		}
		if (this.dsNet == null){
			msgLog.append("Dynamic Simulation can NOT be performed, as network model is null!");
			return;
		}
		
		if(this.isSimuBasedOnSetting){
			IpssDStab dstabDSL = new IpssDStab(this.dsNet);
			
			dstabDSL.setTotalSimuTimeSec(Double.valueOf(this.totalTimeTextField.getText()))
			        .setSimuTimeStep(Double.valueOf(this.timeStepTextField.getText()))
			        .setIntegrationMethod((DynamicSimuMethod) this.integrationMethodComboBox.getSelectedItem())
			        .setRefMachine(this.refMachTextField.getText());
			
			
			StateMonitor sm = new StateMonitor();
			//create the monitoring bus and generator array from the text area
			String[] monitoringBusAry = this.monBusTextArea.getText().split("\\n");
			String[] monitoringGenAry = this.monGenTextArea.getText().split("\\n");
			
			
			sm.addBusStdMonitor(monitoringBusAry);
			sm.addGeneratorStdMonitor(monitoringGenAry);
			
			// set the output handler
			dstabDSL.setDynSimuOutputHandler(sm)
			        .setSimuOutputPerNSteps(Integer.valueOf(this.outputStepsTextField.getText()));
			
//			dstabDSL.addBusFaultEvent(dstabBean.acscConfigBean.faultBusId,  
//					                                              dstabBean.acscConfigBean.category, 
//												                  dstabBean.eventStartTimeSec,
//												                  dstabBean.eventDurationSec, 
//												                  dstabBean.acscConfigBean.zLG.toComplex(), 
//												                  dstabBean.acscConfigBean.zLL.toComplex()); 
					                   
			
			if(dstabDSL.initialize()){
				if(dstabDSL.runDStab()){
					this.msgLog.append("Transient stability simulation completed!"+newline);
					dynOutput =dstabDSL.getOutputHandler();
				}
					
			}
		    
		    
		}
		else{
			DStabDslRunner tsDsl = new DStabDslRunner();
			
			try {
				dynOutput = tsDsl.run(tsDsl.loadConfigBean(jsonTextField.getText()));
			} catch (InterpssException | IOException e) {
				
				e.printStackTrace();
			}
		}
		
	}
	private boolean runHybridSimulation(){
		
		if(!isDataImported){
			msgLog.append("Dynamic Simulation can NOT be performed, as data is not imported yet!"+newline);
		    return false;
		}
		if (this.dsNet == null){
			msgLog.append("Dynamic Simulation can NOT be performed, as network model is null!"+newline);
			return false;
		}
		
		
		
		if(this.isSimuBasedOnSetting){
			//generate the  json configuration first;
			HybridSimuConfigRunner hsConfigRunner = new HybridSimuConfigRunner(this.dsNet);
			HybridSimuConfigBean hsConfigBean = new HybridSimuConfigBean();
			
			// transient stability setting
		
			hsConfigBean.totalSimuTimeSec = Double.valueOf(this.totalTimeTextField.getText());
			hsConfigBean.simuTimeStepSec =  Double.valueOf(this.timeStepTextField.getText());
			hsConfigBean.dynMethod = (DynamicSimuMethod) this.integrationMethodComboBox.getSelectedItem();
			hsConfigBean.referenceGeneratorId = this.refMachTextField.getText();
			
			//monitoring setting
			hsConfigBean.monitoringBusAry = this.monBusTextArea.getText().split("\\n");
			hsConfigBean.monitoringGenAry = this.monGenTextArea.getText().split("\\n");
			
			// detailed system boundary setting
			if(this.boundaryBranchTextArea.getText().trim().length()>0){
					
				Object[][] boundaryTieLineData = getBoundaryTieLineData();
				hsConfigBean.tieLinebranchIdAry = (String[]) boundaryTieLineData[0];
				hsConfigBean.boundaryAtFromBusSide = (Boolean[]) boundaryTieLineData[1];
			}
			else{
				msgLog.append("Boundary data is missing, please select the boundary interface branch first!");
				return false;
			}
			
			
			// hybrid simulation setting
			hsConfigBean.application_type = (HybridSimEquivalentType) this.hybridSimTypeCmboBox.getSelectedItem();
			hsConfigBean.protocol_type = (InteractionProtocol) this.protocolComboBox.getSelectedItem();
			hsConfigBean.socket_port = 	Integer.valueOf(socketPortTextField.getText());	
			hsConfigBean.socket_timeout_sec = Double.valueOf(this.socketTimeOutTextField.getText());
			
			//run hybrid simulation
			//IDStabSimuOutputHandler  output = null;
			try {
				dynOutput = hsConfigRunner.runHybridSimu(hsConfigBean);
			} catch (Exception e) {
				e.printStackTrace();
				msgLog.append("Error during hybrid simulation, caused by -> " + e.toString()+newline);
				return false;
			}
			
		}
		else{
			HybridSimuDslRunner hsDslRunner = new HybridSimuDslRunner();
		    
		    try {
		    	dynOutput = hsDslRunner.run(hsDslRunner.loadConfigBean( jsonTextField.getText()));
			} catch (InterpssException e) {
				e.printStackTrace();
				msgLog.append("Error during hybrid simulation, caused by -> "+e.toString()+newline);
				return false;
			}
			
			
		}
		
		//msgLog.append("Hybrid simulation completed"+newline);
		
		return true;
	}
	/**
	 * 
	 * @return  String[][] , String[0][]  = tieLinebranchIdAry, String[1][]  = boundaryBusAtFromSideAry;
	 */
	private Object[][] getBoundaryTieLineData(){
		
		
		String[] tieLineAndSideData = boundaryBranchTextArea.getText().split("\\n");
		
		String[] boundaryTieLineData = new String[tieLineAndSideData.length];
		Boolean[]  boundaryBusSideData = new Boolean[tieLineAndSideData.length];
		
		for(int i =0;i<tieLineAndSideData.length;i++){
			if(tieLineAndSideData[i].trim().length()>0){
			   String[] temp=tieLineAndSideData[i].split(",");
			   boundaryTieLineData[i] = temp[0];
			   boundaryBusSideData[i] = Boolean.valueOf(temp[1]);
			}
		}
		
		Object[][] boundaryData= new Object[2][tieLineAndSideData.length];
		boundaryData[0] = boundaryTieLineData;
		boundaryData[1] = boundaryBusSideData;
		
		return boundaryData;
		
	}
	
	private void importJSONConfiguration() {
		//import the JSON configuration file
		
		
		JFileChooser jsonFc =  new JFileChooser(".");
		FileFilter json = new FileNameExtensionFilter("JSON Config", "json");
		 jsonFc.addChoosableFileFilter(json);
		 jsonFc.setAcceptAllFileFilterUsed(false);
		 int returnVal = jsonFc.showOpenDialog(frmOpenhybridsim);
		 
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = jsonFc.getSelectedFile();
                try {
					HybridSimuConfigBean hsJson = BaseJSONBean.toBean(file.getAbsolutePath(),HybridSimuConfigBean.class);
				    // format and version
					formatComboBox.setSelectedItem(hsJson.acscConfigBean.runAclfConfig.format);
					versionComboBox.setSelectedItem(hsJson.acscConfigBean.runAclfConfig.version);
					
					//case file(s)
					pfFileTextField.setText(hsJson.acscConfigBean.runAclfConfig.aclfCaseFileName);
					dynFileTextField.setText(hsJson.dynamicFileName);
					if(!hsJson.acscConfigBean.seqFileName.equals("")){
						seqFileTextField.setText(hsJson.acscConfigBean.seqFileName);
					}
					
					// power flow setting
					pfMethodComboBox.setSelectedItem(hsJson.acscConfigBean.runAclfConfig.lfMethod);
					maxItrTextField.setText(Integer.toString(hsJson.acscConfigBean.runAclfConfig.maxIteration));
					tolTextField.setText(Double.toString(hsJson.acscConfigBean.runAclfConfig.tolerance));
					
					// transient stability
					this.integrationMethodComboBox.setSelectedItem(hsJson.dynMethod);
					this.totalTimeTextField.setText(Double.toString(hsJson.totalSimuTimeSec));
					this.timeStepTextField.setText(Double.toString(hsJson.simuTimeStepSec));
					this.refMachTextField.setText(hsJson.referenceGeneratorId);
					
					String monGenText = "";
					for(int i=0;i<hsJson.monitoringGenAry.length;i++)
						monGenText = monGenText + hsJson.monitoringGenAry[i]+newline;
					this.monGenTextArea.setText(monGenText);
					
					String monBusText = "";
					for(int i=0;i<hsJson.monitoringBusAry.length;i++)
						monBusText = monBusText + hsJson.monitoringBusAry[i]+newline;
					this.monBusTextArea.setText(monBusText );
					
					// hybrid simulation setting
					 this.hybridSimTypeCmboBox.setSelectedItem(hsJson.application_type);
					 this.protocolComboBox.setSelectedItem(hsJson.protocol_type);
					 this.socketPortTextField.setText(Integer.toString(hsJson.socket_port));
					 this.socketTimeOutTextField.setText(Double.toString(hsJson.socket_timeout_sec));
					 
					 String boundaryText ="";
					 for(int i =0;i<hsJson.tieLinebranchIdAry.length;i++){
						 boundaryText += hsJson.tieLinebranchIdAry[i]+","+hsJson.boundaryAtFromBusSide[i]+newline;
					 }
					 
					 this.boundaryBranchTextArea.setText(boundaryText);
					
                } catch (IOException e1) {
					e1.printStackTrace();
				}
                
                //This is where a real application would open the file.
                msgLog.append("Import json configuration file: " + file.getName() + "." + newline);
            }
	}
	
	private void saveConfig2JSON(){
		
		JFileChooser jsonFc =  new JFileChooser(".");
		FileFilter json = new FileNameExtensionFilter("JSON Config", "json");
		 jsonFc.addChoosableFileFilter(json);
		 jsonFc.setAcceptAllFileFilterUsed(false);
		 int returnVal = jsonFc.showSaveDialog(frmOpenhybridsim);
		 
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File jsonFile= jsonFc.getSelectedFile();
                
					HybridSimuConfigBean hsJson = new HybridSimuConfigBean(); 
				    // format and version
					hsJson.acscConfigBean.runAclfConfig.format =(FileFormat) formatComboBox.getSelectedItem();
					hsJson.acscConfigBean.runAclfConfig.version = (PsseVersion) versionComboBox.getSelectedItem();
					
					//case file(s)
					hsJson.acscConfigBean.runAclfConfig.aclfCaseFileName =pfFileTextField.getText();
					hsJson.dynamicFileName = dynFileTextField.getText();
					if(seqFileTextField.getText().trim().length()>0){
						hsJson.acscConfigBean.seqFileName = seqFileTextField.getText();
					}
					
					// power flow setting
					hsJson.acscConfigBean.runAclfConfig.lfMethod =(AclfMethod) pfMethodComboBox.getSelectedItem();
					hsJson.acscConfigBean.runAclfConfig.maxIteration = Integer.valueOf(maxItrTextField.getText());
					hsJson.acscConfigBean.runAclfConfig.tolerance = Double.valueOf(tolTextField.getText());
					
					// transient stability
					hsJson.dynMethod = (DynamicSimuMethod) this.integrationMethodComboBox.getSelectedItem();
					hsJson.totalSimuTimeSec = Double.valueOf(this.totalTimeTextField.getText());
					hsJson.simuTimeStepSec = Double.valueOf(this.timeStepTextField.getText());
					hsJson.referenceGeneratorId = this.refMachTextField.getText().trim();
					
					
					hsJson.monitoringGenAry = this.monGenTextArea.getText().split("\\n");
					hsJson.monitoringBusAry = this.monBusTextArea.getText().split("\\n");
					
					// hybrid simulation setting
					hsJson.application_type = (HybridSimEquivalentType) this.hybridSimTypeCmboBox.getSelectedItem();
					hsJson.protocol_type =  (InteractionProtocol) this.protocolComboBox.getSelectedItem();
					hsJson.socket_port =   Integer.valueOf(this.socketPortTextField.getText());
					hsJson.socket_timeout_sec = Double.valueOf(this.socketTimeOutTextField.getText());
					 
					if(this.boundaryBranchTextArea.getText().length()>0){
						 String[] boundaryDataAry = this.boundaryBranchTextArea.getText().split("\\n");
						 String[] tieLineAry = new String[ boundaryDataAry.length];
						 Boolean[] sideAry = new Boolean[ boundaryDataAry.length];
						 for(int i =0;i<boundaryDataAry.length;i++){
							 String[] temp =boundaryDataAry[i].split(",");
							 if(temp.length==2){
							 tieLineAry[i] = temp[0];
							 sideAry [i] = Boolean.valueOf(temp[1]);
							 }
							 else
								 throw new Error("Boundary tie-line defintion format error is detected, "
								 		+ "each line should only defines one tie-line and the boundary side, separated by a comma");
	
						 }
						 hsJson.tieLinebranchIdAry = tieLineAry;
						 hsJson.boundaryAtFromBusSide = sideAry;
					}
					
					
					try {
				        
				          BufferedWriter output = new BufferedWriter(new FileWriter(jsonFile));
				          output.write(hsJson.toString());
				          output.close();
				        } catch ( IOException e ) {
				           e.printStackTrace();
				           msgLog.append("Error during saving configuration to a JSON File : "+e.getMessage()+ newline);
				           return;
				        }
              
                
                
                //This is where a real application would open the file.
                msgLog.append("Configuration is saved to JSON file: " + jsonFile.getAbsolutePath() + "." + newline);
            }
		
	}
	
	private void saveDynGenResults() throws IOException{
		if(dynOutput instanceof StateMonitor){
			
			StateMonitor dynStateMonitor = (StateMonitor) this.dynOutput;
			
			
		// open a file save dialogue 
			JFileChooser xlsFc =  new JFileChooser(".");
			FileFilter xls = new FileNameExtensionFilter("Excel file", "xls");
			 xlsFc .addChoosableFileFilter(xls);
			 xlsFc .setAcceptAllFileFilterUsed(false);
			 int returnVal = xlsFc.showSaveDialog(frmOpenhybridsim);
			 
			 
	   if (returnVal == JFileChooser.APPROVE_OPTION) {
            File xlsFile= xlsFc.getSelectedFile();
            String filePath = xlsFile.getPath();
            String fileName = xlsFile.getName();
            if(fileName.indexOf(".")<0){
            	//TODO there is not extension, add ".xls" to it
            	
            	
            }
            String extensions = filePath.substring(fileName.indexOf(".") + 1, fileName.length());
            
			
		  //Excel file, and add a workbook for each output sheet
			
			Workbook workBook = new HSSFWorkbook();
			
			if(this.chckbxPGenMW.isShowing()){
		        Sheet sheet = workBook.createSheet("Pe (pu)");
		        
		       
		        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getMachPeTable()).split("\\n");
	
	            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
		            String str[] = csvLineAry[rowNum].split(",");
		            
		            Row currentRow=sheet.createRow(rowNum);
		            for(int i=0;i<str.length;i++){
		                currentRow.createCell(i).setCellValue(str[i]);
		            }
	            }
		   }
			
			if(this.chckbxQgenMVAr.isSelected()){
		        Sheet sheet = workBook.createSheet("Qgen (pu)");
		        
		       
		        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getMachQgenTable()).split("\\n");
	
	            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
		            String str[] = csvLineAry[rowNum].split(",");
		            
		            Row currentRow=sheet.createRow(rowNum);
		            for(int i=0;i<str.length;i++){
		                currentRow.createCell(i).setCellValue(str[i]);
		            }
	            }
		   }
			
			if(this.chckbxGenAngle.isSelected()){
		        Sheet sheet = workBook.createSheet("Angle (deg)");
		        
		       
		        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getMachAngleTable()).split("\\n");
	
	            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
		            String str[] = csvLineAry[rowNum].split(",");
		            
		            Row currentRow=sheet.createRow(rowNum);
		            for(int i=0;i<str.length;i++){
		                currentRow.createCell(i).setCellValue(str[i]);
		            }
	            }
		   }
			
			
			if(this.chckbxGenSpeed.isSelected()){
		        Sheet sheet = workBook.createSheet("Speed (pu)");
		        
		       
		        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getMachSpeedTable()).split("\\n");
	
	            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
		            String str[] = csvLineAry[rowNum].split(",");
		            
		            Row currentRow=sheet.createRow(rowNum);
		            for(int i=0;i<str.length;i++){
		                currentRow.createCell(i).setCellValue(str[i]);
		            }
	            }
		   }
			
			if(this.chckbxGenEfd.isSelected()){
		        Sheet sheet = workBook.createSheet("Efd (pu)");
		        
		       
		        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getMachEfdTable()).split("\\n");
	
	            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
		            String str[] = csvLineAry[rowNum].split(",");
		            
		            Row currentRow=sheet.createRow(rowNum);
		            for(int i=0;i<str.length;i++){
		                currentRow.createCell(i).setCellValue(str[i]);
		            }
	            }
		   }
			
			if(this.chckbxGenPmech.isSelected()){
		        Sheet sheet = workBook.createSheet("Pmech (pu)");
		        
		       
		        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getMachPmTable()).split("\\n");
	
	            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
		            String str[] = csvLineAry[rowNum].split(",");
		            
		            Row currentRow=sheet.createRow(rowNum);
		            for(int i=0;i<str.length;i++){
		                currentRow.createCell(i).setCellValue(str[i]);
		            }
	            }
		   }
            
		    
	        FileOutputStream fileOutputStream =  new FileOutputStream(xlsFile.getAbsolutePath());
	        workBook.write(fileOutputStream);
	        fileOutputStream.close();
	        msgLog.append("Generator variables are saved to EXCEL file: "+xlsFile.getAbsolutePath()+newline);
		}
	  }
	}
	
	private void saveDynBusResults() throws IOException{
		
     if(dynOutput instanceof StateMonitor){
			
		  StateMonitor dynStateMonitor = (StateMonitor) this.dynOutput;
			
	    if(this.chckbxBusFreq.isSelected() ||this.chckbxBusVolt.isSelected()){
			// open a file save dialogue 
			JFileChooser xlsFc =  new JFileChooser(".");
			FileFilter xls = new FileNameExtensionFilter("Excel file", "xls");
			xlsFc .addChoosableFileFilter(xls);
			xlsFc .setAcceptAllFileFilterUsed(false);
			int returnVal = xlsFc.showSaveDialog(frmOpenhybridsim);
				 
				 
		    if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File xlsFile= xlsFc.getSelectedFile();
				
			  //Excel file, and add a workbook for each output sheet
				
				Workbook workBook = new HSSFWorkbook();
				
				if(this.chckbxBusVolt.isSelected()){
			        Sheet sheet = workBook.createSheet("Bus volt (pu)");
			        
			       
			        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getBusVoltTable()).split("\\n");
		
		            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
			            String str[] = csvLineAry[rowNum].split(",");
			            
			            Row currentRow=sheet.createRow(rowNum);
			            for(int i=0;i<str.length;i++){
			                currentRow.createCell(i).setCellValue(str[i]);
			            }
		            }
			   }
				
				if(this.chckbxBusFreq.isSelected()){
			        Sheet sheet = workBook.createSheet("Bus freq(pu)");
			        
			       
			        String[] csvLineAry = dynStateMonitor.toCSVString(dynStateMonitor.getBusFreqTable()).split("\\n");
		
		            for(int rowNum = 0;rowNum<csvLineAry.length;rowNum++){
			            String str[] = csvLineAry[rowNum].split(",");
			            
			            Row currentRow=sheet.createRow(rowNum);
			            for(int i=0;i<str.length;i++){
			                currentRow.createCell(i).setCellValue(str[i]);
			            }
		            }
			   }
				
		        FileOutputStream fileOutputStream =  new FileOutputStream(xlsFile.getAbsolutePath());
		        workBook.write(fileOutputStream);
		        fileOutputStream.close();
		        
		        msgLog.append("Bus result is saved to EXCEL file: "+xlsFile.getAbsolutePath()+newline);
			}
		  } //if-at least one oupt should be selected
	  		else{
	  			msgLog.append("At least one BUS output should be selected!");
	  	  }
      } // if stateMonitor instance of
		
	}
	
	private void savePowerflowResults(){
		
		if(dsNet.isLfConverged()){
			// open a file save dialogue 
			JFileChooser txtFc =  new JFileChooser(".");
			FileFilter txt = new FileNameExtensionFilter("Text file", new String[]{"txt","dat"});
			txtFc .addChoosableFileFilter(txt);
			txtFc .setAcceptAllFileFilterUsed(false);
			int returnVal = txtFc.showSaveDialog(frmOpenhybridsim);
			 
			 
	        if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File pfResultFile= txtFc.getSelectedFile();
	            StringBuffer text = null;
	            if(this.rdbtnSummary.isSelected()){
	            	text = AclfOutFunc.loadFlowSummary(dsNet);
	            }
	            else if(this.rdbtnDetailedPFResult.isSelected())
	            	text = AclfOut_BusStyle.lfResultsBusStyle(dsNet, BusIdStyle.BusId_Name);
	            
	            if(text!=null){
		            FileUtil.writeText2File(pfResultFile.getAbsolutePath(), text);
		            
		            msgLog.append("Power flow result is saved to file: "+pfResultFile.getAbsolutePath()+newline);
	            }
		    }
		}
		else
		    msgLog.append("Error in saving power flow result: Power flow is not converged, either it has not been run or the system is diverged, please check!"+newline);
	}
	
	private void buildExternalSystemEquivalent() throws Exception{
		if(!this.isDataImported){
			msgLog.append("Data is not loaded yet. Cannot build external network equivalent."+newline);
		}
		else{
			
			  if(!dsNet.isLfConverged()){
			        boolean converged = runPowerFlow();
					
					if(!converged) {
						msgLog.append("Load flow is not coverged! Cannot build external network equivalent"+newline);
						throw new Error("Load flow is not coverged! Cannot build external network equivalent");
					}
		        }
	        
	        /*
			 * step-2 create sub-network helper for the study case and  define boundary buses
			 * 
			 */
			HybidSimSubNetworkHelper subNetHelper = new HybidSimSubNetworkHelper(dsNet);
			
			Object[][] boundaryTieLineData = getBoundaryTieLineData();
		
			
			String[] interfaceBranches = (String[]) boundaryTieLineData[0];
			Boolean[] boundaryBusSides =  (Boolean[]) boundaryTieLineData[1];
			
			
			
			for(int i=0;i<interfaceBranches.length;i++){
			      subNetHelper.addSubNetInterfaceBranch(interfaceBranches[i], boundaryBusSides[i]);
			}
			
			NetworkEquivalentHelper equivHelper = new NetworkEquivalentHelper(subNetHelper);
			
			
			//fileChooser
			// open a file save dialogue 
			JFileChooser csvFc =  new JFileChooser(".");
			FileFilter csv = new FileNameExtensionFilter("CSV file", "csv");
			csvFc .addChoosableFileFilter(csv);
			csvFc .setAcceptAllFileFilterUsed(false);
			int returnVal = csvFc.showSaveDialog(frmOpenhybridsim);
				 
				 
		    if (returnVal == JFileChooser.APPROVE_OPTION) {
	            File csvFile= csvFc.getSelectedFile();
				
				if(this.hybridSimTypeCmboBox.getSelectedItem() == HybridSimEquivalentType.POSITIVE_SEQUENCE){
					msgLog.append("Positive sequence (3-phase balanced) external network equivalent is choosen based on the setting."+newline);
					equivHelper.calcNSavePositiveEquivParam(csvFile.getAbsolutePath());
					
				}
				else{
					msgLog.append("Three-phase generic external network equivalent is choosen based on the setting."+newline);
					equivHelper.calcNSaveBoundaryBus3PhaseEquivParam(csvFile.getAbsolutePath());
					
				}
				
				msgLog.append("External network equivalent is saved to CSV file: "+csvFile.getAbsolutePath()+newline);
		    }
		}
	}
	
	
	class HybridSimuWorker extends SwingWorker<Boolean, Object> {
	       @Override
	       public Boolean doInBackground() {
	           return runHybridSimulation();
	       }

	       @Override
	       protected void done() {
	           try {
	        	   msgLog.append("Hybrid simulation is completed"+newline);

	           } catch (Exception ignore) {
	           }
	       }
	   }

	
}
