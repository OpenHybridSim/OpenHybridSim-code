package edu.asu.hybridSimu;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamCorruptedException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;

import org.interpss.numeric.util.Number2String;

import com.interpss.common.util.IpssLogger;

/**
 * TCP/IP based socket communication framework is used for hybrid 
 * simulation, with the socket client set up on the PSCAD side, while
 * the socket server on the InterPSS side, as  Java has a better support
 * of Socket techniques.
 * 
 * SocketServerHelper creates and manages a socket server, which connects to the socket
 * client set up in PSCAD. The server receive the data sent from socket client of PSCAD and 
 * send the latest network equivalent data , now in string format, back to the PSCAD.
 * 
 * 
 * @author Qiuhua Huang
 * School of Electrical, Computer and Energy Engineering
 * Ira A. Fulton Schools of Engineering
 * Arizona State University
 * Email: qhuang24@asu.edu
 *
 */
public class SocketServerHelper {
	private ServerSocket serverSocket = null;
	private Socket clientSocket = null;
	private DataOutputStream out = null;
	private InputStream in = null;
	private double[] dataAry = null;
	private boolean isMsgSizeHeaderEnabled = false;
	private long counter = 0;
	
	public SocketServerHelper(){
		
	}
	
	/**
	 * Currently two format of message is supported by the message parser implemented in the SocketServerHelper, i.e.,
	 * 1) purely comma separate values(csv) string
	 * 2) message size header, which indicates the size (byte array size) of the csv String, followed by the csv string.
	 * This design is based on the TLV (Type-length-value) format of binary protocol design, with the type information omitted. 
	 * For more info, see http://en.wikipedia.org/wiki/Type-length-value
	 * 
	 * @param flag
	 */
	public void enableMsgSizeHeader(boolean flag){
		this.isMsgSizeHeaderEnabled = flag;
	}
    /**
     * create a server Socket with specified port number and timeout setting 
     * @param port  port of the server
     * @param timeOutms server waiting for msg specified timeout, in milliseconds
     * @return
     */
	public ServerSocket createServerSokect(int port,int timeOutms){
		 try {
	            serverSocket = new ServerSocket(port);
	            serverSocket.setSoTimeout(timeOutms);
	        } catch (IOException e) {
	           e.printStackTrace();
	            
	        }
		 return serverSocket;
	}
	
    /**
     * create a server Socket with specified port number and timeout setting 
     * @param port  port of the server
     * @param timeOutms server waiting for msg specified timeout, in milliseconds
     * @return
     */
	public ServerSocket createServerSokectWithIP(int port,int timeOutms){
		 try {
	            serverSocket = new ServerSocket(port);
	            serverSocket.setSoTimeout(timeOutms);
	        } catch (IOException e) {
	           e.printStackTrace();
	            
	        }
		 return serverSocket;
	}
	
	/*
	 * Listens for a connection to be made to this socket and accepts it. 
	 * The method blocks until a connection is made.
	 */
	public Socket getClientSocket() throws IOException{
		return clientSocket = serverSocket.accept();
	}
	//create a socket on server side
	
	/**
	 * send the double array to the client (PSCAD) side
	 * @param dblAry
	 * @return
	 */
	public boolean sendDoubleArrays(double[] dblAry){
		
		   //need to format the double first
		   DecimalFormat df = new DecimalFormat("####.#####");
		   String str="";
			 
			for(int i =0;i<dblAry.length-1;i++){
				 str+=df.format(dblAry[i])+",";
			}
			//add "\0" to the end of the string to indicate that is the end of  the string
			str+=df.format(dblAry[dblAry.length-1])+"\0";
			
			return sendString(str);
			 
	}
	public boolean sendString(String s){
		
		//System.out.println("Send String:"+s);
		
		if(clientSocket!=null){
			 try {
			    out =new DataOutputStream(clientSocket.getOutputStream());
			   
			    byte[] message = s.getBytes();
			    
			   
			    //And the msg length, optionally, depending on the setting
			    if (isMsgSizeHeaderEnabled)
			         out.writeInt(message.length);
			   
				out.write(message);
				out.flush();
			   
			   
			    } catch (IOException e) {
				  e.printStackTrace();
				  return false;
			   }
		}
		
		return true;
		
	}
	
	/**
	 * receive the input data in String form;
	 * @return
	 */
	public String receiveDataString(){
		String recvString="";
		counter +=1 ;
		char[] recvCharAry= new char[2000];
		if(clientSocket!=null){
			   try {
				   in =clientSocket.getInputStream();
                   
				   if(this.isMsgSizeHeaderEnabled){ // assuming the client also provide msg size header
					   
					   DataInputStream dis =  new DataInputStream(in);
					   int length = dis.readInt();
					   
				        if(length>0){ 
				        byte[] buffer = new byte[length];
				        
				        try
				        {
				            dis.readFully(buffer, 0, length);
				        }

				        catch (StreamCorruptedException e)
				        {
				           e.printStackTrace();
				        }
				        catch (EOFException e)
				        {
				        	e.printStackTrace();
				        }
				        catch (IOException e)
				        {
				        	e.printStackTrace();
				        }
				        finally{

				        recvString  = new String(buffer);
				        }
				      }
				      else
				    	  IpssLogger.getLogger().severe("The receive byte array is empty");
					   
				   } // no message size header
				   else{
					   BufferedReader br = new BufferedReader(new InputStreamReader(in));
					   
					   int num = br.read(recvCharAry);
					   recvString = new String(recvCharAry, 0, num);
				   }
				  
				   
			      } catch (IOException e) {
				     e.printStackTrace();
			     }  
			}
			else{
				IpssLogger.getLogger().severe("clientSocket is null, data not received from client side yet!");
				return null;
			}
		return recvString;
		
		
	}
	
	/**
	 * receive the input stream from the client side and parse it to a double array
	 * @return
	 */
	public double[] receiveDoubleArrays(){
		
		String recvString = receiveDataString().trim();
		 System.out.println("\n Recv String:"+recvString);
		 
			   if(recvString.contains(",")){
				  
				   
			       String[] dataAryStr=recvString.split(",");
			       
			       dataAry = new double[dataAryStr.length];
			       
				   for(int i=0;i<dataAryStr.length;i++){
					   //TODO Only a temp solution
					   if(dataAryStr[i].contains("\""))
						   dataAryStr[i].replace("\"", "");
					   dataAry[i] =Double.valueOf(dataAryStr[i]);
				   }
			      
			   }
			   else{
				   if(counter ==1)  
					   IpssLogger.getLogger().info("First msg from Client side:"+recvString);
				   else 
				      IpssLogger.getLogger().severe("Recv String from Client socket,but does not use comma as delimiter:"+recvString);
			   }
			   
		return dataAry;
	}
	public double[] getReceiveDataAry(){
		return dataAry;
	}
	
	
	public boolean closeSocket(){
		try {
			out.close();
			in.close();
			clientSocket.close();
			serverSocket.close();
		} catch (IOException e) {
			
			e.printStackTrace();
			return false;
		}
		return true;
		
	}
	
	
	

}
