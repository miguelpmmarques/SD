import java.io.IOException;
import java.net.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
//TCP CLASSES
// CLIENT ---------------------------------------------------------------------


public class MulticastServer extends Thread {
  private String MULTICAST_ADDRESS = "224.0.224.3";
  private String TCP_ADDRESS = "0.0.0.0";
  private int PORT = 6969;
  protected Queue<String> urls_queue = new LinkedList<>();
  protected QueueProcessor processQueue = null;
  protected ComunicationUrlsQueueRequestHandler com = new ComunicationUrlsQueueRequestHandler();
  private int myIdByTCP;
  HashMap responsesTable;
  FilesNamesObject filesManager;
  ArrayList<String[]> arrayListMulticastOnline = new ArrayList<String[]>();
  private Queue<HashMap> queue_database_changes = new LinkedList<>();
  private Queue<HashMap> queue_refs_to_send = new LinkedList();


  public static void main(String[] args) {
    TCP_SERVER serverTCP = new TCP_SERVER(1999,"0.0.0.0");
    int portId = serverTCP.tryConnection();
    FilesNamesObject filesManager = new FilesNamesObject(portId);
    System.out.println("Multicast Id -> "+portId);
    serverTCP.startTCPServer();
    MulticastServer server = new MulticastServer(portId,filesManager);
    server.start();
  }

  private void notifyALLbyMulticast(String type){System.out.println(this.getName() + " ready...");
    MulticastSocket socket = null;
    System.out.println(this.getName() + " ready...");
    String message = "type|"+type+";myid|"+myIdByTCP+";myIp|"+TCP_ADDRESS;
    try {
      socket = new MulticastSocket(); // create socket without binding it (only for sending)
      byte[] buffer = message.getBytes();
      InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
      System.out.println("NOTIFICATION SENT");
      socket.send(packet);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      socket.close();
    }
  }
  public int getMyIdByTCP() {
    return myIdByTCP;
  }

  public String getTCP_ADDRESS() {
    return TCP_ADDRESS;
  }


  public MulticastServer(int id,FilesNamesObject filesManager) {
    super("server-" + id);
    this.filesManager = filesManager;
    this.myIdByTCP = id;
    String[] myMulticast ={""+id,"0.0.0.0"};
    arrayListMulticastOnline.add(myMulticast);
    notifyALLbyMulticast("MulticatImAlive");
  }

  public void run() {
    processQueue = new QueueProcessor(this, "", com,responsesTable,filesManager,arrayListMulticastOnline);
    processQueue.start();

    listenMulticast();
    // have to check message parameter later
    // depending on the teachers feedback might be necessary later!
    // DatabaseHandler db_handler = new DatabaseHandler();
  }

  public QueueProcessor getProcessQueue() {
    return processQueue;
  }
  public Queue<HashMap> getQueue_database_changes(){
    return this.queue_database_changes;
  }

  public Queue<HashMap> getQueue_refs_to_send(){
    return this.queue_refs_to_send;
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
        byte[] buffer = new byte[5000];
        DatagramPacket received_packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(received_packet);
        String message = new String(received_packet.getData(), 0, received_packet.getLength());
        if (!message.equals("ACK")) {
          HandleRequest handle = new HandleRequest(this, message, com,responsesTable,filesManager,arrayListMulticastOnline);
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
  public QueueProcessor(MulticastServer parent_thread, String message, ComunicationUrlsQueueRequestHandler com,HashMap responsesTable,FilesNamesObject filesManager, ArrayList<String[]> arrayListMulticastOnline) {
    super(parent_thread, message, com,responsesTable,filesManager,arrayListMulticastOnline);
    this.setName("Queue Processor-" + this.getId());
    System.out.println("URLS PROCESSING THREAD HERE!");
  }

  //public void setUrls_queue(Queue<String> urls_queue) {
  //this.urls_queue = urls_queue;
  //}

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

              HashMap changes = indexes_and_references_to_send_or_add[1];
              this.queue_database_changes.add(changes);
              // send indexes to multicast ole
              HashMap references = indexes_and_references_to_send_or_add[0];
              this.queue_refs_to_send.add(references);
              // send references to queue
              Set references_set = references.keySet();
              sendMulticastMessage("type|needSync;");
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
  private String MULTICAST_ADDRESS = "224.0.224.3";
  private String request;
  protected Queue<String> urls_queue;
  HashMap<String,String> responsesTable;
  ComunicationUrlsQueueRequestHandler com;
  FilesNamesObject filesManager;
  ArrayList<String[]> arrayListMulticastOnline;
  Queue<HashMap> queue_database_changes;
  Queue<HashMap> queue_refs_to_send;
  private String TCP_ADDRESS;
  private int myIdByTCP;


  public HandleRequest(MulticastServer parent_thread, String message, ComunicationUrlsQueueRequestHandler com,HashMap responsesTable,FilesNamesObject filesManager,ArrayList<String[]> arrayListMulticastOnline) {
    super();
    this.setName("Handler-" + this.getId());
    this.com = com;
    this.request = message;
    this.responsesTable=responsesTable;
    this.urls_queue = parent_thread.getUrlsQueue();
    this.filesManager = filesManager;
    this.arrayListMulticastOnline=arrayListMulticastOnline;
    this.queue_database_changes = parent_thread.getQueue_database_changes();
    this.queue_refs_to_send = parent_thread.getQueue_refs_to_send();
    this.TCP_ADDRESS = parent_thread.getTCP_ADDRESS();
    this.myIdByTCP = parent_thread.getMyIdByTCP();

    System.out.println(this);
    System.out.println(this.urls_queue);
  }
  public void run() {

    System.out.println("I'm " + this.getName());
    String messageToRMI = null;
    try {
      messageToRMI = protocolReaderMulticastSide(this.request);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if (messageToRMI.equals(""))
      return;
    System.out.println(messageToRMI);
    sendMulticastMessage(messageToRMI);

  }

  public void sendMulticastMessage(String message) {
    int ack_port = 9696;
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
    DatabaseHandler bd;
    ArrayList<User> users;
    String messageToRMI = "";
    HashMap<String,String> myDic = new HashMap<>();
    for (int i =0;i<splitedsms.length;i++){
      String[] splitedsplitedsms = splitedsms[i].split("\\|");
      System.out.println(splitedsms[i]);
      myDic.put(splitedsplitedsms[0],splitedsplitedsms[1]);
    }

    if(myDic.get("ACK") != null){
      responsesTable.remove(myDic.get("ACK"));
      System.out.println("QUEUE DE RESPOSTAS AFTER ACK -> "+responsesTable);
      return "";
    }
    else if (myDic.get("type").equals("MulticatImAlive")){
      String[] newMulticast = {myDic.get("myid"),myDic.get("myIp")};
      arrayListMulticastOnline.add(newMulticast);
      System.out.println("OH SHIT MY NIGGA "+myDic.get("myid")+" IS ALIVE!");

      filesManager.loadDataBase("REFERENCE");
      filesManager.loadDataBase("INDEX");
      MessageByTCP messageToTCP = new MessageByTCP("NEW", this.filesManager.loadDataBase("REFERENCE"),this.filesManager.loadDataBase("INDEX"),this.filesManager.loadUsersFromDataBase());
      new TCP_CLIENT(Integer.parseInt(myDic.get("myid")),messageToTCP,myDic.get("myIp"));
      sendMulticastMessage("type|ACKMulticatImAlive");
    }
    else if (myDic.get("type").equals("ACKMulticatImAlive")){
      String[] newMulticast = {myDic.get("myid"),myDic.get("myIp")};
      arrayListMulticastOnline.add(newMulticast);
    }
    else if (responsesTable.get(myDic.get("id"))!=null ){
      return (String)responsesTable.get(myDic.get("id"));
    }
    System.out.println(myDic);
    switch ((String)myDic.get("type")) {
      case "requestURLbyWord":
        users = filesManager.loadUsersFromDataBase();
        for (int i = 0; i < users.size(); i++) {
          if (users.get(i).username.equals((String)myDic.get("user")) )
          {
            users.get(i).addSearchToHistory(returnString("word", myDic));
            bd = new DatabaseHandler(users,filesManager);
            bd.start();
          }
        }
        int numberWords = Integer.parseInt(myDic.get("word_count"));
        String[] values = new String[numberWords];
        for (int i = 0; i < numberWords; i++) {
          values[i] = myDic.get("word_"+(i+1));
          System.out.println(values[i]);
        }
        System.out.println(values);
        String[] urls=(String[]) searchWords(values);
        messageToRMI = "id|"+myDic.get("id")+";type|responseURLbyWord;url_count|"+urls.length+";";
        for (int i = 0; i < urls.length; i++) {
          messageToRMI+="url_"+(i+1)+"|"+urls[i]+";";
        }
        responsesTable.put(myDic.get("id"),messageToRMI);
        return messageToRMI;

      case "requestURLbyRef":
        HashMap<String,HashSet<String>> references = filesManager.loadDataBase("REFERENCE");
        HashSet<String> referencesFound = references.get(myDic.get("URL"));
        messageToRMI = "id|"+myDic.get("id")+";type|responseURLbyRef;url_count|"+referencesFound.size()+";";
        int k = 1;
        for (String elem : referencesFound) {
          messageToRMI+="url_"+k+"|"+elem+";";
          k++;
        }
        responsesTable.put(myDic.get("id"),messageToRMI);
        return messageToRMI;

      case "requestUSERhistory":
        User myUser;
        String message2send;
        ArrayList<String> myUserHistory;
        users = filesManager.loadUsersFromDataBase();
        for (int i = 0; i < users.size(); i++) {
          if (users.get(i).username.equals((String)myDic.get("user")) )
          {
            myUser = users.get(i);
            myUserHistory = myUser.getSearchToHistory();
            message2send = "word_count|"+myUserHistory.size()+";";
            for (int j = 0; j < myUserHistory.size(); j++) {
              message2send += "word_"+(j+1)+"|"+myUserHistory.get(j)+";";
            }
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responsetUSERhistory;"+message2send);
            return "id|"+(String)myDic.get("id")+";type|responsetUSERhistory;"+message2send;
          }
        }
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responsetUSERhistory;");
        return "id|"+(String)myDic.get("id")+";type|responsetUSERhistory;";


      case "requestUSERLogin":
        users = filesManager.loadUsersFromDataBase();
        for (int i = 0; i < users.size(); i++) {
          if (users.get(i).username.equals((String)myDic.get("user")) && users.get(i).password.equals((String)myDic.get("pass")))
          {
            if (users.get(i).getIsAdmin()){
              if (users.get(i).getNotify()){
                users.get(i).setNotify(false);
                responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin;Notify|true");
                return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin;Notify|true";

              }
              responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin;Notify|false");
              return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged admin;Notify|nop";
            }
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged on");
            return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged on;Notify|nop";
          }
        }
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged off;");
        return "id|"+(String)myDic.get("id")+";type|responseUSERLogin;status|logged off;";


      case "requestUSERRegist":
        users = filesManager.loadUsersFromDataBase();
        if (users.isEmpty()){
          User adminUser = new User((String)myDic.get("user"),(String)myDic.get("pass"));
          adminUser.setIsAdmin();
          users.add(adminUser);

          bd = new DatabaseHandler(users,filesManager);
          bd.start();
          responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Admin");
          return "id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Admin";
        }
        for (int i = 0; i < users.size(); i++) {
          if (users.get(i).username.equals((String)myDic.get("user")))
          {
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Failled");
            return "id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Failled";
          }
        }
        users.add(new User((String)myDic.get("user"),(String)myDic.get("pass")));
        bd = new DatabaseHandler(users,filesManager);
        bd.start();
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Success");
        return "id|"+(String)myDic.get("id")+";type|responseUSERRegist;status|Success";


      case "requestAllUSERSPrivileges":
        users = filesManager.loadUsersFromDataBase();
        String messageToSend = "id|"+(String)myDic.get("id")+";type|responseUSERSPrivileges;user_count|"+users.size()+";";

        for (int i = 0; i < users.size(); i++)  {
          // Um if para verificar e indicar se e Admin ou nao
          messageToSend+="user_"+(i+1)+"|"+users.get(i).username+" -> ";
          if(users.get(i).getIsAdmin()){
            messageToSend+="Admin;";
          }
          else {
            messageToSend+="User;";
          }
        }
        responsesTable.put(myDic.get("id"),messageToSend);
        return messageToSend;
      case "requestSetNotify":
        users = filesManager.loadUsersFromDataBase();
        for (int i = 0; i < users.size(); i++)  {

          if (myDic.get("user").equals(users.get(i).username)){
            users.get(i).setNotify(true);
            bd = new DatabaseHandler(users,filesManager);
            bd.start();
          }
          responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseSetNotify");
          return "id|"+(String)myDic.get("id")+";type|responseSetNotify";
        }
      case "requestChangeUSERPrivileges":
        users = filesManager.loadUsersFromDataBase();
        for (int i = 0; i < users.size(); i++)  {
          if (myDic.get("user").equals(users.get(i).username)){
            if (users.get(i).getIsAdmin())
              return "id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|User is already an Admin";
            users.get(i).setIsAdmin();
            users.get(i).setNotify(true);
            bd = new DatabaseHandler(users,filesManager);
            bd.start();
            responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|New admin added with success");
            return "id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|New admin added with success";
          }

        }
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|User not Found");
        return "id|"+(String)myDic.get("id")+";type|responseaddURLbyADMIN;" + "status|User not Found";
      case "requestaddURLbyADMIN":
        System.out.println("BATEU1");
        synchronized (urls_queue) {
          System.out.println("BATEU2");
          urls_queue.add(myDic.get("URL"));
          System.out.println(myDic.get("URL"));
          //System.out.println("URLS QUEUE INSIDE HANDLE REQUEST SCOPE=" + urls_queue);
          com.process_url(urls_queue);
        }
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseChangeUSERPrivileges;" + "status|Success");
        return "id|"+(String)myDic.get("id")+";type|responseChangeUSERPrivileges;" + "status|Success";
      case "requestSYSinfo":
        String message2Send= "activeMulticast_count|"+arrayListMulticastOnline.size()+";";
        Socket s;
        for (int i = 0; i < arrayListMulticastOnline.size(); i++) {
          try {
            s = new Socket(arrayListMulticastOnline.get(i)[1], Integer.parseInt(arrayListMulticastOnline.get(i)[0]));
            s.close();
            message2Send+="activeMulticast_"+(i+1)+"|"+arrayListMulticastOnline.get(i)[1]+":"+arrayListMulticastOnline.get(i)[0]+";";
          } catch (IOException e){
            System.out.println("Multicast esligou-se entretanto--"+arrayListMulticastOnline.get(i)[1]+":"+arrayListMulticastOnline.get(i)[0]);
          }
        }
        responsesTable.put(myDic.get("id"),"id|"+(String)myDic.get("id")+";type|responseSYSinfo;" + message2Send);
        return "id|"+(String)myDic.get("id")+";type|responseSYSinfo;" + message2Send;
      default:
        messageToRMI = "";
    }
    return messageToRMI;
  }
  private String returnString(String name,HashMap myDic){
    String returnS = "";
    int arraySize = Integer.parseInt((String)myDic.get(name+"_count"));
    for(int i =1 ;i<arraySize+1;i++){
      returnS+= (String)myDic.get(name+"_"+i) + " ";
    }
    return returnS;
  }

  public List<String> getTenMostReferencedUrls(){
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    System.out.println("reading from database");
    refereceURL = filesManager.loadDataBase("REFERENCE");
    System.out.println("finished reading from database");
    Set all_entrys = refereceURL.entrySet();
    HashMap<String, Integer> new_map = new HashMap<>();
    for(Object entry : all_entrys){
      Map.Entry actual_entry = (Map.Entry)entry;
      //System.out.println("Entry==" +actual_entry);
      HashSet aux  = (HashSet) actual_entry.getValue();
      new_map.put(String.valueOf(actual_entry.getKey()), aux.size());
    }
    //System.out.println("new_map=" +new_map);

    HashMap<String, Integer> sorted = orderHashMapByIntegerValue(new_map);
    System.out.println("sorted=" + sorted);
    Set key_set = sorted.keySet();
    //System.out.println("sorted key set=" + key_set);
    String[] array_to_send = (String[]) key_set.toArray(new String[0]);
    List<String> arrayList = Arrays.asList(array_to_send);
    // TO DO : VERIFICAR SE NAO CRAHA SE HOUVRE MENOS DE 10 URLS
    return arrayList.subList(0, 10);

  }

  public Object[] searchWords(String[] words) {
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    HashMap<String, HashSet<String>> indexURL = new HashMap<>();
    ArrayList<HashMap<String, Integer>> aux_array = new ArrayList<>();

  System.out.println("reading from database");
  refereceURL = this.filesManager.loadDataBase("REFERENCE");
  indexURL = this.filesManager.loadDataBase("INDEX");
  System.out.println("finished reading from database");


    String[] ordered_urls_to_send;
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
        HashMap<String, Integer> sorted = orderHashMapByIntegerValue(urls_to_send);

        System.out.println("SORTED=="+ sorted);
        aux_array.add(sorted);
      } else {
        aux_array.add(new HashMap<>());
      }
    }
    ordered_urls_to_send = checkRepeatedWords(aux_array);
    System.out.println("ORDERED ARRAY TO SEND= " + Arrays.toString(ordered_urls_to_send));
    return ordered_urls_to_send;
  }
  private HashMap<String, Integer> orderHashMapByIntegerValue(HashMap<String, Integer> urls_to_send){
    return  urls_to_send.entrySet().stream().
            sorted(new ReferencesComparator()).
            collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                    (e1, e2) -> e1, LinkedHashMap::new));

  }
  private String[] checkRepeatedWords(ArrayList<HashMap<String, Integer>> aux_array) {
    int size = aux_array.size();
    HashMap first_set = aux_array.get(0);
    Set first_set_urls = first_set.keySet();
    for (int i = 1; i < size; i++) {
      Set current_set = aux_array.get(i).keySet();
      first_set_urls.retainAll(current_set);
    }
    return (String[]) first_set_urls.toArray(new String[0]);
  }

  public HashMap[] crawl(String url) throws IOException {
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    HashMap<String, HashSet<String>> indexURL = new HashMap<>();
    HashMap[] database_changes_and_references_to_index = new HashMap[2];
    refereceURL = this.filesManager.loadDataBase("REFERENCE");
    indexURL = this.filesManager.loadDataBase("INDEX");


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
      DatabaseHandler handler_db = new DatabaseHandler(refereceURL, indexURL,filesManager);
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
  private HashMap<String, HashSet<String>> refereceURL = null;
  private HashMap<String, HashSet<String>> indexURL = null;
  private ArrayList<User> users_list = null;
  private  FilesNamesObject fileManager;

  // constructor for saving users
  public DatabaseHandler(ArrayList<User> users_list,FilesNamesObject fileManager) {
    super();
    this.setName("DatabaseHandler-" + this.getId());
    this.users_list = users_list;
    this.fileManager = fileManager;
  }
  // constructor for saving urls
  public DatabaseHandler(
      HashMap<String, HashSet<String>> refereceURL, HashMap<String, HashSet<String>> indexURL,FilesNamesObject fileManager) {
    super();
    this.setName("DatabaseHandler-" + this.getId());
    this.refereceURL = refereceURL;
    this.indexURL = indexURL;
    this.fileManager=fileManager;
  }
  // depending on the initialization of the class, the thread may save the users or the url HashMaps
  // so far
  public void run() {
    // waiting in conditional variable until save is
    if (this.users_list == null) {
        this.fileManager.saveHashSetsToDataBase("INDEX",this.indexURL);
        this.fileManager.saveHashSetsToDataBase("REFERENCE",this.refereceURL);
    } else {
        this.fileManager.saveUsersToDataBase(this.users_list);

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
class ReferencesComparator implements Comparator<Map.Entry<String, Integer>>{
  public int compare(Map.Entry<String, Integer> s1,Map.Entry<String, Integer> s2){
    if(s1.getValue().equals(s2.getValue()))
      return 0;
    else if(s1.getValue()<s2.getValue())
      return 1;
    else
      return -1;
  }
}