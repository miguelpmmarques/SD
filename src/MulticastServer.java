import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.io.*;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.ToIntFunction;
import java.util.Queue;

import static java.util.stream.Collectors.toMap;

// thread responsible for listening in on multicast requests
public class MulticastServer extends Thread {
  private String MULTICAST_ADDRESS = "224.0.224.0";
  private int PORT = 4321;
  protected Queue<String> urls_queue = new LinkedList<>();
  protected QueueProcessor processQueue = null;
  protected ComunicationUrlsQueueRequestHandler com = new ComunicationUrlsQueueRequestHandler();
  HashMap responsesTable;

  public static void main(String[] args) {
    MulticastServer server = new MulticastServer("0");
    server.start();
  }

  public MulticastServer(String id) {
    super("server-" + id);
  }

  public void run() {
    processQueue = new QueueProcessor(this, "", com,responsesTable);    processQueue.start();

    listenMulticast();
    // have to check message parameter later
    // depending on the teachers feedback might be necessary later!
    // DatabaseHandler db_handler = new DatabaseHandler();
  }

  public QueueProcessor getProcessQueue() {
    return processQueue;
  }

  public Queue<String> getUrlsQueue() {
    return this.urls_queue;
  }

  public void listenMulticast() {
    HashMap<String,String> responsesTable = new HashMap<>();
    MulticastSocket socket = null;
    System.out.println(this.getName() + " running...");
    try {
      socket = new MulticastSocket(PORT);
      InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
      socket.joinGroup(group);
      while (true) {
        byte[] buffer = new byte[1000];
        DatagramPacket received_packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(received_packet);
        /*System.out.println(
            "Sender "
                + received_packet.getAddress().getHostAddress()
                + ":"
                + received_packet.getPort());*/
        String message = new String(received_packet.getData(), 0, received_packet.getLength());
        if (!message.equals("ACK")) {
          HandleRequest handle = new HandleRequest(this, message, com,responsesTable);
          handle.start();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      socket.close();
    }
  }
}

class QueueProcessor extends HandleRequest {
  public QueueProcessor(MulticastServer parent_thread, String message, ComunicationUrlsQueueRequestHandler com,HashMap responsesTable) {
    super(parent_thread, message, com,responsesTable);
    this.setName("Queue Processor-" + this.getId());
    System.out.println("URLS PROCESSING THREAD HERE!");
  }

  public void setUrls_queue(Queue<String> urls_queue) {
    this.urls_queue = urls_queue;
  }

  public void run() {
    HashMap[] indexes_and_references_to_send_or_add;
    while (true) {
        System.out.println("urls_queue==" + urls_queue.size());
        urls_queue = com.web_crawler();
      if (!urls_queue.isEmpty()) {

        synchronized (urls_queue) {
          /*try {
          sleep(1000);
          } catch (InterruptedException e) {
          e.printStackTrace();
          }*/
          String url = urls_queue.remove();
          System.out.println("Getting url " + url + " from queue to process");
          try {
            indexes_and_references_to_send_or_add = this.crawl(url);
            if (indexes_and_references_to_send_or_add.length != 0) {

              HashMap indexes = indexes_and_references_to_send_or_add[1];
              // send indexes to multicast ole
              HashMap references = indexes_and_references_to_send_or_add[0];
              // send references to queue
              Set references_set = references.keySet();
              synchronized (urls_queue) {
                for (Object ref : references_set) {
                  if(!String.valueOf(ref).isEmpty())
                    urls_queue.add(String.valueOf(ref));
                  //System.out.println("REF==" + ref);

                }
              }
              com.process_url(urls_queue);
              //System.out.println("TEST INDEXES======>" + indexes);
              //System.out.println("TEST References=======>" + references);
            }
          } catch (IOException e) {
            e.printStackTrace();
          }

        }
      }
    }
  }
}

class HandleRequest extends Thread {
  static final String INDEXFILE = "indexURL.tmp";
  static final String REFERENCEFILE = "referenceURL.tmp";
  static final String USERS_FILE = "users.tmp";
  private String MULTICAST_ADDRESS = "224.0.224.0";
  private String request;
  protected Queue<String> urls_queue;
  HashMap responsesTable;
  ComunicationUrlsQueueRequestHandler com;

  public HandleRequest(MulticastServer parent_thread, String message, ComunicationUrlsQueueRequestHandler com,HashMap responsesTable) {
    super();
    this.setName("Handler-" + this.getId());
    this.com = com;
    this.request = message;
    this.responsesTable=responsesTable;
    this.urls_queue = parent_thread.getUrlsQueue();
    System.out.println(this);
    System.out.println(this.urls_queue);
  }

  public void run() {
    System.out.println("I'm " + this.getName());
    // String message_to_send_to_rmi=protocolReaderMulticastSide(this.request);
    String messageToRMI = null;
    /*try {
      messageToRMI = protocolReaderMulticastSide(this.request);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if (messageToRMI.equals(""))
      return;
    System.out.println(messageToRMI);
    sendMulticastMessage(messageToRMI);*/
    try {
      interpretRequest();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    /*System.out.println("Message to send to rmi==" + message_to_send_to_rmi);
    // add to queue os responses awaiting acknowledge
        sendMulticastMessage(message_to_send_to_rmi);*/
  }

  public void sendMulticastMessage(String message) {
    int ack_port = 4322;
    MulticastSocket socket = null;

    System.out.println(this.getName() + " ready...");
    try {
      socket = new MulticastSocket(); // create socket without binding it (only for sending)
      byte[] buffer = message.getBytes();
      InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, ack_port);
      System.out.println("Sent message");
      socket.send(packet);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      socket.close();
    }
  }

  private String protocolReaderMulticastSide(String sms) throws InterruptedException {
    String[] splitedsms = sms.split("\\;");
    String messageToRMI = "";
    HashMap<String,String> myDic = new HashMap<>();
    for (int i =0;i<splitedsms.length;i++){
      String[] splitedsplitedsms = splitedsms[i].split("\\|");

      System.out.println(splitedsms[i]);
      myDic.put(splitedsplitedsms[0],splitedsplitedsms[1]);
    }
    System.out.println("QUEUE DE RESPOSTAS -> "+responsesTable);
    if(myDic.get("ACK") != null){
      responsesTable.remove(myDic.get("ACK"));
      System.out.println("QUEUE DE RESPOSTAS AFTER ACK -> "+responsesTable);
      return "";
    }
    if (responsesTable.get(myDic.get("id"))!=null ){
      System.out.println("NAO FOI A BASE DE DADOS");
      return (String)responsesTable.get(myDic.get("id"));
    }
    switch ((String)myDic.get("type")) {
      case "requestURLbyWord":
        return "id|"+(String)myDic.get("id")+";type|responseURLbyWord;status|logged on";

      case "requestURLbyRef":
        break;
      case "requestUSERhistory":
        break;
      case "requestUSERLogin":
        ArrayList<User> usersL = DatabaseHandler.loadUsersFromDataBase();
        System.out.println("SIZE USERS - "+usersL.size());
        for (int i = 0; i < usersL.size(); i++) {
          if (usersL.get(i).username.equals((String)myDic.get("user")) && usersL.get(i).password.equals((String)myDic.get("pass")))
          {
            if (usersL.get(i).getIsAdmin()){
              if (usersL.get(i).getNotify()){
                usersL.get(i).setNotify(false);
                responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin;Notify|true");
                return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin;Notify|true";

              }
              responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin");
              return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin";
            }
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged on");
            return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged on";
          }
        }

        return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged off;";
      case "requestUSERRegist":
        ArrayList<User> usersR = DatabaseHandler.loadUsersFromDataBase();
        System.out.println("SIZE USERS - "+usersR.size());
        if (usersR.isEmpty()){
          User adminUser = new User((String)myDic.get("user"),(String)myDic.get("pass"));
          adminUser.setIsAdmin();
          usersR.add(adminUser);

          DatabaseHandler bd = new DatabaseHandler(usersR);
          bd.start();
          responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Admin");
          return "id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Admin";
        }
        for (int i = 0; i < usersR.size(); i++) {
          System.out.println(usersR.get(i));
          if (usersR.get(i).username.equals((String)myDic.get("user")))
          {
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Failled");
            return "id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Failled";
          }
        }
        usersR.add(new User((String)myDic.get("user"),(String)myDic.get("pass")));
        System.out.println("SIZE USERS AFTER REGIST - "+usersR.size());
        DatabaseHandler bd = new DatabaseHandler(usersR);
        bd.start();
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Success");
        return "id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Success";
      case "requestAllUSERSPrivileges":
        ArrayList<User> usersS = DatabaseHandler.loadUsersFromDataBase();
        System.out.println("SIZE USERS - "+usersS.size());

        String messageToSend = "id|"+(String)myDic.get("id")+";type|responseUSERSPrivileges;user_count|"+usersS.size()+";";

        for (int i = 0; i < usersS.size(); i++)  {
          // Um if para verificar e indicar se e Admin ou nao
          messageToSend+="user_"+(i+1)+"|"+usersS.get(i).username+" -> ";
          if(usersS.get(i).getIsAdmin()){
            messageToSend+="Admin;";
          }
          else {
            messageToSend+="User;";
          }
        }
        responsesTable.put(myDic.get("id"),messageToSend);
        return messageToSend;
      case "requestSetNotify":
        ArrayList<User> usersN = DatabaseHandler.loadUsersFromDataBase();
        for (int i = 0; i < usersN.size(); i++)  {

          if (myDic.get("user").equals(usersN.get(i).username)){
            usersN.get(i).setNotify(true);
            DatabaseHandler bd3 = new DatabaseHandler(usersN);
            bd3.start();
          }
          responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseSetNotify");
          return "id|"+(String)myDic.get("id")+";type|responseSetNotify";
        }
      case "requestChangeUSERPrivileges":
        ArrayList<User> usersP = DatabaseHandler.loadUsersFromDataBase();
        for (int i = 0; i < usersP.size(); i++)  {
          if (myDic.get("user").equals(usersP.get(i).username)){
            usersP.get(i).setIsAdmin();
            DatabaseHandler bd2 = new DatabaseHandler(usersP);
            bd2.start();
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|New admin added with success");
            return "id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|New admin added with success";
          }

        }
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|User not Found");
        return "id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|User not Found";
      case "requestaddURLbyADMIN":
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseChangeUSERPrivileges;" + "status|Success");
        return "id|"+(String)myDic.get("id")+";type|responseChangeUSERPrivileges;" + "status|Success";
      case "requestSYSinfo":
        break;
      default:
        messageToRMI = "Error in protocol type";
    }
    return messageToRMI;
  }
  /*synchronized (urls_queue) {
      System.out.println("Adding " + value + " to urls queues");
      urls_queue.add(value);
      System.out.println("URLS QUEUE INSIDE HANDLE REQUEST SCOPE=" + urls_queue);
      com.process_url(urls_queue);
  }*/


  public void interpretRequest() throws InterruptedException {
    String request = this.request;
    //System.out.println("Request:" + request);

    // future expansion necessary
    String[] requests_array = request.split("\\|");
    String type = requests_array[0];
    String[] values = new String[requests_array.length - 1];
    for (int i = 1; i < requests_array.length; i++) {
      values[i - 1] = requests_array[i];
    }
    Object[] to_send_multicast_and_tcp;
    //System.out.println(type);
    //System.out.println(values);
    // MIGUELELELELELELE

    if (type.equals("URL")) {

      synchronized (urls_queue) {
        System.out.println("Adding " + values + " to urls queues");
        urls_queue.add(values[0]);
        //System.out.println("URLS QUEUE INSIDE HANDLE REQUEST SCOPE=" + urls_queue);
        com.process_url(urls_queue);
      }

      // integrate tcp and multicast code here;
      // divide references ( index 0  of array ), send one portion to this server's queue, and
      // send another portion via multicast to each of the other servers (doing that next!
      // Ass:Paulo)
      // send the changes made to the database, not the entire database itself, by tcp. (index 1
      // of array).

    } else if (type.equals("WORD")) {
      to_send_multicast_and_tcp = searchWords(values);
      //System.out.println("To send multicast =" + to_send_multicast_and_tcp.toString());
    } else {
      System.out.println("Unsuported Request!");
    }
    this.join();
  }

  public Object[] searchWords(String[] words) {
    // unnecessary-- add to search user function later
    // ArrayList<User> users_list = new ArrayList<>();
    // loadUsersFromDataBase(users_list);
    // System.out.println(users_list);
    // loading the indexes and references
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    HashMap<String, HashSet<String>> indexURL = new HashMap<>();
    ArrayList<HashMap<String, Integer>> aux_array = new ArrayList<>();
    try {
      System.out.println("reading from database");
      refereceURL = DatabaseHandler.loadDataBase(refereceURL, REFERENCEFILE);
      //System.out.println("finished reading from database");
      //System.out.println("reading from database");
      indexURL = DatabaseHandler.loadDataBase(indexURL, INDEXFILE);
      System.out.println("finished reading from database");

    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    Object[] ordered_urls_to_send;
    // getting the urls that have the requested word

    for (String word : words) {
      HashSet word_urls = indexURL.get(word);
      // getting the number of references for each url that has the requested word
      HashMap<String, Integer> urls_to_send = new HashMap<>();
      if (word_urls != null) {
        for (Object elem : word_urls) {
          String url = (String) elem;
          HashSet link_references = refereceURL.get(url);
          if (link_references != null) {
            int num_references = link_references.size();
            urls_to_send.put((String) elem, num_references);
          } else {
            urls_to_send.put((String) elem, 0);
          }
        }

        // sorting by number of references to a new hashmap
        // -------------------------------------------------------------------------------------
        /*HashMap<String, Integer> sorted =
            urls_to_send.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(
                    toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, HashMap::new));*/
        //System.out.println(urls_to_send.entrySet().);
        ArrayList<String> ole = new ArrayList<>();
        //ole.
        // -------------------------------------------------------------------------------------------------------------------------
        //System.out.println("SORTED=="+ sorted);
        //aux_array.addAll(urls_to_send.entrySet().toArray());
        // might have to save the user who made the request later;

      } else {
        aux_array.add(new HashMap<>());
      }
    }
    //ordered_urls_to_send = checkRepeatedWords(aux_array);
    //System.out.println("ORDERED ARRAY TO SEND= " + Arrays.toString(ordered_urls_to_send));
    //return ordered_urls_to_send;
    return new Object[2];
  }

  private Object[] checkRepeatedWords(ArrayList<HashMap<String, Integer>> aux_array) {
    int size = aux_array.size();
    HashMap first_set = aux_array.get(0);
    Set first_set_urls = first_set.keySet();
    for (int i = 1; i < size; i++) {
      Set current_set = aux_array.get(i).keySet();
      first_set_urls.retainAll(current_set);
    }
    return first_set_urls.toArray();
  }

  public HashMap[] crawl(String url) throws IOException {
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    HashMap<String, HashSet<String>> indexURL = new HashMap<>();
    HashMap[] database_changes_and_references_to_index = new HashMap[2];
    try {
      refereceURL = DatabaseHandler.loadDataBase(refereceURL, REFERENCEFILE);
      indexURL = DatabaseHandler.loadDataBase(indexURL, INDEXFILE);

    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    //System.out.println("index url:" + indexURL);
    //System.out.println("reference url:" + refereceURL);
    //System.out.println("URL =" + url);
    try {

      String inputLink = url;

      // Attempt to connect and get the document
      System.out.println("input link=" + inputLink);

      Document doc = Jsoup.connect(inputLink).get(); // Documentation: https://jsoup.org/

      // Title
      //System.out.println("WEB SITE TITLE > " + doc.title() + "\n");

      // Get all links
      Elements links = doc.select("a[href]");
      database_changes_and_references_to_index[0] =
          indexURLreferences(links, inputLink, refereceURL);

      String text = doc.text();
      database_changes_and_references_to_index[1] = indexWords(text, inputLink, indexURL);

      //System.out.println("URL REFERENCE FROM " + doc.title() + "\n" + refereceURL);
      //System.out.println("WORDS FOUND IN " + doc.title() + "\n" + indexURL);
      DatabaseHandler handler_db = new DatabaseHandler(refereceURL, indexURL);
      handler_db.start();
      // --------------------------------------------------------------------------HERE SAVE
      // DATABASE

    } catch(org.jsoup.HttpStatusException e){
      return new HashMap[0];
    } catch(UnknownHostException e) {
      return new HashMap[0];
    } catch (IOException e) {
      System.out.println("Did not search for website");
      e.printStackTrace();
    }catch(Exception e){
      e.printStackTrace();
    }
    return database_changes_and_references_to_index;
  }
  // possibly deprecated
  /*private static void runtMapUsers(ArrayList myList, ArrayList arrayFile) {
  Iterator it = arrayFile.iterator();
  while (it.hasNext()) {
   myList.add(it.next());
   it.remove(); // avoids a ConcurrentModificationException
  }
  }*/

  private static HashMap<String, HashSet<String>> indexURLreferences(
      Elements links, String inputWebsite, HashMap refereceURL) {
    HashMap<String, HashSet<String>> references_to_send = new HashMap<>();
    for (Element link : links) {
      String linkfound = link.attr("abs:href");
      HashSet aux = (HashSet) refereceURL.get(linkfound);
      if (aux == null) {
        aux = new HashSet<String>();
      }
      aux.add(inputWebsite);
      refereceURL.put(linkfound, aux);
      references_to_send.put(linkfound, aux);
    }
    //System.out.println(
        //"References to send-------------------------------------" + references_to_send);
    return references_to_send;
  }

  private static HashMap<String, HashSet<String>> indexWords(
      String text, String URL, HashMap indexURL) {
    HashMap<String, HashSet<String>> indexes_to_send = new HashMap<>();
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
    String line;

    // Get words and respective count
    while (true) {
      try {
        if ((line = reader.readLine()) == null) break;
        String[] words = line.split("[ ,;:.?!“”(){}\\[\\]<>']+");
        for (String word : words) {
          word = word.toLowerCase();
          if ("".equals(word)) {
            continue;
          }
          HashSet aux = (HashSet) indexURL.get(word);
          if (aux == null) {
            aux = new HashSet<String>();
          }
          aux.add(URL);
          indexURL.put(word, aux);
          indexes_to_send.put(word, aux);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    // Close reader
    try {
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return indexes_to_send;
  }
}
// Thread responsible for synchronized saving of objects to our database-- Object Files as of now
class DatabaseHandler extends Thread {
  static final String INDEXFILE = "indexURL.tmp";
  static final String REFERENCEFILE = "referenceURL.tmp";
  static final String USERS_FILE = "users.tmp";
  private static HashMap<String, HashSet<String>> refereceURL = null;
  private static HashMap<String, HashSet<String>> indexURL = null;
  private static ArrayList<User> users_list = null;

  // constructor for saving users
  public DatabaseHandler(ArrayList<User> users_list) {
    super();
    this.setName("DatabaseHandler-" + this.getId());
    this.users_list = users_list;
  }
  // constructor for saving urls
  public DatabaseHandler(
      HashMap<String, HashSet<String>> refereceURL, HashMap<String, HashSet<String>> indexURL) {
    super();
    this.setName("DatabaseHandler-" + this.getId());
    this.refereceURL = refereceURL;
    this.indexURL = indexURL;
  }
  // depending on the initialization of the class, the thread may save the users or the url HashMaps
  // so far
  public void run() {
    // waiting in conditional variable until save is
    if (this.users_list == null) {
      try {
        //System.out.println("SAVING FILES");
        //System.out.println("saving to database");
        saveHashSetsToDataBase(this.INDEXFILE, this.indexURL);
        //System.out.println("finished saving to database");
        //System.out.println("saving to database");
        saveHashSetsToDataBase(this.REFERENCEFILE, this.refereceURL);
        //System.out.println("finished saving to database");
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      try {
        saveArraysToDataBase(this.USERS_FILE, this.users_list);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public static synchronized ArrayList<User> loadUsersFromDataBase() {
    File f_users = new File(USERS_FILE);
    FileInputStream fis;
    ObjectInputStream ois;
    ArrayList<User> users_list = new ArrayList<>();
    try {
      fis = new FileInputStream(f_users);
      ois = new ObjectInputStream(fis);
      users_list = (ArrayList) ois.readObject();
      return users_list;
    } catch (FileNotFoundException ex) {
      System.out.println("Rip");
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    return users_list;
  }

  public static synchronized HashMap<String, HashSet<String>> loadDataBase(HashMap map, String file)
      throws InterruptedException {
    File f_ref = new File(file);
    FileInputStream fis;
    ObjectInputStream ois;
    try {
      fis = new FileInputStream(f_ref);
      ois = new ObjectInputStream(fis);
      map = (HashMap) ois.readObject();

      return map;

    } catch (FileNotFoundException ex) {
      System.out.println("Rip");
    } catch (IOException e) {
      System.out.println("IO");
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      System.out.println("Class not found");
      e.printStackTrace();
    }
    return new HashMap<>();
  }
  // for now, only useful for saving users
  private static synchronized void saveArraysToDataBase(String file_name, ArrayList<User> list)
      throws IOException {
    FileOutputStream fos = new FileOutputStream(file_name);
    try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {

      oos.writeObject(users_list);
      System.out.println("Inside Semaphore saving users");

      System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> USERS");
      oos.close();
      fos.close();
    } catch (FileNotFoundException ex) {
      System.out.println("Ficheiro nao encontrado");
    } catch (IOException ex) {
      System.out.println("Erro a escrever para o ficheiro.");
      ex.printStackTrace();
    }
  }
  // so far only useful for saving url hashMaps
  private static synchronized void saveHashSetsToDataBase(
      String file_name, HashMap<String, HashSet<String>> map) throws IOException {
    FileOutputStream fos = new FileOutputStream(file_name);
    try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {

      oos.writeObject(map);
      //System.out.println("Inside semaphore saving indexes or references");
      System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> OLE");
      oos.close();
      fos.close();
    } catch (FileNotFoundException ex) {
      System.out.println("Ficheiro nao encontrado");
    } catch (IOException ex) {
      System.out.println("Erro a escrever para o ficheiro.");
      ex.printStackTrace();
    }
  }
}

class ComunicationUrlsQueueRequestHandler {

  Queue<String> urls_queue;
  boolean check = false;

  synchronized Queue<String> web_crawler() {
    while (!check)
      try {
        wait();
      } catch (InterruptedException e) {
        System.out.println("interruptedException caught");
      }
    System.out.println("Crawling!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    check = false;
    notify();
    return this.urls_queue;
  }

  synchronized void process_url(Queue<String> sharedObj) {
    while (check)
      try {
        wait();
      } catch (InterruptedException e) {
        System.out.println("interruptedException caught");
      }
    check = true;
    this.urls_queue = sharedObj;
    notify();
  }
}

/*class ReferencesComparator implements Comparator<HashMap<String, Integer>>{
  public int compare(HashMap<String, Integer> s1,HashMap<String, Integer> s2){
    if(s1.equals(s2))
      return 0;
    else if(s1>s2)
      return 1;
    else
      return -1;
  }
}*/