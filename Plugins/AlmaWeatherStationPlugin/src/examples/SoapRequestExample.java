public class SoapRequestExample {

  // SAAJ - SOAP Client Testing
  public static void main(String args[]) {
    String endpointUrl = "http://weather.aiv.alma.cl/ws_weather.php";
    String target = "http://weather.aiv.alma.cl/ws_weather.php";
    String action = "getCurrentWeatherData";
    String idName = "id";

    SOAPRequest soap = new SOAPRequest(endpointUrl, target, action, idName);

    System.out.println("Starting to send the requests");
    for (int i = 1; i < 12; i++) {
      String response = soap.sendRequest(Integer.toString(i));
      System.out.println(response);
    }
  }

}
