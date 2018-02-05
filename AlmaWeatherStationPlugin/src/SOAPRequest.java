
import javax.xml.soap.*;

/**
 * Connects to a soap service and makes requests with one parameter,
 * returning the text content of the body.
 */
public class SOAPRequest {

  // SAAJ - SOAP Client Testing
  public static void main(String args[]) {
    String endpointUrl = "http://weather.aiv.alma.cl/ws_weather.php";
    String target = "http://weather.aiv.alma.cl/ws_weather.php";
    String action = "getCurrentWeatherData";
    String idName = "id";

    SOAPRequest soap = new SOAPRequest(endpointUrl, target, action, idName);

    for (int i = 2; i < 12; i++) {
      String response = soap.sendRequest(Integer.toString(i));
      System.out.println(response);
    }
  }

  // request parameters
  /**
   * endpoint to make the request.
   */
  private String endpointUrl;

  /**
   * target of the action, might be the equal to the endpoint.
   */
  private String target;

  /**
   * action of the request.
   */
  private String action;

  /**
   * name of the single parameter for the request.
   */
  private String idName;

  // SOAP elements
  private SOAPConnection connection;
  private SOAPMessage message;
  private SOAPElement text;

  /**
   * Creates a SOAPRequest that can send requests to the endpoint specified,
   * providing the target, action and name of the id given to the service.
   *
   * @param endpointUrl soap endpoint.
   * @param target      soap target.
   * @param action      soap action.
   * @param idName      name of the ONLY parameter of the service.
   */
  SOAPRequest(String endpointUrl, String target, String action, String idName) {
    this.endpointUrl = endpointUrl;
    this.target = target;
    this.action = action;
    this.idName = idName;

    try {
      createConnection();
    } catch (SOAPException e) {
      System.err.println("Error occurred while creating the connection.");
      e.printStackTrace();
      System.exit(1);
    }
    try {
      createMessage();
    } catch (SOAPException e) {
      System.err.println("Error occurred while creating the message.");
      e.printStackTrace();
      System.exit(2);
    }
  }

  /**
   * readies the connection and the message to send request to the web service.
   */
  private void createConnection() throws SOAPException {
    // Create SOAP Connection
    SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
    connection = connectionFactory.createConnection();
  }

  /**
   * creates the soap message object with all the info required for the request,
   * except for the value of the id.
   */
  private void createMessage() throws SOAPException {
    MessageFactory messageFactory = MessageFactory.newInstance();
    message = messageFactory.createMessage();
    SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();

    String namespace = "ns";

    // namespace declarations
    envelope.addNamespaceDeclaration(namespace, target);
    envelope.addNamespaceDeclaration("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    envelope.addNamespaceDeclaration("ns3", "http://www.w3.org/2001/XMLSchema");

    // SOAP Body
    SOAPBody body = envelope.getBody();
    SOAPElement elem = body.addChildElement(action, namespace);

    // where to add the value
    SOAPElement elemInfo = elem.addChildElement(idName);
    elemInfo.setAttribute("xsi:type", "ns3:integer");
    text = elemInfo.addTextNode("");

    // header
    MimeHeaders headers = message.getMimeHeaders();
    headers.addHeader("SOAPAction", target + "/" + action);

    message.saveChanges();
  }

  /**
   * changes the value of the id in the message created for the request.
   *
   * @param idValue the value to be used in the request.
   */
  private void setRequestValue(String idValue) throws SOAPException {
    text.setTextContent(idValue);
    message.saveChanges();
  }

  /**
   * Sends a request using the message and connection from the class, changes the
   * value of the message to the given parameter.
   *
   * @param idValue value to be sent in the request.
   * @return the text of the response, if there's more than one node with text
   * it's concatenated.
   */
  public String sendRequest(String idValue) {

    // set value for request
    try {
      setRequestValue(idValue);
    } catch (SOAPException e) {
      System.err.println("Error while setting request value " + idName + "=" + idValue);
      e.printStackTrace();
      return null;
    }

    try {
      // send request and get response
      SOAPMessage soapResponse = connection.call(message, endpointUrl);

      // return body content
      return soapResponse.getSOAPPart().getEnvelope().getTextContent();

    } catch (Exception e) {
      System.err.println("\nError while making the call to the service: " + endpointUrl);
      System.err.println("\tmake sure you have access to the requested url. Now exiting.\n");
      System.exit(1);
    }

    return null;
  }
}