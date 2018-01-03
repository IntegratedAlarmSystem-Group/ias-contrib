
import java.io.StringReader;
import java.util.HashMap;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class WeatherSensor implements Runnable {

    // sensor testing
    public static void main(String[] args) {
        WeatherSensor sensor = new WeatherSensor(2);
        System.out.println(sensor);
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
     * creates a sensor with the given id, the id is the value given in the soap
     * request to access the data.
     *
     * @param id of the sensor to request.
     */
    WeatherSensor(int id) {
        // soap requests
        String url = "http://weather.aiv.alma.cl/ws_weather.php";
        String action = "getCurrentWeatherData";
        String idName = "id";
        soap = new SOAPRequest(url, url, action, idName);

        createDOMBuilder();

        // sensor id
        this.id = id;
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
        Document doc = parseDOM(response);

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
    }

    /**
     * returns the requested value for this sensor, if the name doesn't exists ???.
     */
    public double getValue(String name) {
        if (values.containsKey(name))
            return values.get(name);

        // TODO: what to do with unexisting values? exception?
        return 0.;
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
            System.err.println("Error occurred while creating document builder.");
            e.printStackTrace();
        }
    }

    /**
     * parses a soap request in xml format and returns the generated document.
     *
     * @param response the xml response of the soap weather service.
     * @return a DOM document containing the xml tree.
     */
    private Document parseDOM(String response) {
        try {
            return dBuilder.parse(new InputSource(new StringReader(response)));

        } catch (Exception e) {
            System.err.println("Error occurred while parsing the DOM.");
            e.printStackTrace();
        }
        return null;
    }
}
