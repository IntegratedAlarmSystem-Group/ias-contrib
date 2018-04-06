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
public class WeatherSensor implements Runnable {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(WeatherPlugin.class);

	/**
	 * sensor id the will be sent in the soap request.
	 */
	private int id;

	/**
	 * DOM parser.
	 */
	private DocumentBuilder dBuilder;

	/**
	 * soap client.
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
	 * time to live of the values in this plugin, in milliseconds.
	 */
	private long ttl;
	
	/**
	 * Max delay for getting data from a weather station
	 */
	private int maxDelay = 300000;

	/**
	 * creates a sensor with the given id, the id is the value given in the soap
	 * request to access the data.
	 *
	 * @param id
	 *            of the sensor to request.
	 */
	WeatherSensor(int id, int timeToLive) {
		// soap requests
		String url = "http://weather.aiv.alma.cl/ws_weather.php";
		String action = "getCurrentWeatherData";
		String idName = "id";
		soap = new SOAPRequest(url, url, action, idName);

		createDOMBuilder();

		// sensor id
		this.id = id;
		ttl = timeToLive;
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
	 * creates a soap request for the sensor data, parses and saves it.
	 */
	public void updateValues() {
		String response = soap.sendRequest(Integer.toString(id));
		if (response == null)
			return;
		
		Document doc = parseDOM(response);
		if (doc == null)
			return;

		// Check timestamp
		Node weatherNode = Objects.requireNonNull(doc).getElementsByTagName("weather").item(0);
		NamedNodeMap weatherAtts = weatherNode.getAttributes();
		long almaZeroTimestamp = 122192928000000000L;
		long timestamp = Long.parseLong(weatherAtts.getNamedItem("timestamp").getTextContent());

		long stationTimestamp = (timestamp - almaZeroTimestamp) / 10000;
		long currentTimestamp = new Date().getTime();
		long diff = currentTimestamp - stationTimestamp;

		// Update Values
		NodeList sensors = Objects.requireNonNull(doc).getElementsByTagName("sensor");
		for (int i = 0; i < sensors.getLength(); i++) {
			Node sensor = sensors.item(i);
			NamedNodeMap atts = sensor.getAttributes();
			String name = atts.getNamedItem("name").getTextContent();
			double value = Double.parseDouble(sensor.getTextContent());
			
			if( diff > maxDelay) {
				value = Double.parseDouble("NaN");
			}
			if (!values.containsKey(name))
				values.put(name, value);
			values.replace(name, value);
			
		}

		lastUpdated = System.currentTimeMillis();
	}

	/**
	 * parses the xml and saves it. (for testing purposes)
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
	 * creates the document builder that will be used later to parse the soap
	 * response.
	 */
	private void createDOMBuilder() {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dBuilder = dbFactory.newDocumentBuilder();

		} catch (Exception e) {
			logger.error("Error occurred while creating document builder.");
			e.printStackTrace();
			System.exit(1);
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
			logger.warn("Error occurred while parsing the DOM.");
		}
		return null;
	}

	// sensor testing
	public static void main(String[] args) {
		WeatherSensor sensor = new WeatherSensor(2, 2000);
		sensor.updateValues();
		System.out.println(sensor);
	}
}
