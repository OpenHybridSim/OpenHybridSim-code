package edu.asu.hybridSim.test;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import edu.asu.hybridSimu.SocketServerHelper;

public class TestSocketServerHelper {
	
	public static void main(String[] args) throws IOException{
		
		Thread serverThread = new Thread(new Runnable() {
			  @Override
			  
			  public void run() {
		
					SocketServerHelper ssHelper = new SocketServerHelper();
					ssHelper.createServerSokect(8089,200000);
					double[] a = {
							1.0,2.803957,0.116130,-2.526824,0.000006,-2.89191};
					try {
						while(ssHelper.getClientSocket()!=null){
							if(ssHelper.receiveDoubleArrays()!=null){
								double[] recvData=ssHelper.getReceiveDataAry();
								System.out.println(Arrays.toString(recvData));
							    //System.out.println(recvData[0]);

								ssHelper.sendDoubleArrays(a);
							}
							//System.out.println(ssHelper.receiveDataString());
							//One tricky thing, we need to add one additional comma at the end of the data
						    for(int i =0; i<a.length;i++)
						    	a[i]+=1;
							
						}
					} catch (IOException e) {
						
						e.printStackTrace();
					}
					ssHelper.closeSocket();
			  }
		});
		
		
		
		Thread clientThread = new Thread(new Runnable() {
			  @Override
			  public void run() {
				 
				            Socket clientSocket;
							try {
								clientSocket = new Socket("127.0.0.1", 8099);
								
								
								PrintWriter out =
									    new PrintWriter(clientSocket.getOutputStream(), true);
								
								out.println("1.000,2.002,3.0");
								
								BufferedReader in = new BufferedReader(new InputStreamReader(
					                    clientSocket.getInputStream()));
								
								System.out.println("Client recv :"+in.readLine());
								clientSocket.close();
								
							} catch (IOException e1) {
	
								e1.printStackTrace();
							}
				     
						
			  }
			   
			});
		
		
		serverThread.start();
		//clientThread.start();
		
	}
	
	
	

}
