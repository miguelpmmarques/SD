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
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.Queue;

import static java.util.stream.Collectors.toMap;

// thread responsible for listening in on multicast requests
public class MulticastServer extends Thread {
  private String MULTICAST_ADDRESS = "224.0.224.0";
  private int PORT = 4321;
  private long SLEEP_TIME = 5000;
  protected Queue<String> urls_queue = new LinkedList<>();
  QueueProcessor processQueue = null;
  Comunication com = new Comunication();

  public static void main(String[] args) {
    MulticastServer server = new MulticastServer(args[0]);
    server.start();
  }

  public MulticastServer(String id) {
    super("server-" + id);
  }

  public void run() {
    processQueue = new QueueProcessor(this, "", com);
    processQueue.start();
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
        System.out.println(
            "Sender "
                + received_packet.getAddress().getHostAddress()
                + ":"
                + received_packet.getPort());
        String message = new String(received_packet.getData(), 0, received_packet.getLength());
        if (!message.equals("ACK")) {
          HandleRequest handle = new HandleRequest(this, message, com);
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
  public QueueProcessor(MulticastServer parent_thread, String message, Comunication com) {
    super(parent_thread, message, com);
    this.setName("Queue Processor-" + this.getId());
    System.out.println("URLS PROCESSING THREAD HERE!");
  }

  public void setUrls_queue(Queue<String> urls_queue) {
    this.urls_queue = urls_queue;
  }

  public void run() {
    while (true) {
      urls_queue = com.web_crawler();
      System.out.println("urls_queue==" + urls_queue);
      if (!urls_queue.isEmpty()) {
        synchronized (urls_queue) {
          /*try {
            sleep(1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }*/
          String url = urls_queue.remove();
          System.out.println("Getting url " + url + "from queue to process");
          try {
            this.crawl(url);
          } catch (IOException e) {
            e.printStackTrace();
          }
          System.out.println("Processing url from queue");
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
  Comunication com;

  public HandleRequest(MulticastServer parent_thread, String message, Comunication com) {
    super();
    this.setName("Handler-" + this.getId());
    this.com = com;
    this.request = message;
    this.urls_queue = parent_thread.getUrlsQueue();
    System.out.println(this);
    System.out.println(this.urls_queue);
  }

  public void run() {
    System.out.println("I'm " + this.getName());
    sendMulticastMessage("ACK");
    try {
      interpretRequest();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
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

  public void interpretRequest() throws InterruptedException {
    String request = this.request;
    System.out.println("Request:" + request);

    // future expansion necessary
    String[] requests_array = request.split("\\|");
    String type = requests_array[0];
    String value = requests_array[1];
    String[] to_send_multicast_and_tcp = new String[2];
    System.out.println(type);
    System.out.println(value);
    // MIGUELELELELELELE
    if (type.equals("URL")) {
      synchronized (urls_queue) {
        System.out.println("Adding " + value + " to urls queues");
        urls_queue.add(value);
        System.out.println("URLS QUEUE INSIDE HANDLE REQUEST SCOPE=" + urls_queue);
        com.process_url(urls_queue);
      }


      // integrate tcp and multicast code here;
      // divide references ( index 0  of array ), send one portion to this server's queue, and
      // send another portion via multicast to each of the other servers (doing that next!
      // Ass:Paulo)
      // send the changes made to the database, not the entire database itself, by tcp. (index 1
      // of array).

    } else if (type.equals("WORD")) {
      searchWord(value);
    } else {
      System.out.println("Unsuported Request!");
    }

    this.join();
  }

  public void searchWord(String word) {
    // unnecessary-- add to search user function later
    // ArrayList<User> users_list = new ArrayList<>();
    // loadUsersFromDataBase(users_list);
    // System.out.println(users_list);
    // loading the indexes and references
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    HashMap<String, HashSet<String>> indexURL = new HashMap<>();
    refereceURL = DatabaseHandler.loadDataBase(refereceURL, REFERENCEFILE);
    indexURL = DatabaseHandler.loadDataBase(indexURL, INDEXFILE);
    // getting the urls that have the requested word
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
      HashMap<String, Integer> sorted =
          urls_to_send.entrySet().stream()
              .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, HashMap::new));
      // -------------------------------------------------------------------------------------------------------------------------
      System.out.println(sorted);
      // might have to save the user who made the request later;
      sendMulticastMessage(sorted.toString());
    } else {
      sendMulticastMessage(
          "No such word in our database! Please refer to the admins to ask for further url indexation");
    }
  }

  public String[] crawl(String url) throws IOException {
    HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
    HashMap<String, HashSet<String>> indexURL = new HashMap<>();
    String[] database_changes_and_references_to_index = new String[2];
    refereceURL = DatabaseHandler.loadDataBase(refereceURL, REFERENCEFILE);
    indexURL = DatabaseHandler.loadDataBase(indexURL, INDEXFILE);
    System.out.println("index url:" + indexURL);
    System.out.println("reference url:" + refereceURL);
    try {
      String inputLink = url;
      if (!inputLink.startsWith("http://") || !inputLink.startsWith("https://"))
        inputLink = "http://".concat(inputLink);

      // Attempt to connect and get the document
      Document doc = Jsoup.connect(inputLink).get(); // Documentation: https://jsoup.org/

      // Title
      System.out.println("WEB SITE TITLE > " + doc.title() + "\n");

      // Get all links
      Elements links = doc.select("a[href]");
      database_changes_and_references_to_index[0] =
          indexURLreferences(links, inputLink, refereceURL);

      String text = doc.text();
      database_changes_and_references_to_index[1] = indexWords(text, inputLink, indexURL);

      System.out.println("URL REFERENCE FROM " + doc.title() + "\n" + refereceURL);
      System.out.println("WORDS FOUND IN " + doc.title() + "\n" + indexURL);
      DatabaseHandler handler_db = new DatabaseHandler(refereceURL, indexURL);
      handler_db.start();
      // --------------------------------------------------------------------------HERE SAVE
      // DATABASE
    } catch (IOException e) {
      System.out.println("Did not search for website");
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



  private static String indexURLreferences(
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
    return references_to_send.toString();
  }

  private static String indexWords(String text, String URL, HashMap indexURL) {
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
    return indexes_to_send.toString();
  }
}
// Thread responsible for synchronized saving of objects to our database-- Object Files as of now
class DatabaseHandler extends Thread {
  static final String INDEXFILE = "indexURL.tmp";
  static final String REFERENCEFILE = "referenceURL.tmp";
  static final String USERS_FILE = "users.tmp";
  private HashMap<String, HashSet<String>> refereceURL = null;
  private HashMap<String, HashSet<String>> indexURL = null;
  private ArrayList<User> users_list = null;
  private boolean live;

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
        System.out.println("SAVING FILES");
        saveHashSetsToDataBase(this.INDEXFILE, this.indexURL);
        saveHashSetsToDataBase(this.REFERENCEFILE, this.refereceURL);
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

  public static synchronized ArrayList<User> loadUsersFromDataBase(ArrayList<User> users_list) {
    File f_users = new File(USERS_FILE);
    FileInputStream fis;
    ObjectInputStream ois;
    try {
      fis = new FileInputStream(f_users);
      ois = new ObjectInputStream(fis);
      ArrayList users_list_aux = (ArrayList) ois.readObject();
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

  public static synchronized HashMap<String, HashSet<String>> loadDataBase(
      HashMap<String, HashSet<String>> map, String file) {
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
  private synchronized void saveArraysToDataBase(String file_name, ArrayList<User> list)
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
  private synchronized void saveHashSetsToDataBase(
      String file_name, HashMap<String, HashSet<String>> map) throws IOException {
    FileOutputStream fos = new FileOutputStream(file_name);
    try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {

      oos.writeObject(map);
      System.out.println("Inside semaphore saving indexes or references");
      System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> INDEX");
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

class Comunication {

  Queue<String> urls_queue;
  boolean check = false;


  synchronized Queue<String> web_crawler() {
    while (!check)
      try {
        wait();
      } catch (InterruptedException e) {
        System.out.println("interruptedException caught");
      }

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
