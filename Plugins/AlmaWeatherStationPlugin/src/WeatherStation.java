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
import java.util.Objects;
import java.util.Date;

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
	private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

	private static final long almaZeroTimestamp = 122192928000000000L;

	/**
	 * Station id used in the soap request to obtain the current state.
	 */
	private int id;

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
	 * The timestamp from the last time the values were updated.
	 */
	private long lastUpdated = 0;

	/**
	 * Time to live of the values in this plugin in milliseconds. The weather
	 * station values should be updated before the ttl time passes.
	 */
	private long ttl;

	/**
	 * Max difference between current time and weather station response time to
	 * be consider as not responding.
	 */
	private int maxDelay = 600000;

	/**
	 * Creates a station with the given id.
	 * The id is the value associated with each station, necessary to get the
	 * data through SOAP request.
	 *
	 * @param id
	 *            Identifier of the weather station to be requested.
	 */
	WeatherStation(int id, int timeToLive) {

		// Create the SOAP Request
		String url = "http://weather.aiv.alma.cl/ws_weather.php";
		String action = "getCurrentWeatherData";
		String idName = "id";
		this.soap = new SOAPRequest(url, url, action, idName);

		// Document Builder to parse the SOAP responses
		this.dBuilder = createDOMBuilder();

		// Weather Station id
		this.id = id;
		this.ttl = timeToLive;
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
		updateValues();
	}

	/**
	 * Send a SOAP request and handle the response to update the values
	 */
	public void updateValues(){
		this.handleResponse(this.sendSoapRequest());
	}

	/**
	 * Parses a SOAP Request response and saves it.
	 *
	 * @param response
	 *							String corresponding to a Weather Station SOAP Request Response
	 */
	private void handleResponse(String response) {
		if (response == null)
			return;

		Document doc = parseDOM(response);
		if (doc == null)
			return;

		Node weatherNode = Objects.requireNonNull(doc).getElementsByTagName("weather").item(0);
		NamedNodeMap weatherAttributes = weatherNode.getAttributes();

		// Get station status
		String stationStatus = weatherAttributes.getNamedItem("status").getTextContent();
		boolean stationStatusValidity = this.validateStationStatus(stationStatus);

		// Get station timestamp
		long timestamp = Long.parseLong(weatherAttributes.getNamedItem("timestamp").getTextContent());
		boolean stationTimestampValidity = this.validateStationTimestamp(timestamp);

		// Update Values for each weather station sensor
		NodeList sensors = Objects.requireNonNull(doc).getElementsByTagName("sensor");
		for (int i = 0; i < sensors.getLength(); i++) {
			Node sensor = sensors.item(i);
			NamedNodeMap atts = sensor.getAttributes();
			String sensorName = atts.getNamedItem("name").getTextContent();
			double sensorValue = Double.parseDouble(sensor.getTextContent());

			// // If the station is responding with an old value, sensor will be updated
			// // with null values
			// if( !stationTimestampValidity ) {
			// 	sensorValue = Double.parseDouble("NaN");
			// }

			// If the station has status false, update the sensors with null values.
			if( !stationStatusValidity ) {
				sensorValue = -Double.MAX_VALUE;
			}

			// Update sensor values
			if (!values.containsKey(sensorName))
				values.put(sensorName, sensorValue);
			values.replace(sensorName, sensorValue);

		}

		lastUpdated = System.currentTimeMillis();
	}


	/**
	 * Parses the xml and saves it. (for testing purposes)
	 */
	public void updateValues(String xml) {
		Document doc = parseDOM(xml);
		NodeList sensors = Objects.requireNonNull(doc).getElementsByTagName("sensor");
		for (int i = 0; i < sensors.getLength(); i++) {
			Node sensor = sensors.item(i);

			NamedNodeMap atts = sensor.getAttributes();
			String name = atts.getNamedItem("name").getTextContent();
			double value = Double.parseDouble(sensor.getTextContent());

			if (!values.containsKey(name))
				values.put(name, value);
			values.replace(name, value);
		}

		lastUpdated = System.currentTimeMillis();
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
		// throw exception when out of date
		if (System.currentTimeMillis() - lastUpdated > ttl)
			throw new Exception("The values are too old, sensor" + id + " needs update.");

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
		return soap.sendRequest(Integer.toString(this.id));
	}

	/**
	* Transform the given timestamp from ALMA Timestamp to UTC timestamp and
	* compares it with the current UTC timestamp. If the difference is more than
	* maxDelay it returns False, otherwise return True.
	*
	* @param timestamp
	*							Weather Station ALMA timetamp to be validated
	* @return False if the difference is more than maxDelay, otherwise return True
	*/
	private boolean validateStationTimestamp(long timestamp) {
		long stationTimestamp = (timestamp - this.almaZeroTimestamp) / 10000;
		long currentTimestamp = new Date().getTime();
		long diff = currentTimestamp - stationTimestamp;
		if( diff > this.maxDelay) {
			return false;
		}
		return true;
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
	 * parses a soap request in xml format and returns the generated document.
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
