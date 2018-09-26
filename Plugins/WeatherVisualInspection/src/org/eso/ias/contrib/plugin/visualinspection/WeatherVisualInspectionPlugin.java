package  org.eso.ias.contrib.plugin.visualinspection;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.eso.ias.heartbeat.publisher.HbKafkaProducer;
import org.eso.ias.heartbeat.serializer.HbJsonSerializer;
import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.config.Property;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.KafkaPublisher;
import org.eso.ias.types.OperationalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.*;

/**
 * IAS Plugin that runs in a never-ending loop and publishes data obtained
 * periodically from a set of weather stations to a Kafka Queue
 */
public class WeatherVisualInspectionPlugin extends Plugin {

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
	private static final String configPath = "config.json";

	/**
	 * The Json File to read
	 */
	private File jsonFile;

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
	 * The mapper to convert received strings into {@link MessageDao}
	 */
	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Constructor
	 *
	 * @param config
	 *            The configuration of the plugin.
	 * @param sender
	 *            The sender.
	 */
	private WeatherVisualInspectionPlugin(PluginConfig config, MonitorPointSender sender) {
		// Connection with the Kafka healthyness queue to report its own health state
		super(config, sender, new HbKafkaProducer(
			config.getId()+"HBSender", config.getSinkServer() + ":" + config.getSinkPort(),
			new HbJsonSerializer())
		);

		idOfMPoints=config.getMapOfValues().keySet();
		logger.info("Will produce {} monitor points",idOfMPoints.size());
		idOfMPoints.forEach(id -> WeatherVisualInspectionPlugin.logger.info("Recognized MP {}",id));

		this.refreshTime = config.getAutoSendTimeInterval();
        logger.info("Refresh rate {} secs",refreshTime);

		// Read the location of the json input file
		String jsonFilePath = null;
		try  {
			jsonFilePath = config.getProperty("input-file").get().getValue();
		} catch (Exception e) {
			logger.error("Error reading the path to the input-file from the configuration file", e);
			System.exit(-5);
		}
		jsonFile = new File(jsonFilePath);
		if (!jsonFile.exists()) {
			logger.error("The json file does not exist: "+jsonFilePath);
			System.exit(-6);
		}
		logger.info("Json input file read successfully: {}",jsonFilePath);
	}

	/**
	 *  Updates monitor point only for recognized IDs
	 *
	 * @see #updateMonitorPointValue(String, Object)
	 */
	@Override
	public OperationalMode setOperationalMode(String mPointId, OperationalMode opMode) throws PluginException {
			if (idOfMPoints.contains(mPointId)) {
					return super.setOperationalMode(mPointId, opMode);
			} else {
					return null;
			}
	}

	/**
	 * The weather station tries to publish all the monitor points
	 * but not all the weather sstations have the device installed
	 * This method only publishes the monito points
	 * defined in the config file and silently discard the others.
	 *
	 * @param mPointID
	 * @param value
	 * @throws PluginException
	 */
	@Override
	public void updateMonitorPointValue(String mPointID, Object value) throws PluginException {
			if (idOfMPoints.contains(mPointID)) {
			 super.updateMonitorPointValue(mPointID, value);
			}
	}

	/**
	 * Add the shutdown hook.
	 */
	private CountDownLatch initialize() {
		// Adds the shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, "Release shutdown hook"));
		return done;
	}

	/**
	 * Terminate the thread that publishes the data and disconnects from the weather
	 * station pool.
	 */
	private void cleanUp() {
		if (loopFuture != null) {
			loopFuture.cancel(false);
		}
		done.countDown();
	}

	private static void printUsage() {
		System.out.println("Usage: java - jar alma-weather-plugin.jar");
	}

	private void startLoop() {
		loopFuture=getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
			public void run() {
				logger.info("Updating monitor point values");
				try {
					if (jsonFile.exists()){
						try {
							registries = Arrays.asList(mapper.readValue(jsonFile, WeatherStationInspectionRegistry[].class));
						} catch (IOException e) {
							logger.error("Error parsing a registries from the json file", e);
							System.exit(-6);
						}
						for (int i = 0; i < registries.size(); i++) {
							WeatherStationInspectionRegistry registry = registries.get(i);
							logger.debug("Sending visual inspection registry: " + registry);
							String monitorPointId = "WS-" + registry.getStation() + "-LastInspection";
							updateMonitorPointValue(monitorPointId, registry.getTimestamp());
						}

					}
				} catch (PluginException pe) {
					logger.error("Error sending Temperature monitor point to the core of the IAS");
				}
			}
		}, 1, 2, TimeUnit.SECONDS);
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

		logger.info("Started...");
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
				printUsage();
				System.exit(-2);
			}
		}
		logger.info("Kafka sink server: " + sinkServer + ":" + sinkPort);

		// Instantiate a KafkaPublisher
		KafkaPublisher kafkaPublisher = new KafkaPublisher(config.getId(), config.getMonitoredSystemId(),
				config.getSinkServer(), config.getSinkPort(), Plugin.getScheduledExecutorService());

		// Instantiate the WeatherVisualInspectionPlugin
		WeatherVisualInspectionPlugin plugin = new WeatherVisualInspectionPlugin(config, kafkaPublisher);

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
