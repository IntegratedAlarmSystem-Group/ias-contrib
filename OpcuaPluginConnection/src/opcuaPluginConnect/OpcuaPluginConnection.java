package opcuaPluginConnect;

import java.io.BufferedReader;

/* 
 * Create by Filippo Fagioli
 * Perugia 05-12-2017
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.channels.Channel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eso.ias.converter.ConverterStream;
import org.eso.ias.converter.config.ConfigurationException;
/**** Import Plugin Library ***********/ 
import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.Sample;
import org.eso.ias.plugin.ValueToSend;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.publisher.*;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.JsonFilePublisher;
import org.eso.ias.plugin.publisher.impl.KafkaPublisher;
import org.eso.ias.prototype.input.java.IASValue;
import org.hamcrest.core.IsEqual;
import org.hibernate.sql.Insert;
import org.omg.CORBA.exception_type;


/**** Import OPC ua Client Library ***********/
import org.opcfoundation.ua.application.Client;
import org.opcfoundation.ua.application.SessionChannel;
import org.opcfoundation.ua.builtintypes.DataValue;
import org.opcfoundation.ua.builtintypes.LocalizedText;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.QualifiedName;
import org.opcfoundation.ua.builtintypes.UnsignedInteger;
import org.opcfoundation.ua.builtintypes.Variant;
import org.opcfoundation.ua.common.ServiceFaultException;
import org.opcfoundation.ua.common.ServiceResultException;
import org.opcfoundation.ua.core.ApplicationDescription;
import org.opcfoundation.ua.core.ApplicationType;
import org.opcfoundation.ua.core.Attributes;
import org.opcfoundation.ua.core.BrowseDescription;
import org.opcfoundation.ua.core.BrowseDirection;
import org.opcfoundation.ua.core.BrowsePathTarget;
import org.opcfoundation.ua.core.BrowseResponse;
import org.opcfoundation.ua.core.BrowseResult;
import org.opcfoundation.ua.core.BrowseResultMask;
import org.opcfoundation.ua.core.Identifiers;
import org.opcfoundation.ua.core.MonitoringMode;
import org.opcfoundation.ua.core.NodeClass;
import org.opcfoundation.ua.core.ReadResponse;
import org.opcfoundation.ua.core.ReadValueId;
import org.opcfoundation.ua.core.ReferenceDescription;
import org.opcfoundation.ua.core.RelativePathElement;
import org.opcfoundation.ua.core.TimestampsToReturn;
import org.opcfoundation.ua.transport.Endpoint;
import org.opcfoundation.ua.transport.ServiceChannel;
import org.opcfoundation.ua.transport.security.KeyPair;
import org.opcfoundation.ua.transport.security.SecurityMode;
import org.opcfoundation.ua.utils.AttributesUtil;
import org.opcfoundation.ua.utils.bytebuffer.InputStreamReadable;

import com.prosysopc.ua.ServiceException;
import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.client.AddressSpace;
import com.prosysopc.ua.client.AddressSpaceException;
import com.prosysopc.ua.client.MonitoredDataItem;
import com.prosysopc.ua.client.ServerList;
import com.prosysopc.ua.client.ServerListException;
import com.prosysopc.ua.client.Subscription;
import com.prosysopc.ua.client.UaClient;
import com.prosysopc.ua.nodes.UaDataType;

import org.eso.ias.cdb.CdbReader;
import org.eso.ias.cdb.CdbWriter;
import org.eso.ias.cdb.IasCdbException;
import org.eso.ias.cdb.json.CdbFiles;
import org.eso.ias.cdb.json.CdbJsonFiles;
import org.eso.ias.cdb.json.JsonReader;
import org.eso.ias.cdb.json.JsonWriter;
import org.eso.ias.cdb.pojos.AsceDao;
import org.eso.ias.cdb.pojos.DasuDao;
import org.eso.ias.cdb.pojos.IasDao;
import org.eso.ias.cdb.pojos.IasioDao;
import org.eso.ias.cdb.pojos.SupervisorDao;
/*********Import converter***************/
import org.eso.ias.converter.*;

public class OpcuaPluginConnection {
	private ScheduledExecutorService schedExecutorSvcPlug;
	private ScheduledExecutorService schedExecutorSvcClient;
	public static final String resourcePath="/uaClientConnect/";
	public static String clientName = "uaClientConnect";
	public Plugin plugin;
	public Converter c;
	String[] id={"IASValue"};
	public JsonReader jsonReader;
	public ConverterKafkaStream converterKafkaStream;
	private CdbFiles cdbFiles;

	/**
	 * JSON files reader
	 */
	private CdbReader cdbReader;

	/**
	 * The parent folder is the actual folder
	 */
	public static final Path cdbParentPath =  FileSystems.getDefault().getPath(".");



	public ArrayList<String> nome= new ArrayList<String>();


	public OpcuaPluginConnection(String clientName) {
		this.clientName = clientName;
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException, ServerListException, PluginException{
		OpcuaPluginConnection clientNew= new OpcuaPluginConnection(clientName);

		try {
			//clientNew.testCon();
			clientNew.pluginConnection();
			clientNew.createClient();
		} catch (/*URISyntaxException | ServiceException | StatusException | ServiceResultException
				| AddressSpaceException e*/Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void testCon() throws IOException{
		//Create a KafkaStream for streaming data from plugin topic to IAStopic
		converterKafkaStream=new ConverterKafkaStream("id", "localhost:9092","PluginsKTopic","BsdbCoreKTopic");
		
		//Read the configuration component from the CDB folder  
		Path path = FileSystems.getDefault().getPath("src/testCdb");
		cdbFiles = new CdbJsonFiles(path);
		cdbReader = new JsonReader(cdbFiles);
		
		//Create a converter with id(String value), cdbReader(defined before), converterKafkaStream(stream for the conversion)
		c=new Converter(id[0], cdbReader, converterKafkaStream);
		try {
			//Initialized the loop for send value to kafkatopic
			c.setUp();
		} catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//End of the loop in ClientConnect class
	}

	public static int insert(){
		InputStreamReader stream= new InputStreamReader(System.in);
		BufferedReader myInput = new BufferedReader (stream);
		System.out.println("-----------------------------");
		System.out.println("Select the port number for the connection");
		System.out.println("0 -> port number: 52520 for WeatherStation");
		System.out.println("1 -> port number: 52521 for AMC");
		System.out.println("2 -> port number: 52522 for PMC");
		System.out.println("3 -> port number: 52523 for TCU");
		System.out.println("4 -> port number: 52524 for THCU");
		System.out.println("-----------------------------");
		String str= new String();
		try {
			str = myInput.readLine();
			switch (str) {
			case "0":
				return 52520;
			case "1":
				return 52521;
			case "2":
				return 52522;
			case "3":
				return 52523;
			case "4":
				return 52523;
			default:
				System.out.println("Error, insert a value inside the range");
			}
		}catch (IOException e) {
			System.out.println ("Error: " + e);
			System.exit(-1); 
		}
		return 0;
	}



	public void createClient() throws InterruptedException, ExecutionException, URISyntaxException, ServiceException, StatusException, ServiceResultException, AddressSpaceException, ServerListException, PluginException{
		int port=insert();
		String uri= "opc.tcp://localhost:"+port+"/OPCUA/hwSimulator";
		UaClient client = new UaClient(uri);

		client.setSecurityMode(SecurityMode.NONE);
		ApplicationDescription appDescription = new ApplicationDescription();
		appDescription.setApplicationName(new LocalizedText("hwSimulator", Locale.ENGLISH));

		appDescription.setApplicationUri("urn:localhost:UA:hwSimulator");
		appDescription.setProductUri("urn:prosysopc.com:UA:hwSimulator");
		appDescription.setApplicationType(ApplicationType.Client);
		client.connect();



		AddressSpace as = client.getAddressSpace();
		ArrayList<String> nome=recurseAddressSpace(client,as,"",Identifiers.RootFolder);


		schedExecutorSvcClient =
				Executors.newScheduledThreadPool(1);
		ScheduledFuture<?> scheduledFuture =
				schedExecutorSvcClient.schedule(new Runnable() {
					public void run() {
						int j=0;

						while(j<10){
							for(int i=0; i<nome.size();i++){

								System.out.println(nome.get(i));
								String[] nsSplit= nome.get(i).split(":");
								NodeId node=new NodeId(Integer.parseInt(nsSplit[0]),nsSplit[1]);

								if (nsSplit[1].equals("ws_windspd")){
									try {
										plugin.updateMonitorPointValue(nsSplit[1].toUpperCase(), Double.parseDouble(client.readAttribute(node, Attributes.Value).getValue().toString()));

									} catch (NumberFormatException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (PluginException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (ServiceException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									} catch (StatusException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								try {
									System.out.println(client.readAttribute(node, Attributes.Value).getValue().toString());
								} catch (ServiceException | StatusException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							j++;		
						}
					}

				},
						10,
						TimeUnit.SECONDS);



		System.out.println(scheduledFuture.get());/*
		Thread t =new Thread(new Runnable() {
			@Override
			public void run() {
				int j=0;
				try{
					while(j<10){

						for(int i=0; i<nome.size();i++){

							System.out.println(nome.get(i));
							String[] nsSplit= nome.get(i).split(":");
							NodeId node=new NodeId(Integer.parseInt(nsSplit[0]),nsSplit[1]);

							if (nsSplit[1].equals("ws_windspd")){
								try {
									plugin.updateMonitorPointValue(nsSplit[1].toUpperCase(), Double.parseDouble(client.readAttribute(node, Attributes.Value).getValue().toString()));
									//Thread.sleep(5000);
								} catch (NumberFormatException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (PluginException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (ServiceException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (StatusException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							try {
								System.out.println(client.readAttribute(node, Attributes.Value).getValue().toString());
							} catch (ServiceException | StatusException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						j++;

					}
				}catch(Exception e){

				}
			}
		});
		try{
			t.start();
			t.join();
		}catch(InterruptedException e){
			System.out.println(e);
		}*/
		plugin.shutdown();
		c.tearDown();
		schedExecutorSvcClient.shutdown();
		client.disconnect();
	}


	private ArrayList<String> recurseAddressSpace(UaClient client, AddressSpace as, String nodePath, NodeId thisNode)
	{

		try{
			//int entryCount = 0;
			List<ReferenceDescription> ref = as.browse(thisNode);
			//entryCount=ref.size();
			int i=0;
			for(ReferenceDescription item : ref){
				if(item.getNodeClass().toString().equals("Object")){
					try{
						if(nodePath.equals(""))	{
							recurseAddressSpace(client, as, cleanName(item.getBrowseName().toString()), as.getNode(item.getNodeId()).getNodeId());
						}
						else{
							recurseAddressSpace(client, as, nodePath+"."+cleanName(item.getBrowseName().toString()), as.getNode(item.getNodeId()).getNodeId());
						}
					}
					catch(Exception y){

					}
				}if(item.getNodeClass().toString().equals("Variable")){
					try	{
						if(nodePath.equals("Objects.MYTEST.MYTEST")){
							//if (item.getBrowseName().toString().toLowerCase().contains("tchu".toLowerCase())){
							if(i>34){
								//System.out.println("Monitor Point: "+item.getBrowseName().toString());
								nome.add(item.getBrowseName().toString());
							}
							i++;
							//}
						}/*else
						{
							System.out.println("Leaf: "+nodePath+"."+cleanName(item.getBrowseName().toString()));
						}*/
					}catch(Exception e){
						/*if(nodePath.equals(""))
						{
							System.out.println("Leaf: "+item.getBrowseName().toString()+" without an accessible Value attribute");
						}
						else{
							System.out.println("Leaf: "+nodePath+"."+cleanName(item.getBrowseName().toString())+" without an accessible Value attribute");
						}*/
					}
				}
			}
		}catch(Exception x)	{
			System.out.println("Error: "+x.toString());
		}
		return nome;
	}


	private String cleanName(String dirtyName){
		return dirtyName.substring(dirtyName.indexOf(":")+1);
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

				 /*JsonFilePublisher jsonPublisher = new JsonFilePublisher(
				config.getId(), 
				config.getMonitoredSystemId(),
				config.getSinkServer(), 
				config.getSinkPort(), 
				Plugin.getScheduledExecutorService(), 
				jsonWriter);
*/
		int poolSize = 2;
		int port = 9092;
		ScheduledExecutorService schedExecutorSvc = Executors.newScheduledThreadPool(poolSize, Plugin.getThreadFactory());
		KafkaPublisher kPub = new KafkaPublisher(
				config.getId(), 
				config.getMonitoredSystemId(), 
				config.getSinkServer(), 
				port, 
				schedExecutorSvc);
		plugin = new Plugin(config,kPub);
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

