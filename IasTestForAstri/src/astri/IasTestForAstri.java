/*
 * ALMA - Atacama Large Millimiter Array (c) European Southern Observatory,
 * 2002 Copyright by ESO (in the framework of the ALMA collaboration), All
 * rights reserved
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2.1 of the License, or (at your
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package astri.iasTest;

import java.util.logging.Level;
import java.util.logging.Logger;

import alma.ACS.ROdouble;
import alma.ACSErr.CompletionHolder;

import alma.JavaContainerError.wrappers.AcsJContainerServicesEx;
import alma.acs.component.client.ComponentClient;
import alma.demo.HelloDemo;
import astri.TCS.WeatherStation;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.*;


/*****************/
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.Sample;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.JsonFilePublisher;
//import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/*****************/



class IasTestForAstri extends ComponentClient{
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	public WeatherStation ws;
	public Plugin plugin;
	public static final String resourcePath="/astri/";
	public ScheduledFuture<?> loopFuture;
	/**
	 * @param logger
	 * @param managerLoc
	 * @param clientName
	 * @throws Exception
	 */
	public IasTestForAstri(Logger logger, String managerLoc, String clientName) throws Exception {
		super(logger, managerLoc, clientName);
	}

	public static void main(String[] args) {
		
		String managerLoc = System.getProperty("ACS.manager");
		String clientName = "IasTestForAstri";
		try{
			IasTestForAstri newConnection = new IasTestForAstri(null, managerLoc, clientName);
			//newConnection.openConnect(); //Avvia Connessione ACS
			try {
				newConnection.pluginConnection();
				newConnection.doSomeStuff();			
			}
			catch (Exception e) {
				try {
					Logger logger = newConnection.getContainerServices().getLogger();
					logger.log(Level.SEVERE, "Client application failure", e);
				} catch (Exception e2) {
					e.printStackTrace(System.err);
				}
			}
			finally {
				if (newConnection != null) {
					try {
						newConnection.tearDown();
					}
					catch (Exception e3) {
						// bad luck
						e3.printStackTrace();
					}
				}
			}
		}catch(Exception e){
			System.out.println(e);
		}
		
	}
	
	public void doSomeStuff() throws AcsJContainerServicesEx{
		Thread t =new Thread(new Runnable() {
			@Override
			public void run() {
				int i=0;
				try{
					//Try to take the component from the container;
					ws = astri.TCS.WeatherStationHelper.narrow(getContainerServices().getComponent("WeatherStation"));
					while(i<10){
						//System.out.println("Thread started");
						// Convert the value of Windspd into ROdouble
						ROdouble windSpeedProp = ws.WS_WINDSPD();
						CompletionHolder c = new CompletionHolder();
						//Extract the value
						double val = windSpeedProp.get_sync(c);
						m_logger.info(" Wind speed is: "+val);
						//Update the monitor point with ID and Value
						updateMonitorPointValue("WS_WINDSP",val);
						Thread.sleep(1000);
						i++;
					}

				}catch(Exception e){
					System.out.println(e);
				}

			}
		});
		
		try{
			t.start();
			t.join();
		}catch(InterruptedException e){
			System.out.println(e);
		}

	}
	
	public void updateMonitorPointValue(String mPointID, Object value) {
		// TODO Auto-generated method stub
		try {
			plugin.updateMonitorPointValue(mPointID, value);
		} catch (Exception pe) {
			System.out.println("Error" + pe);
		}
	}
	
	public void pluginConnection(){
		System.out.println("Started...");
		PluginConfig config=null;
		try {
			PluginConfigFileReader jsonFileReader = new PluginConfigFileReader(resourcePath+"WeatherStationConf.json");
			Future<PluginConfig> futurePluginConfig = jsonFileReader.getPluginConfig();
			config = futurePluginConfig.get(1, TimeUnit.MINUTES);
		} catch (PluginConfigException pce) {
			System.out.println("Excetion reading configuratiopn"+pce);
			System.exit(-1);
		} catch (InterruptedException ie) {
			System.out.println("Interrupted"+ie);
			System.exit(-1);
		} catch (TimeoutException te) {
			System.out.println("Timeout reading configuration"+te);
			System.exit(-1);
		} catch (ExecutionException ee) {
			System.out.println("Execution error"+ee);
			System.exit(-1);
		}
		System.out.println("Configuration successfully red");

		// Create the file in the IAS temporary folder
		String tmpFolderName = System.getProperty("ias.tmp.folder",".");
		File folder = new File(tmpFolderName);
		BufferedWriter jsonWriter = null;
		try { 
			File jsonFile =File.createTempFile("MonitorPointValues", ".json", folder);
			jsonWriter = new BufferedWriter(new FileWriter(jsonFile));
			System.out.println("Moitor points to be sent to the core of the IAS will be saved in {}"+jsonFile.getAbsolutePath());
		} catch (IOException ioe) {
			System.out.println("Cannot create the JSON file"+ioe);
			System.exit(-1);
		}

		JsonFilePublisher jsonPublisher = new JsonFilePublisher(
				config.getId(), 
				config.getMonitoredSystemId(),
				config.getSinkServer(), 
				config.getSinkPort(), 
				Plugin.getScheduledExecutorService(), 
				jsonWriter);

		plugin = new Plugin(config,jsonPublisher);
		try {
			plugin.start();
		} catch (PublisherException pe) {
			System.out.println("The plugin failed to start"+pe);
			System.exit(-3);
		}
		//System.out.println("Connection with ACS and start comunication");
		//plugin.connection();
		System.out.println("Done.");
	
	}

}

