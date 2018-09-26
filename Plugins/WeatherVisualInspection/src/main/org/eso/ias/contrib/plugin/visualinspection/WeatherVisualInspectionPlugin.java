package  org.eso.ias.contrib.plugin.visualinspection;

import org.eso.ias.utils.ISO8601Helper;
import org.eso.ias.plugin.config.Property;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eso.ias.heartbeat.publisher.HbKafkaProducer;
import org.eso.ias.heartbeat.serializer.HbJsonSerializer;
import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.KafkaPublisher;
import org.eso.ias.types.OperationalMode;


/**
 * IAS Plugin that runs in a never-ending loop and publishes data obtained
 * periodically from a JSON file to a Kafka Queue.
 * This data corresponds to the timestamp of the last visual inspection of the
 * weather stations.
 */
public class WeatherVisualInspectionPlugin extends Plugin {

	/**
	 * The key used to read the path of the json file from the
	 * {@link PluginConfig} properties.
	 */
	private static final String JSON_FILEPATH_KEY = "input-file";

	/**
	 * The value to send when the monitor point is malfuncioning
	 */
	private static final String VALUE_WHEN_FAILED = "None";

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherVisualInspectionPlugin.class);

	/**
	 * the loop to keep the plugin running.
	 */
	private ScheduledFuture<?> loopFuture;

	/**
	 * The list of registries of type {@link WeatherStationInspectionRegistry}
	 */
	private List<WeatherStationInspectionRegistry> registries = null;

	/**
	 * The path to the config file for the plugin.
	 */
	private static String configPath = "config.json";

	/**
	 * The Json File to read
	 */
	private String jsonFilePath;

	/**
	 * Refresh time in seconds
	 */
	private final int refreshTime;

	/**
	 * The name of the property to pass the kafka servers to connect to
	 */
	private static final String KAFKA_SERVERS_PROP_NAME = "org.eso.ias.plugins.kafka.server";

	/**
	 * The latch to be notified about termination
	 */
	private final CountDownLatch done = new CountDownLatch(1);

	/**
	 The IDs of the monitor points to send to the BSDB
	 */
	private final Set<String> idOfMPoints;

	/**
	 * The mapper to convert received strings into {@link WeatherStationInspectionRegistry}
	 */
	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Constructor
	 *
	 * @param config
	 *            The configuration of the plugin.
	 */
	private WeatherVisualInspectionPlugin(PluginConfig config) {
		// Connection with the Kafka healthyness queue to report its own health state

		super(
			config,
			new KafkaPublisher(config.getId(), config.getMonitoredSystemId(), config.getSinkServer(), config.getSinkPort(), Plugin.getScheduledExecutorService()),
			new HbKafkaProducer(config.getId()+"HBSender", config.getSinkServer() + ":" + config.getSinkPort(), new HbJsonSerializer())
		);

		idOfMPoints=config.getMapOfValues().keySet();
		logger.info("Will produce {} monitor points",idOfMPoints.size());
		idOfMPoints.forEach(id -> WeatherVisualInspectionPlugin.logger.info("Recognized MP {}",id));

		this.refreshTime = config.getAutoSendTimeInterval();
        logger.info("Refresh rate {} secs",refreshTime);

		// Read the location of the json input file
		this.jsonFilePath = null;
		try  {
			this.jsonFilePath = config.getProperty(JSON_FILEPATH_KEY).get().getValue();
		} catch (Exception e) {
			logger.error("Error reading the path to the input-file from the configuration file", e);
			System.exit(-5);
		}
	}

	/**
	 * Sets a given monitor point with {@link OperationalMode} OPERATIONAL
	 * and updates the value with a given value
	 *
	 * @param mPointID
	 * @throws PluginException
	 */
	private void updateMonitorPointOperational(String mPointID, String value) throws PluginException {
		updateMonitorPointValue(mPointID, value);
		setOperationalMode(mPointID, OperationalMode.OPERATIONAL);
	}

	/**
	 * Sets a given monitor point with {@link OperationalMode} MALFUNCTIONING
	 * and updates the value with None
	 *
	 * @param mPointID
	 * @throws PluginException
	 */
	private void updateMonitorPointMalfunctioning(String mPointID) throws PluginException {
		updateMonitorPointValue(mPointID, VALUE_WHEN_FAILED);
		setOperationalMode(mPointID, OperationalMode.DEGRADED);
	}

	/**
	 * Sets all the monitor points with {@link OperationalMode} MALFUNCTIONING
	 * and updates the value with None
	 *
	 * @param mPointID
	 * @throws PluginException
	 */
	private void updateAllMonitorPointsMalfunctioning() throws PluginException {
		Iterator<String> iterator = idOfMPoints.iterator();
		while(iterator.hasNext()) {
			String mPointID = iterator.next();
			updateMonitorPointMalfunctioning(mPointID);
		}
	}

	/**
	 * Add the shutdown hook.
	 */
	private CountDownLatch initialize() {
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, "Release shutdown hook"));
		return done;
	}

	/**
	 * Terminate the thread that publishes the data
	 */
	private void cleanUp() {
		if (loopFuture != null) {
			loopFuture.cancel(false);
		}
		done.countDown();
	}

	/**
	 * Print the usage string
	 *
	 * @param options The options expected in the command line
	 */
	private static void printUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "java - jar WeatherVisualInspection.jar", options );
	}

	/**
	 * Starts the loop that reads the data from the JSON file
	 */
	private void startLoop() {
		loopFuture=getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
			public void run() {
				logger.debug("Updating monitor point values");
				try {
					File jsonFile = new File(jsonFilePath);

					if (jsonFile.exists()){
						logger.info("Json input file read successfully: {}",jsonFilePath);
						try {
							registries = Arrays.asList(mapper.readValue(jsonFile, WeatherStationInspectionRegistry[].class));
							unsetPluginOperationalMode();

							// Iterate through registries
							for (int i = 0; i < registries.size(); i++) {
								WeatherStationInspectionRegistry registry = registries.get(i);
								logger.debug("Sending visual inspection registry: " + registry);
								String monitorPointId = "WS-" + registry.getStation() + "-LastInspection";
								String timestamp = registry.getTimestamp();

								// Validate registry timestamp, send as OPERATIONAL if correct and DEGRADED if not
								try {
									ISO8601Helper.timestampToMillis(registry.getTimestamp());
									updateMonitorPointOperational(monitorPointId, timestamp);
								} catch (Exception e) {
									logger.error("Exception parsing the timestamp [{}]", timestamp ,e);
									updateMonitorPointMalfunctioning(monitorPointId);
								}
							}
						// Set the whole plugin DEGRADED if the JSON file cannot be parsed
						} catch (IOException e) {
							logger.error("Error parsing a registries from the json file", e);
							updateAllMonitorPointsMalfunctioning();
						}

					}
					// Set the whole plugin DEGRADED if the JSON file does not exist
					else {
						updateAllMonitorPointsMalfunctioning();
					}
				} catch (PluginException pe) {
					logger.error("Error sending monitor point to the core of the IAS");
				}
			}
		}, 2, 30, TimeUnit.SECONDS);
		try {
			loopFuture.get();
		} catch (ExecutionException ee) {
			logger.error("Execution exception getting values from the weather station",ee);
		} catch (Exception ce) {
			logger.info("Loop to get minitor point values from the weather station terminated");
		}
	}

	/**
	 * Runs the plugin. The sinkserver and port can be overrided using the java
	 * property. Args are optional and they override the refresh time.
	 *
	 * @param args
	 *          [0] optional -refresh
	 *					[1] optional REFRESH-TIME-MILLIS
	 */
	public static void main(String[] args) {

		logger.info("**** Starting VisualInspectionPlugin");

		// Use apache CLI for command line parsing
		Options options = new Options();
		options.addOption("f", "file-path", true, "Path of the file to read");
		options.addOption("c", "config-file", true, "Plugin configuration file");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd=null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException pe) {
			logger.error("Error parsing the comamnd line: "+pe.getMessage());
			printUsage(options);
			System.exit(-1);
		}

		String jsonFilePath = null;
		if (cmd.hasOption("f")) {
			jsonFilePath = cmd.getOptionValue("f");
			logger.info("Filepath to read obtained from command line: {}",jsonFilePath);
		}

		if (cmd.hasOption("c")) {
			configPath = cmd.getOptionValue("c");
		}
		logger.info("Configuration file name {}",configPath);

		PluginConfig config = null;
		try {
			File configFile = new File(configPath);
			PluginConfigFileReader jsonFileReader = new PluginConfigFileReader(configFile);
			Future<PluginConfig> futurePluginConfig = jsonFileReader.getPluginConfig();
			config = futurePluginConfig.get(1, TimeUnit.MINUTES);
		} catch (FileNotFoundException e) {
			logger.error("Exception opening config file", e);
			e.printStackTrace();
			System.exit(-1);
		} catch (PluginConfigException pce) {
			logger.error("Exception reading configuration", pce);
			pce.printStackTrace();
			System.exit(-1);
		} catch (InterruptedException ie) {
			logger.error("Interrupted", ie);
			ie.printStackTrace();
			System.exit(-1);
		} catch (TimeoutException te) {
			logger.error("Timeout reading configuration", te);
			te.printStackTrace();
			System.exit(-1);
		} catch (ExecutionException ee) {
			ee.printStackTrace();
			logger.error("Execution error", ee);
			System.exit(-1);
		}
		logger.info("Configuration successfully read");

		String sinkServer = config.getSinkServer();
		int sinkPort = config.getSinkPort();

		/*Kafka server and port can be set using the Java properties*/
		String kServers= System.getProperty(KAFKA_SERVERS_PROP_NAME);
		if (kServers!=null && !kServers.isEmpty()) {
			try{
				sinkServer = kServers.split(":")[0];
				sinkPort = Integer.parseInt(kServers.split(":")[1]);
				config.setSinkServer(sinkServer);
				config.setSinkPort(sinkPort);
			} catch (NumberFormatException e) {
				printUsage(options);
				System.exit(-2);
			}
		}
		logger.info("Kafka sink server: " + sinkServer + ":" + sinkPort);

		/*JSON file to read can be set using the command line*/
		if (jsonFilePath != null && !jsonFilePath.isEmpty()) {
			Property[] properties= config.getProperties();
			for (int i = 0; i < properties.length; i++) {
				if (properties[i].getKey().equals(JSON_FILEPATH_KEY)) {
					properties[i].setValue(jsonFilePath);
					break;
				}
			}
			config.setProperties(properties);
			logger.info("Reading json file parsed by command line {}", jsonFilePath);
		}

		// Instantiate the WeatherVisualInspectionPlugin
		WeatherVisualInspectionPlugin plugin = new WeatherVisualInspectionPlugin(config);

		// Start the Plugin
		try {
			plugin.start();
		} catch (PublisherException pe) {
			logger.error("The plugin failed to start", pe);
			System.exit(-3);
		}

		// Connect to the Weather Stations.
		CountDownLatch latch = plugin.initialize();

		plugin.startLoop();
		try {
			WeatherVisualInspectionPlugin.logger.info("Waiting");
			latch.await();
		} catch (InterruptedException ie) {
			WeatherVisualInspectionPlugin.logger.error("Plugin interrupted",ie);
		}

		logger.info("Done.");
	}
}
