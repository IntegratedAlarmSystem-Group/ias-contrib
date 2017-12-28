package weatherPlugin;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eso.ias.plugin.Plugin;
import org.eso.ias.plugin.PluginException;
import org.eso.ias.plugin.config.PluginConfig;
import org.eso.ias.plugin.config.PluginConfigException;
import org.eso.ias.plugin.config.PluginConfigFileReader;
import org.eso.ias.plugin.publisher.MonitorPointSender;
import org.eso.ias.plugin.publisher.PublisherException;
import org.eso.ias.plugin.publisher.impl.JsonFilePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WeatherStationPlugin extends Plugin {

    /**
     * The logger
     */
    private static final Logger logger = LoggerFactory.getLogger(WeatherStationPlugin.class);

    /**
     * The path from the resources where JSON files for testing have been saved
     */
    private static final String configPath = "AlmaWeatherStationPlugin/test/resources/WeatherStationPluginTest.json";

    /**
     * The future of the loop
     */
    private ScheduledFuture<?> loopFuture;

    /**
     * the weather station to retrieve the data
     */
    private WeatherStation weather;

    /**
     * Constructor
     *
     * @param config The configuration of the plugin
     * @param sender The sender
     */
    public WeatherStationPlugin(PluginConfig config, MonitorPointSender sender) {
        super(config, sender);
    }

    /**
     * Connect to the monitored system.
     * <p>
     * In this example there is no real connection/initialization because the remote
     * system is simulated by a java object. However we need to get at least a
     * reference to the simulated value to start the updating threads: we consider
     * this our initialization with the monitored system
     * <p>
     * In a real system it can be opening a socket, connecting to a database or to a
     * hardware device and so on
     */
    public void initialize() {
        // refreshes every 2 seconds
        weather = new WeatherStation(2, 11, 1);

        // Adds the shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                cleanUp();
            }
        }, "Release weather station shutdown hook"));
    }

    /**
     * Close the connection with the remote system before exiting.
     * <p>
     * In the example there is no real connection but we take opportunity here to
     * terminate the threads to update the values of the monitor points
     * <p>
     * In a real system it can be closing a socket, disconnecting from a database or
     * a hardware device and so on
     */
    public void cleanUp() {
        if (loopFuture != null) {
            loopFuture.cancel(false);
        }

        weather.release();
    }

    /**
     * Override method to catch the exception and log a message
     * <p>
     * In the example we do not take any special action if the Plugin returns an
     * error when submitting a new value.
     *
     * @see org.eso.ias.plugin.Plugin#updateMonitorPointValue(java.lang.String,
     * java.lang.Object)
     */
    @Override
    public void updateMonitorPointValue(String mPointID, Object value) {
        try {
            super.updateMonitorPointValue(mPointID, value);
        } catch (PluginException pe) {
            logger.error("Error sending {} monitor point to the core of the IAS", mPointID);
        }
    }

    /**
     * The loop to get monitor values from the weather station and send to the core
     * of the IAS.
     * <p>
     * The weather stations updates the values every minute, 30 seconds and 2
     * seconds as you can see in the definition of the
     * {@link SimulatedMonitorPoint}.
     * <p>
     * In reality, the refresh time of the monitor points is described in the
     * Interface Control Document but there are cases where such a time frame does
     * not exist and the user must poll the value when he/she needs. Even more,
     * there are other cases where the monitored control system notify the listeners
     * about changes of the values of the monitor points. <BR>
     * It is not easy to generalize the loop but it must be developed case by case
     * depending on the API provided by the monitored control software.
     * <p>
     * For the simulated weather station, it is enough to loop every 2 seconds.
     */
    public void startLoop() {
        loopFuture = getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
            // Counts the second at every tick
            int count = 0;

            public void run() {
                logger.info("Updating monitor point values from the simulated weather station");
                for (int i = 2; i < 12; i++) {
                    Double temperature = weather.getValue(i, "temperature");
                    updateMonitorPointValue("Temperature" + i, temperature);

                    Double dewpoint = weather.getValue(i, "dewpoint");
                    updateMonitorPointValue("Dewpoint" + i, dewpoint);

                    Double humidity = weather.getValue(i, "humidity");
                    updateMonitorPointValue("Humidity" + i, humidity);
                    Double pressure = weather.getValue(i, "pressure");
                    updateMonitorPointValue("Pressure" + i, pressure);

                    // 2 seconds
                    Double windSpeed = weather.getValue(i, "wind speed");
                    // The ID of the monitor point is that of the configuration file
                    updateMonitorPointValue("WindSpeed" + i, windSpeed);

                    Double windDir = weather.getValue(i, "wind direction");
                    updateMonitorPointValue("WindDirection" + i, windDir);

                }
                count = (count < Integer.MAX_VALUE) ? count + 2 : 0;
                logger.info("Monitor point values updated");
            }
        }, 0, 1, TimeUnit.SECONDS);
        try {
            loopFuture.get();
        } catch (ExecutionException ee) {
            logger.error("Execution exception getting values from the weather station", ee);
        } catch (Exception ce) {
            logger.info("Loop to get minitor point values from the weather station terminated");
        }
    }

    public static void main(String[] args) {
        logger.info("Started...");
        PluginConfig config = null;
        try {
            File configFile = new File(configPath);
            PluginConfigFileReader jsonFileReader = new PluginConfigFileReader(configFile);
            Future<PluginConfig> futurePluginConfig = jsonFileReader.getPluginConfig();
            config = futurePluginConfig.get(1, TimeUnit.MINUTES);
        } catch (FileNotFoundException e) {
            logger.error("Excetion opening config file", e);
            e.printStackTrace();
            System.exit(-1);
        } catch (PluginConfigException pce) {
            logger.error("Excetion reading configuratiopn", pce);
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
        logger.info("Configuration successfully red");

        // Create the file in the IAS temporary folder
        String tmpFolderName = System.getProperty("ias.tmp.folder", ".");
        File folder = new File(tmpFolderName);
        BufferedWriter jsonWriter = null;
        try {
            File jsonFile = File.createTempFile("MonitorPointValues", ".json", folder);
            jsonWriter = new BufferedWriter(new FileWriter(jsonFile));
            logger.info("Moitor points to be sent to the core of the IAS will be saved in {}",
                    jsonFile.getAbsolutePath());
        } catch (IOException ioe) {
            logger.error("Cannot create the JSON file", ioe);
            System.exit(-1);
        }

        JsonFilePublisher jsonPublisher = new JsonFilePublisher(config.getId(), config.getMonitoredSystemId(),
                config.getSinkServer(), config.getSinkPort(), Plugin.getScheduledExecutorService(), jsonWriter);

        WeatherStationPlugin plugin = new WeatherStationPlugin(config, jsonPublisher);

        try {
            plugin.start();
        } catch (PublisherException pe) {
            logger.error("The plugin failed to start", pe);
            System.exit(-3);
        }

        // Connect to the weather station
        plugin.initialize();

        // Start getting data from the weather station
        //
        // This method exits when the user presses CTRL+C
        // and the shutdown hook disconnects from the weather station.
        plugin.startLoop();

        logger.info("Done.");
    }

}
