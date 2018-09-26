package  org.eso.ias.contrib.plugin.weather;

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
import java.util.stream.Collectors;

/**
 * IAS Plugin that runs in a never-ending loop and publishes data obtained
 * periodically from a set of weather stations to a Kafka Queue
 */
public class WeatherPlugin extends Plugin {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

	/**
	* The IDs of the weather station, like MeteoCentral or MeteoTB2
	 * and their Identifer for SOAP
	*/
	private static Map<String, Integer> weatherStationsIds = new HashMap<>();

	/**
	 * the loop to keep the plugin running.
	 */
	private ScheduledFuture<?> loopFuture;

	/**
	 * The weather stations pool that updates their data periodically and provides
	 * them to the plugin
	 */
	private WeatherStationsPool weatherStationsPool;

	/**
	 * The path to the config file for the plugin.
	 */
	private static final String configPath = "config.json";

	/**
	 * Refresh time in seconds
	 */
	private final int refreshTime;

	/**
	 * The name of the property to pass the kafka servers to connect to
	 */
	private static final String KAFKA_SERVERS_PROP_NAME = "org.eso.ias.plugins.kafka.server";

	/** The IDs of the monitor points to send to the BSDB */
	private final Set<String> idOfMPoints;

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
	 * Constructor
	 *
	 * @param config
	 *            The configuration of the plugin.
	 * @param sender
	 *            The sender.
	 */
	private WeatherPlugin(PluginConfig config, MonitorPointSender sender) {
		// Connection with the Kafka healthyness queue to report its own health state
		super(config, sender, new HbKafkaProducer(
			"AlmaWeatherPlugin"+"HBSender", config.getSinkServer() + ":" + config.getSinkPort(),
			new HbJsonSerializer())
		);

		idOfMPoints=config.getMapOfValues().keySet();
		logger.info("Will produce {} monitor points",idOfMPoints.size());
		idOfMPoints.forEach(id -> WeatherPlugin.logger.info("Recognized MP {}",id));

		this.refreshTime = config.getAutoSendTimeInterval();
        logger.info("Refresh rate {} secs",refreshTime);

		// Read the relation IasValueID:StationID from the properties
		// and save it into an internal hash map
		Property[] props = config.getProperties();
		for (Property prop : props) {
		    weatherStationsIds.put(prop.getKey(), Integer.valueOf(prop.getValue()));
		}
	}

	/**
	 * Connect to the Weather Stations and add the shutdown hook.
	 */
	private void initialize() {
		weatherStationsPool = new WeatherStationsPool(weatherStationsIds,this);

		// Adds the shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUp, "Release weather station shutdown hook"));
	}

	/**
	 * Terminate the thread that publishes the data and disconnects from the weather
	 * station pool.
	 */
	private void cleanUp() {
		if (loopFuture != null) {
			loopFuture.cancel(false);
		}
		weatherStationsPool.release();
	}

	private static void printUsage() {
		System.out.println("Usage: java - jar alma-weather-plugin.jar");
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

		// Instantiate the WeatherPlugin
		WeatherPlugin plugin = new WeatherPlugin(config, kafkaPublisher);

		// Start the Plugin
		try {
			plugin.start();
		} catch (PublisherException pe) {
			logger.error("The plugin failed to start", pe);
			System.exit(-3);
		}

		// Connect to the Weather Stations.
		plugin.initialize();

		logger.info("Configuration done.");
	}
}
