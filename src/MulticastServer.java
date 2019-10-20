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
private Queue<String> urls_queue = new ArrayList<>();

public static void main(String[] args) {
        MulticastServer server = new MulticastServer(args[0]);
        server.start();
}

public MulticastServer(String id) {
        super("server-" + id);
}

public void run() {
        listenMulticast();
        // have to check message parameter later
        //QueueProcessor processQueue = new QueueProcessor(this, "");
        //processQueue.start();
}
public Queue<String> getUrlsQueue(){
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
                                HandleRequest handle = new HandleRequest(this, message);
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
public QueueProcessor(Thread parent_thread, String message){
        super(parent_thread, message);
        this.setName("Queue Processor-"+ this.getId());

}
public void run(){
        while(! urls_queue.isEmpty()){

        }
}
}

class HandleRequest extends Thread {
static final String INDEXFILE = "indexURL.tmp";
static final String REFERENCEFILE = "referenceURL.tmp";
static final String USERS_FILE = "users.tmp";
private String MULTICAST_ADDRESS = "224.0.224.0";
private String request;
private Queue<String> urls_queue = null;

public HandleRequest(Thread parent_thread, String message) {
        super();
        this.setName("Handler-" + this.getId());
        this.request = message;
        this.urls_queue = parent_thread.getUrlsQueue();
        System.out.println(this);
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
        System.out.println(request);

        // future expansion necessary
        String[] requests_array = request.split("\\|");
        String type = requests_array[0];
        String value = requests_array[1];
        String[] to_send_multicast_and_tcp = new String[2];
        System.out.println(type);
        System.out.println(value);
        if (type.equals("URL")) {
                try {

                        to_send_multicast_and_tcp =crawl(value);
                        //integrate tcp and multicast code here;
                        // divide references ( index 0  of array ), send one portion to this server's queue, and send another portion via multicast to each of the other servers (doing that next! Ass:Paulo)
                        // send the changes made to the database, not the entire database itself, by tcp. (index 1 of array).
                } catch (IOException e) {
                        e.printStackTrace();
                }
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
        HashMap<String, HashSet<String> > refereceURL = new HashMap<>();
        HashMap<String, HashSet<String> > indexURL = new HashMap<>();
        loadDataBase(refereceURL, indexURL);
        // getting the urls that have the requested word
        HashSet word_urls = indexURL.get(word);
        // getting the number of references for each url that has the requested word
        HashMap<String, Integer> urls_to_send = new HashMap<>();
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
                .collect(toMap(Map.Entry::getKey, Map.Entry:: getValue, (e1, e2)->e2, HashMap:: new));
        // -------------------------------------------------------------------------------------------------------------------------
        System.out.println(sorted);
        // might have to save the user who made the request later;
        sendMulticastMessage(sorted.toString());
}

public String[] crawl(String url) throws IOException {
        HashMap<String, HashSet<String> > refereceURL = new HashMap<>();
        HashMap<String, HashSet<String> > indexURL = new HashMap<>();
        String[] database_changes_and_references_to_index = new String[2];
        loadDataBase(refereceURL, indexURL);
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
                database_changes_and_references_to_index[0] = indexURLreferences(links, inputLink, refereceURL);

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
        return to_send_tcp;
}

private static void runtMap(HashMap myhash, HashMap hashFile) {
        Iterator it = hashFile.entrySet().iterator();
        while (it.hasNext()) {
                Map.Entry pair = (Map.Entry)it.next();
                myhash.put(pair.getKey(), pair.getValue());
                it.remove(); // avoids a ConcurrentModificationException
        }
}

private static void runtMapUsers(ArrayList myList, ArrayList arrayFile) {
        Iterator it = arrayFile.iterator();
        while (it.hasNext()) {
                myList.add(it.next());
                it.remove(); // avoids a ConcurrentModificationException
        }
}

private static void loadDataBase(
        HashMap<String, HashSet<String> > refereceURL, HashMap<String, HashSet<String> > indexURL) {
        File fIndex = new File(INDEXFILE);
        File frefence = new File(REFERENCEFILE);
        FileInputStream fis;
        ObjectInputStream ois;
        try {
                fis = new FileInputStream(frefence);
                ois = new ObjectInputStream(fis);
                HashMap<String, HashSet<String> > refereceURLaux = (HashMap) ois.readObject();
                runtMap(refereceURL, refereceURLaux);
                fis = new FileInputStream(fIndex);
                ois = new ObjectInputStream(fis);
                HashMap<String, HashSet<String> > indexURLaux = (HashMap) ois.readObject();
                runtMap(indexURL, indexURLaux);

        } catch (FileNotFoundException ex) {
                System.out.println("Rip");
                refereceURL = new HashMap<>();
                indexURL = new HashMap<>();
        } catch (IOException e) {
                e.printStackTrace();
        } catch (ClassNotFoundException e) {
                e.printStackTrace();
        }
}

private static void loadUsersFromDataBase(ArrayList<User> users_list) {
        File f_users = new File(USERS_FILE);
        FileInputStream fis;
        ObjectInputStream ois;
        try {
                fis = new FileInputStream(f_users);
                ois = new ObjectInputStream(fis);
                ArrayList users_list_aux = (ArrayList) ois.readObject();
                runtMapUsers(users_list, users_list_aux);
        } catch (FileNotFoundException ex) {
                System.out.println("Rip");
        } catch (IOException e) {
                e.printStackTrace();
        } catch (ClassNotFoundException e) {
                e.printStackTrace();
        }
}

private static String indexURLreferences(Elements links, String inputWebsite, HashMap refereceURL) {
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
        HashMap<String,HashSet<String>> indexes_to_send = new HashMap<>();
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
                                indexes_to_send.put(word,aux);
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
private HashMap<String, HashSet<String> > refereceURL = null;
private HashMap<String, HashSet<String> > indexURL = null;
ArrayList<User> users_list = null;

// constructor for saving users
public DatabaseHandler(ArrayList<User> users_list) {
        super();
        this.setName("DatabaseHandler-" + this.getId());
        this.users_list = users_list;
}
// constructor for saving urls
public DatabaseHandler(
        HashMap<String, HashSet<String> > refereceURL, HashMap<String, HashSet<String> > indexURL) {
        super();
        this.setName("DatabaseHandler-" + this.getId());
        this.refereceURL = refereceURL;
        this.indexURL = indexURL;
}
// depending on the initialization of the class, the thread may save the users or the url HashMaps
// so far
public void run() {

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
// for now, only useful for saving users
private void saveArraysToDataBase(String file_name, ArrayList<User> list) throws IOException {
        FileOutputStream fos = new FileOutputStream(file_name);
        try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                synchronized (oos) {
                        oos.writeObject(users_list);
                        System.out.println("Inside Semaphore saving users");
                }
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
private void saveHashSetsToDataBase(String file_name, HashMap<String, HashSet<String> > map)
throws IOException {
        FileOutputStream fos = new FileOutputStream(file_name);
        try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                synchronized (oos) {
                        oos.writeObject(map);
                }
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
