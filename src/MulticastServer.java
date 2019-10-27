import java.io.IOException;
import java.lang.reflect.Array;
import java.net.*;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;


//TCP CLASSES
// CLIENT ---------------------------------------------------------------------
class SpamIsAlive extends Thread {
    private String MULTICAST_ADDRESS;
    private String TCP_IP_ADDRESS;
    private int PORT;
    private int TCP_PORT;

    public SpamIsAlive(String MULTICAST_ADDRESS, String TCP_IP_ADDRESS, int PORT, int TCP_PORT) {
        this.MULTICAST_ADDRESS = MULTICAST_ADDRESS;
        this.TCP_IP_ADDRESS = TCP_IP_ADDRESS;
        this.TCP_PORT = TCP_PORT;
        this.PORT = PORT;
        this.start();
    }

    public void run() {
        while (true) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                System.out.println("Crashou o sleep");
            }
            //System.out.println("SPAMANDO MULTICASTS");
            MulticastSocket socket = null;
            String message = "type|MulticastIsAlive;myid|" + TCP_PORT + ";myIp|" + TCP_IP_ADDRESS;
            try {
                socket = new MulticastSocket(); // create socket without binding it (only for sending)
                byte[] buffer = message.getBytes();
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                socket.close();
            }
        }

    }
}

public class MulticastServer extends Thread {
    private String MULTICAST_ADDRESS;
    private int PORT;
    private String TCP_ADDRESS;
    protected BlockingQueue<String> urls_queue;
    protected QueueProcessor processQueue;
    protected ComunicationUrlsQueueRequestHandler com;
    private int myIdByTCP;
    private HashMap responsesTable;
    private FilesNamesObject filesManager;
    private HashMap<String, Date> arrayListMulticastOnline = new HashMap<>();
    private HashMap<String, HashSet<String>> separated_refs_to_send = new HashMap<>();


    public static void main(String[] args) {
        String propFileName = "config.properties";
        InputStream inputStream = MulticastServer.class.getClassLoader().getResourceAsStream(propFileName);
        Properties prop = new Properties();
        try {
            prop.load(inputStream);

        } catch (Exception e) {
            System.out.println("Cannot read properties File");
            return;
        }
        TCP_SERVER serverTCP = new TCP_SERVER(Integer.parseInt(prop.getProperty("TCP_PORT_ADDRESS")), prop.getProperty("TCP_IP_ADDRESS"));
        int portId = serverTCP.getServersocketPort();
        FilesNamesObject filesManager = serverTCP.getDatabase_object();
        System.out.println("Multicast Id -> " + portId);
        serverTCP.startTCPServer();
        MulticastServer server = new MulticastServer(portId, filesManager, prop, serverTCP.getCom(), serverTCP.getUrls_queue());
        server.start();

    }

    public int getMyIdByTCP() {
        return myIdByTCP;
    }

    public String getTCP_ADDRESS() {
        return TCP_ADDRESS;
    }

    public HashMap<String, HashSet<String>> getSeparated_refs_to_send() {
        return separated_refs_to_send;
    }

    public MulticastServer(int id, FilesNamesObject filesManager, Properties prop, ComunicationUrlsQueueRequestHandler com, BlockingQueue<String> urls_queue) {
        super("server-" + id);
        this.filesManager = filesManager;
        this.myIdByTCP = id;
        this.com = com;
        this.urls_queue = urls_queue;
        System.out.println("Esta aqui alguma coisa?" + urls_queue);
        MULTICAST_ADDRESS = prop.getProperty("MULTICAST_ADDRESS");
        PORT = Integer.parseInt(prop.getProperty("MULTICAST_PORT"));
        TCP_ADDRESS = prop.getProperty("TCP_IP_ADDRESS");
        String[] myMulticast = {"" + id, TCP_ADDRESS};
        new SpamIsAlive(MULTICAST_ADDRESS, TCP_ADDRESS, PORT, id);

    }

    public void run() {
        processQueue = new QueueProcessor(this, "", com, responsesTable, filesManager, arrayListMulticastOnline);
        processQueue.start();
        listenMulticast();
    }

    public int getMyPort() {
        return this.PORT;
    }

    public QueueProcessor getProcessQueue() {
        return processQueue;
    }



    public BlockingQueue<String> getUrlsQueue() {
        return this.urls_queue;
    }

    public String getMulticastAdress() {
        return this.MULTICAST_ADDRESS;
    }

    public void listenMulticast() {

        HashMap<String, String> responsesTable = new HashMap<>();
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
                if (!message.substring(0, 3).equals("id|")) {

                    HandleRequest handle = new HandleRequest(this, message, com, responsesTable, filesManager, arrayListMulticastOnline);
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
    public QueueProcessor(MulticastServer parent_thread, String message, ComunicationUrlsQueueRequestHandler com, HashMap responsesTable, FilesNamesObject filesManager, HashMap<String, Date> arrayListMulticastOnline) {
        super(parent_thread, message, com, responsesTable, filesManager, arrayListMulticastOnline);
        this.setName("Queue Processor-" + this.getId());
        System.out.println("URLS PROCESSING THREAD HERE!");
    }

    public void run() {
        HashMap[] indexes_and_references_to_send_or_add;
        while (true) {
            System.out.println("---------------------------------------> " + urls_queue);

            System.out.println("urls_queue  == " + urls_queue.size());


            if (urls_queue.size() != 0) {

                String url = null;
                try {
                    url = urls_queue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                indexes_and_references_to_send_or_add = this.crawl(url);

                if (indexes_and_references_to_send_or_add.length != 0) {
                    HashMap index = indexes_and_references_to_send_or_add[1];
                    HashMap references = indexes_and_references_to_send_or_add[0];
                    // send references to queue
                    Set references_set = references.keySet();

                    for (Object ref : references_set) {
                        if (!String.valueOf(ref).isEmpty())
                            try {
                                urls_queue.put(String.valueOf(ref));
                            } catch (InterruptedException e) {
                                System.out.println("A link did not enter the queue");
                            }
                    }
                    Iterator i = arrayListMulticastOnline.entrySet().iterator();
                    while (i.hasNext()) {
                        Map.Entry pair = (Map.Entry) i.next();
                        BlockingQueue<String> aux= new LinkedBlockingQueue<>();
                        int elementsInQueue= urls_queue.size();
                        for (int j = 0; j < elementsInQueue/(arrayListMulticastOnline.size()*2); j++) {
                            try {
                                aux.put(urls_queue.remove());
                            } catch (InterruptedException e){
                                System.out.println("nao adicionou");
                            }
                        }
                        String[] ip_port = ((String) pair.getKey()).split(":");
                        MessageByTCP messageToTCP = new MessageByTCP("UPDATE", aux,references,index);
                        new TCP_CLIENT(Integer.parseInt(ip_port[1]), messageToTCP, ip_port[0]);
                    }
                }
            }else {
                synchronized (urls_queue){
                    try {
                        urls_queue.wait();
                    } catch (InterruptedException e) {
                        System.out.println("Erro no wait");
                    }
                }
            }

        }
    }
}

class HandleRequest extends Thread {
    private String MULTICAST_ADDRESS;
    private String request;
    protected BlockingQueue<String> urls_queue;
    HashMap<String, String> responsesTable;
    ComunicationUrlsQueueRequestHandler com;
    FilesNamesObject filesManager;
    HashMap<String, Date> arrayListMulticastOnline;
    private String TCP_ADDRESS;
    private int myIdByTCP;
    private int myport;
    private HashMap<String, HashSet<String>> seperated_refs_to_send;


    public HandleRequest(MulticastServer parent_thread, String message, ComunicationUrlsQueueRequestHandler com, HashMap responsesTable, FilesNamesObject filesManager, HashMap<String, Date> arrayListMulticastOnline) {
        super();
        this.setName("Handler-" + this.getId());
        this.com = com;
        this.request = message;
        this.responsesTable = responsesTable;
        this.urls_queue = parent_thread.getUrlsQueue();
        this.filesManager = filesManager;
        this.arrayListMulticastOnline = arrayListMulticastOnline;
        this.seperated_refs_to_send = parent_thread.getSeparated_refs_to_send();
        this.TCP_ADDRESS = parent_thread.getTCP_ADDRESS();
        this.myIdByTCP = parent_thread.getMyIdByTCP();
        this.myport = parent_thread.getMyPort();
        this.MULTICAST_ADDRESS = parent_thread.getMulticastAdress();

    }

    public void run() {

        //System.out.println("I'm " + this.getName());
        String messageToRMI = null;
        try {
            messageToRMI = protocolReaderMulticastSide(this.request);
            //interpretRequest(this.request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (messageToRMI.equals(""))
            return;
        sendMulticastMessage(messageToRMI);

    }

    public void sendMulticastMessage(String message) {
        MulticastSocket socket = null;

        System.out.println(this.getName() + " ready...");
        try {
            socket = new MulticastSocket(); // create socket without binding it (only for sending)
            byte[] buffer = message.getBytes();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, this.myport);
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
        System.out.println("MENSAGEM - " + sms);
        HashMap<String, String> myDic = new HashMap<>();
        for (int i = 0; i < splitedsms.length; i++) {
            String[] splitedsplitedsms = splitedsms[i].split("\\|");
            myDic.put(splitedsplitedsms[0], splitedsplitedsms[1]);
        }
        if (myDic.get("id") != null) {
            return "";
        }
        if (myDic.get("ACK") != null) {
            responsesTable.remove(myDic.get("ACK"));
            return "";
        } else if (myDic.get("type").equals("MulticastIsAlive")) {
            if (!myDic.get("myid").equals("" + this.myIdByTCP)) {
                String newMulticast = myDic.get("myIp") + ":" + myDic.get("myid");

                if (arrayListMulticastOnline.put(newMulticast, new Date()) == null) {
                    // Join dos ficheiros deles
                    synchronized (filesManager) {
                        MessageByTCP messageToTCP = new MessageByTCP("NEW", this.filesManager.loadDataBase("REFERENCE"), this.filesManager.loadDataBase("INDEX"), this.filesManager.loadUsersFromDataBase());
                        new TCP_CLIENT(Integer.parseInt(myDic.get("myid")), messageToTCP, myDic.get("myIp"));
                    }

                }
            }
            Iterator i = arrayListMulticastOnline.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry pair = (Map.Entry) i.next();
                Date lastIsAlive = (Date) pair.getValue();
                System.out.println(pair.getKey() + " = " + pair.getValue());
                if ((new Date()).getTime() - lastIsAlive.getTime() > 13000)
                    i.remove();
            }

        } else if (responsesTable.get(myDic.get("id")) != null) {
            return (String) responsesTable.get(myDic.get("id"));
        }
        //System.out.println(myDic);
        if (myDic.get("idRMI") != null) {
            switch ((String) myDic.get("type")) {
                case "requestURLbyWord":
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals(myDic.get("user"))) {
                            users.get(i).addSearchToHistory(returnString("word", myDic));
                            bd = new DatabaseHandler(users, filesManager);
                            bd.start();
                        }
                    }
                    int numberWords = Integer.parseInt(myDic.get("word_count"));
                    String[] values = new String[numberWords];
                    for (int i = 0; i < numberWords; i++) {
                        values[i] = myDic.get("word_" + (i + 1));
                    }
                    String[] urls = (String[]) searchWords(values);
                    messageToRMI = "id|" + myDic.get("idRMI") + ";type|responseURLbyWord;url_count|" + urls.length + ";";
                    for (int i = 0; i < urls.length; i++) {
                        messageToRMI += "url_" + (i + 1) + "|" + urls[i] + ";";
                    }
                    responsesTable.put(myDic.get("idRMI"), messageToRMI);
                    return messageToRMI;

                case "requestURLbyRef":
                    HashMap<String, HashSet<String>> references = filesManager.loadDataBase("REFERENCE");
                    System.out.println(references);
                    System.out.println(myDic.get("URL"));
                    System.out.println(references.get(myDic.get("URL")));
                    HashSet<String> referencesFound = references.get(myDic.get("URL"));
                    if (referencesFound == null) {
                        messageToRMI = "id|" + myDic.get("idRMI") + ";type|responseURLbyRef;url_count|0;";
                    } else {
                        messageToRMI = "id|" + myDic.get("idRMI") + ";type|responseURLbyRef;url_count|" + referencesFound.size() + ";";
                        int k = 1;
                        for (String elem : referencesFound) {
                            messageToRMI += "url_" + k + "|" + elem + ";";
                            k++;
                        }
                    }
                    responsesTable.put(myDic.get("idRMI"), messageToRMI);
                    return messageToRMI;
                case "requestUSERhistory":
                    User myUser;
                    String message2send;
                    ArrayList<String> myUserHistory;
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals((String) myDic.get("user"))) {
                            myUser = users.get(i);
                            myUserHistory = myUser.getSearchToHistory();
                            message2send = "word_count|" + myUserHistory.size() + ";";
                            for (int j = 0; j < myUserHistory.size(); j++) {
                                message2send += "word_" + (j + 1) + "|" + myUserHistory.get(j) + ";";
                            }
                            responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responsetUSERhistory;" + message2send);
                            return "id|" + (String) myDic.get("idRMI") + ";type|responsetUSERhistory;" + message2send;
                        }
                    }
                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responsetUSERhistory;");
                    return "id|" + (String) myDic.get("idRMI") + ";type|responsetUSERhistory;";
                case "requestUSERLogin":
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals((String) myDic.get("user")) && users.get(i).getPassword().equals((String) myDic.get("pass"))) {
                            if (users.get(i).getIsAdmin()) {
                                if (users.get(i).getNotify()) {
                                    users.get(i).setNotify(false);
                                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged admin;Notify|true");
                                    return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged admin;Notify|true";

                                }
                                responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged admin;Notify|false");
                                return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged admin;Notify|nop";
                            }
                            responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged on");
                            return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged on;Notify|nop";
                        }
                    }
                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged off;");
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged off;";
                case "requestUSERRegist":
                    users = filesManager.loadUsersFromDataBase();
                    if (users.isEmpty()) {
                        User adminUser = new User((String) myDic.get("user"), (String) myDic.get("pass"));
                        adminUser.setIsAdmin();
                        users.add(adminUser);

                        bd = new DatabaseHandler(users, filesManager);
                        bd.start();
                        responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Admin");
                        return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Admin";
                    }
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals((String) myDic.get("user"))) {
                            responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Failled");
                            return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Failled";
                        }
                    }
                    users.add(new User((String) myDic.get("user"), (String) myDic.get("pass")));
                    bd = new DatabaseHandler(users, filesManager);
                    bd.start();
                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Success");
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Success";


                case "requestAllUSERSPrivileges":
                    users = filesManager.loadUsersFromDataBase();
                    String messageToSend = "id|" + (String) myDic.get("idRMI") + ";type|responseUSERSPrivileges;user_count|" + users.size() + ";";

                    for (int i = 0; i < users.size(); i++) {
                        // Um if para verificar e indicar se e Admin ou nao
                        messageToSend += "user_" + (i + 1) + "|" + users.get(i).getUsername() + " -> ";
                        if (users.get(i).getIsAdmin()) {
                            messageToSend += "Admin;";
                        } else {
                            messageToSend += "User;";
                        }
                    }
                    responsesTable.put(myDic.get("idRMI"), messageToSend);
                    return messageToSend;
                case "requestSetNotify":
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {

                        if (myDic.get("user").equals(users.get(i).getUsername())) {
                            users.get(i).setNotify(true);
                            bd = new DatabaseHandler(users, filesManager);
                            bd.start();
                        }
                        responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseSetNotify");
                        return "id|" + (String) myDic.get("idRMI") + ";type|responseSetNotify";
                    }
                case "requestChangeUSERPrivileges":
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (myDic.get("user").equals(users.get(i).getUsername())) {
                            if (users.get(i).getIsAdmin())
                            {
                                responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseChangeUSERPrivileges;" + "status|User is already an Admin");
                                return "id|" + (String) myDic.get("idRMI") + ";type|responseChangeUSERPrivileges;" + "status|User is already an Admin";
                            }

                            users.get(i).setIsAdmin();
                            users.get(i).setNotify(true);
                            bd = new DatabaseHandler(users, filesManager);
                            bd.start();
                            responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseChangeUSERPrivileges;" + "status|New admin added with success");
                            return "id|" + (String) myDic.get("idRMI") + ";type|responseChangeUSERPrivileges;" + "status|New admin added with success";
                        }

                    }
                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseChangeUSERPrivileges;" + "status|User not Found");
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseChangeUSERPrivileges;" + "status|User not Found";
                case "getMulticastList":
                    String listMulticast = ";";
                    Iterator i = arrayListMulticastOnline.entrySet().iterator();
                    while (i.hasNext()) {
                        Map.Entry pair = (Map.Entry) i.next();
                        listMulticast += ((String) pair.getKey()) + ";";
                        System.out.println();
                    }
                    listMulticast += this.TCP_ADDRESS + ":" + this.myIdByTCP + ";";
                    System.out.println(listMulticast);
                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + listMulticast);
                    return "id|" + (String) myDic.get("idRMI") + listMulticast;

                case "requestaddURLbyADMIN":
                    System.out.println("RECEBIDO -->" + myDic.get("MulticastId"));
                    System.out.println("O QUE TENHO -->" + this.TCP_ADDRESS + ":" + this.myIdByTCP);
                    if (myDic.get("MulticastId").equals(this.TCP_ADDRESS + ":" + this.myIdByTCP)) {
                        urls_queue.put(myDic.get("URL"));
                        synchronized (urls_queue){
                            urls_queue.notify();
                        }
                        responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseaddURLbyADMIN;" + "status|Success");
                        return "id|" + (String) myDic.get("idRMI") + ";type|responseaddURLbyADMIN;" + "status|Success";
                    }
                    return "";

                case "requestSYSinfo":

                    String message2Send = "activeMulticast_count|" + (arrayListMulticastOnline.size() + 1) + ";";
                    Iterator is = arrayListMulticastOnline.entrySet().iterator();
                    int ls = 1;
                    while (is.hasNext()) {
                        Map.Entry pair = (Map.Entry) is.next();
                        message2Send += "activeMulticast_" + ls + "|" + pair.getKey() + ";";
                        System.out.println();
                        ls++;
                    }
                    message2Send += "activeMulticast_" + ls + "|" + this.TCP_ADDRESS + ":" + this.myIdByTCP + ";";
                    List<String> top10ULR = getTenMostReferencedUrls();
                    message2Send += "important_pages_count|" + top10ULR.size() + ";";

                    int m = 1;
                    for (String elem : top10ULR) {
                        message2Send += "important_pages_" + m + "|" + elem + ";";
                        m++;
                    }


                    responsesTable.put(myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + ";type|responseSYSinfo;" + message2Send);
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseSYSinfo;" + message2Send;
                default:
                    messageToRMI = "";
            }
        }
        return messageToRMI;
    }

    public HashMap<String, HashSet<String>> divideRefs(HashMap new_from_merged, int len) {
        // change after merge, the array list no longer contains this server
        boolean extra;
        int size;
        HashMap<String, HashSet<String>> strings_to_send = new HashMap<>();
        ArrayList<String> aux_alive_multicast_array = new ArrayList<>(arrayListMulticastOnline.keySet());
        synchronized (new_from_merged) {
            size = new_from_merged.size();
            if (size % len != 0) {
                extra = true;
            } else {
                extra = false;
            }
            int i = 1;
            int server_index = 0;
            HashSet<String> aux_array = new HashSet<>();
            for (Object elem : new_from_merged.entrySet()) {
                Map.Entry aux_elem = (Map.Entry) elem;
                aux_array.add(String.valueOf(aux_elem.getKey()));

                if (extra) {
                    if ((i % (size / len) == 0 && i != size - 1) || i == size) {
                        strings_to_send.put(aux_alive_multicast_array.get(server_index), aux_array);
                        server_index++;
                        aux_array = new HashSet<>();
                    }
                } else {
                    if (i % (size / len) == 0) {
                        strings_to_send.put(aux_alive_multicast_array.get(server_index), aux_array);
                        server_index++;
                        aux_array = new HashSet<>();
                    }
                }
                i++;
            }
        }
        return strings_to_send;
    }

    private String returnString(String name, HashMap myDic) {
        String returnS = "";
        int arraySize = Integer.parseInt((String) myDic.get(name + "_count"));
        for (int i = 1; i < arraySize + 1; i++) {
            returnS += (String) myDic.get(name + "_" + i) + " ";
        }
        return returnS;
    }

    public List<String> getTenMostReferencedUrls() {
        HashMap<String, HashSet<String>> refereceURL;
        refereceURL = filesManager.loadDataBase("REFERENCE");


        Set all_entrys = refereceURL.entrySet();
        HashMap<String, Integer> new_map = new HashMap<>();
        for (Object entry : all_entrys) {
            Map.Entry actual_entry = (Map.Entry) entry;
            System.out.println("K --> " + actual_entry.getKey());
            System.out.println("V --> " + actual_entry.getValue());
            HashSet aux = (HashSet) actual_entry.getValue();
            new_map.put(String.valueOf(actual_entry.getKey()), aux.size());
        }
        System.out.println(new_map);
        HashMap<String, Integer> sorted = orderHashMapByIntegerValue(new_map);
        System.out.println("sorted=" + sorted);
        Set key_set = sorted.keySet();

        String[] array_to_send = (String[]) key_set.toArray(new String[0]);
        List<String> arrayList = Arrays.asList(array_to_send);

        if (arrayList.size() < 10)
            return arrayList;
        return arrayList.subList(0, 10);
    }

    public Object[] searchWords(String[] words) {
        HashMap<String, HashSet<String>> refereceURL = new HashMap<>();
        HashMap<String, HashSet<String>> indexURL = new HashMap<>();
        ArrayList<HashMap<String, Integer>> aux_array = new ArrayList<>();

        //System.out.println("reading from database");
        refereceURL = this.filesManager.loadDataBase("REFERENCE");
        indexURL = this.filesManager.loadDataBase("INDEX");
        //System.out.println("finished reading from database");


        String[] ordered_urls_to_send;
        // getting the urls that have the requested word
        System.out.println(words);
        for (String word : words) {
            System.out.println("--> " + word);
            System.out.println(indexURL);
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

                System.out.println("SORTED==" + sorted);
                aux_array.add(sorted);
            } else {
                aux_array.add(new HashMap<>());
            }
        }
        ordered_urls_to_send = checkRepeatedWords(aux_array);
        System.out.println("ORDERED ARRAY TO SEND= " + Arrays.toString(ordered_urls_to_send));
        return ordered_urls_to_send;
    }

    private HashMap<String, Integer> orderHashMapByIntegerValue(HashMap<String, Integer> urls_to_send) {
        return urls_to_send.entrySet().stream().
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

    public HashMap[] crawl(String url) {
        HashMap<String, HashSet<String>> refereceURL;
        HashMap<String, HashSet<String>> indexURL;
        HashMap[] database_changes_and_references_to_index = new HashMap[2];
        refereceURL = this.filesManager.loadDataBase("REFERENCE");
        indexURL = this.filesManager.loadDataBase("INDEX");

        //System.out.println("index url:" + indexURL);
        //System.out.println("reference url:" + refereceURL);
        //System.out.println("URL =" + url);

        try {

            String inputLink = url;

            // Attempt to connect and get the document
            Connection conn;
            Document doc;
            try {
                conn = (Connection) Jsoup.connect(inputLink);
                conn.timeout(5000);
                doc = conn.get();
            } catch (IllegalArgumentException e) {
                System.out.println("URL not found by Jsoup");
                return new HashMap[0];
            }

            Elements links = doc.select("a[href]");

            database_changes_and_references_to_index[0] = indexURLreferences(links, inputLink, refereceURL);
            System.out.println("DATABASE CHANGES===" + database_changes_and_references_to_index[0]);
            String text = doc.text();
            database_changes_and_references_to_index[1] = indexWords(text, inputLink, indexURL);


            DatabaseHandler handler_db = new DatabaseHandler(refereceURL, indexURL, filesManager);
            handler_db.start();
            // --------------------------------------------------------------------------HERE SAVE
            // DATABASE
            System.out.println("RECOLHEU TUDO O QUE TINHAAAAAA");

        } catch (org.jsoup.HttpStatusException | UnknownHostException | IllegalArgumentException e) {
            System.out.println("------------------------ Did not search for website ----------------------");
            return new HashMap[0];
        } catch (IOException e) {
            System.out.println("------------------------ Did not search for website -------------------------");
            //e.printStackTrace();
            return new HashMap[0];
        } catch (Exception e) {
            e.printStackTrace();
        }
        return database_changes_and_references_to_index;
    }

    private HashMap<String, HashSet<String>> indexURLreferences(Elements links, String inputWebsite, HashMap refereceURL) {
        HashMap<String, HashSet<String>> references_to_send = new HashMap<>();
        for (Element link : links) {
            String linkfound = link.attr("abs:href");
            if (linkfound.trim().length() != 0) {
                HashSet aux = (HashSet) refereceURL.get(linkfound);
                if (aux == null) {
                    aux = new HashSet<String>();
                }
                aux.add(inputWebsite);
                refereceURL.put(linkfound, aux);
                references_to_send.put(linkfound, aux);
            }
            // System.out.println(
            // "References to send-------------------------------------" + references_to_send);
        }
        return references_to_send;
    }

    private HashMap<String, HashSet<String>> indexWords(
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
                    HashSet aux = new HashSet();
                    if (indexURL.get(word) != null) {
                        aux.addAll((Collection) indexURL.get(word));
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
    private FilesNamesObject fileManager;

    // constructor for saving users
    public DatabaseHandler(ArrayList<User> users_list, FilesNamesObject fileManager) {
        super();
        this.setName("DatabaseHandler-" + this.getId());
        this.users_list = users_list;
        this.fileManager = fileManager;
    }

    // constructor for saving urls
    public DatabaseHandler(
            HashMap<String, HashSet<String>> refereceURL, HashMap<String, HashSet<String>> indexURL, FilesNamesObject fileManager) {
        super();
        this.setName("DatabaseHandler-" + this.getId());
        this.refereceURL = refereceURL;
        this.indexURL = indexURL;
        this.fileManager = fileManager;
    }

    // depending on the initialization of the class, the thread may save the users or the url HashMaps
    // so far
    public void run() {
        // waiting in conditional variable until save is
        if (this.users_list == null) {
            this.fileManager.saveHashSetsToDataBase("INDEX", this.indexURL);
            this.fileManager.saveHashSetsToDataBase("REFERENCE", this.refereceURL);
        } else {
            this.fileManager.saveUsersToDataBase(this.users_list);

        }
    }

}

class ComunicationUrlsQueueRequestHandler {

    BlockingQueue<String> urls_queue;
    boolean check = false;

    synchronized BlockingQueue<String> web_crawler() {
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

    synchronized void process_url(BlockingQueue<String> sharedObj) {
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

class ReferencesComparator implements Comparator<Map.Entry<String, Integer>> {
    public int compare(Map.Entry<String, Integer> s1, Map.Entry<String, Integer> s2) {
        if (s1.getValue().equals(s2.getValue()))
            return 0;
        else if (s1.getValue() < s2.getValue())
            return 1;
        else
            return -1;
    }
}