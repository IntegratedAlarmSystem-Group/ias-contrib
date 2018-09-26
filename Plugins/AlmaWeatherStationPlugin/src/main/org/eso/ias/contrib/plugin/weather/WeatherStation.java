package  org.eso.ias.contrib.plugin.weather;

import org.eso.ias.plugin.Plugin;
import org.eso.ias.types.OperationalMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A class that parses and saves weather data from
 * http://weather.aiv.alma.cl/ws_weather.php, to work it must be in a network
 * from ALMA, ESO, NAOJ or NRAO.
 * <p>
 * The values can be updated using updateValues() or run() (for scheduling).
 */
public class WeatherStation implements Runnable {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherStation.class);

	/**
	 * Value used as Zero timestamp in the Alma Observatory
	 */
	private static final long almaZeroTimestamp = 122192928000000000L;

	/**
	 * Station id used in the soap request to obtain the current state.
	 */
	private final String id;

	/**
	 * DOM parser.
	 */
	private DocumentBuilder dBuilder;

	/**
	 * SOAP client.
	 */
	private SOAPRequest soap;

	/**
	 * sensor values {temperature, pressure, dewpoint, humidity, wind speed, wind
	 * direction}.
	 */
	private HashMap<String, Double> values = new HashMap<>();

    /**
     * The ID of the weather station for SOAP
     */
	private final int soapWsId;

	/**
	 * The plugin to notify with new samples
	 */
	private final Plugin plugin;

    /**
     * Map the names of the monitor points returned by SOAP
     * to those expected by the plugin
     */
	private final Map<String,String> mapOfMpointNames = new HashMap<>();

	/**
	 * Creates a station with the given id.
	 * The id is the value associated with each station, necessary to get the
	 * data through SOAP request.
	 *
	 * @param id the identifier of the weather like MeteoCentral
     * @param soapWsId The ID of the weather station for SOAP
	 * @param plugin                The plugin to send new sample
	 */
	WeatherStation(String id, int soapWsId, Plugin plugin) {
		Objects.requireNonNull(plugin);
		Objects.requireNonNull(id);
		this.plugin=plugin;
		this.soapWsId=soapWsId;

		// Create the SOAP Request
		String url = "http://weather.aiv.alma.cl/ws_weather.php";
		String action = "getCurrentWeatherData";
		String idName = "id";
		this.soap = new SOAPRequest(url, url, action, idName);

		// Document Builder to parse the SOAP responses
		this.dBuilder = createDOMBuilder();

		// Weather Station id
		this.id = id;

		// Only this mpoint wil be sent to the plugin
		mapOfMpointNames.put("wind speed", "WindSpeed");
        mapOfMpointNames.put("humidity", "Humidity");
        mapOfMpointNames.put("temperature", "Temperature");
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(id);
		sb.append(System.lineSeparator());

		for (String key : values.keySet()) {
			sb.append("  ");
			sb.append(key);
			sb.append(": ");
			sb.append(values.get(key));
			sb.append(System.lineSeparator());
		}

		return sb.toString().trim();
	}

	/**
	 * method to keep running in the threads, only updates values.
	 */
	@Override
	public void run() {
		this.handleResponse(this.sendSoapRequest());
	}

	/**
	 * Parses a SOAP Request response and saves it.
	 *
	 * @param response
	 *							String corresponding to a Weather Station SOAP Request Response
	 */
	private void handleResponse(String response) {
		if (response == null) {
		    logger.error("WS {} got an empty response from SOAP",id);
		    return;
        }

		Document doc = parseDOM(response);
		if (doc == null) {
            logger.error("WS {} error parsing the response from SOAP",id);
            return;
        }

		Node weatherNode = Objects.requireNonNull(doc).getElementsByTagName("weather").item(0);
		NamedNodeMap weatherAttributes = weatherNode.getAttributes();

		// Get station status
		String stationStatus = weatherAttributes.getNamedItem("status").getTextContent();
		boolean stationStatusValidity = this.validateStationStatus(stationStatus);

		// Update Values for each weather station sensor
		NodeList sensors = Objects.requireNonNull(doc).getElementsByTagName("sensor");
		for (int i = 0; i < sensors.getLength(); i++) {
			Node sensor = sensors.item(i);
			NamedNodeMap atts = sensor.getAttributes();
			String sensorName = atts.getNamedItem("name").getTextContent();
			double sensorValue = Double.parseDouble(sensor.getTextContent());

			// If the station has status false, update the sensors with null values.
			if( !stationStatusValidity ) {
				sensorValue = -Double.MAX_VALUE;
			}

			String mappedMPName = mapOfMpointNames.get(sensorName);
			if (mappedMPName!=null) {
                // Update sensor values
                String mPointName = "WS-"+id+"-"+mappedMPName+"-Value";


                try {
                    if (sensorValue == -Double.MAX_VALUE) {
                        sensorValue = Double.parseDouble("NaN");
                        plugin.setOperationalMode(mPointName, OperationalMode.SHUTTEDDOWN);
                    } else {
                        plugin.setOperationalMode(mPointName, OperationalMode.OPERATIONAL);
                    }
                } catch (Exception e) {
                    logger.error("Error setting operational mode of mpoint {}",mPointName,e);
                }


                try {
                    plugin.updateMonitorPointValue(mPointName, sensorValue);
                } catch (Exception e) {
                    logger.error("Error submitting mpoint {}: value lost",mPointName,e);
                }
            }
		}
	}

	/**
	 * Returns the requested value for this sensor, if the name doesn't exists
	 * throws an exception. If the time since the last update is greater than the
	 * ttl, throw exception.
	 *
	 * @param name
	 *            the name of the parameter.
	 * @return the value requested
	 * @throws Exception
	 *             if it doesnt exists, or it is too old.
	 */
	public double getValue(String name) throws Exception {

		if (values.containsKey(name))
			return values.get(name);

		// throws exception when value not found
		throw new Exception("The value " + name + " was not found.");
	}

	/**
	* Creates the document builder that will be used later to parse the SOAP
	* response.
	*
	* @return the created document builder
	*/
	private DocumentBuilder createDOMBuilder() {
		DocumentBuilder dBuilder = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dBuilder = dbFactory.newDocumentBuilder();
		} catch (Exception e) {
			logger.error("Error occurred while creating document builder.");
			e.printStackTrace();
			System.exit(1);
		}
		return dBuilder;
	}

	/**
	* Send the SOAP Request to get updated data for this weather station.
	*
	* @return The SOAP Request Response
	*/
	private String sendSoapRequest(){
		return soap.sendRequest(Integer.toString(soapWsId));
	}

	/**
	 * Check the status of the WeatherStation
	 *
	 * @param state
	 *						String corresponding to the state of the station
	 * @return True if the weatherStation is operative and False if it is not
	 */
	private boolean validateStationStatus(String state) {
		if( state.equals("true")){
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Parses a soap request in xml format and returns the generated document.
	 *
	 * @param response
	 *            the xml response of the soap weather service.
	 * @return a DOM document containing the xml tree.
	 */
	private Document parseDOM(String response) {
		try {
			return dBuilder.parse(new InputSource(new StringReader(response)));

		} catch (Exception e) {
			logger.warn("Error occurred while parsing the DOM. \n" + e);
		}
		return null;
	}
}
