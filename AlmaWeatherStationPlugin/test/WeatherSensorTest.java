import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * test the Weather Sensor, in charge of parsing the SOAP requests made to the service.
 */
public class WeatherSensorTest {

    /**
     * sensor to be used in the tests.
     */
    private WeatherSensor sensor;

    @Before
    public void setUp() {
        // sensor with any id, wont be used.
        sensor = new WeatherSensor(0);

        // response of the soap service example.
        String xml = "<weather id='2' timestamp='1.3734218308E+17' status='true' serialNumber='D2320028D1810012'><sensors>" +
                "<sensor name='humidity' unit='percentage'>32.131</sensor>" +
                "<sensor name='temperature' unit='celsius'>-0.064</sensor>" +
                "<sensor name='dewpoint' unit='celsius'>-14.689</sensor>" +
                "<sensor name='wind direction' unit='degree'>274.000</sensor>" +
                "<sensor name='wind speed' unit='m/s'>12.400</sensor>" +
                "<sensor name='pressure' unit='hPa'>554.910</sensor>" +
                "</sensors></weather>\n";

        // update values
        sensor.updateValues(xml);
    }

    @Test
    public void getValue() throws Exception {
        // check that it gets all the values in the xml
        assertEquals(32.131, sensor.getValue("humidity"), 0.);
        assertEquals(-0.064, sensor.getValue("temperature"), 0.);
        assertEquals(-14.689, sensor.getValue("dewpoint"), 0.);
        assertEquals(274., sensor.getValue("wind direction"), 0.);
        assertEquals(12.4, sensor.getValue("wind speed"), 0.);
        assertEquals(554.91, sensor.getValue("pressure"), 0.);
    }

    @Test(expected = Exception.class)
    public void getValueError() throws Exception {
        sensor.getValue("SomeValue");
    }
}
