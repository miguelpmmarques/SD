import java.io.IOException;
import java.net.*;
import RMISERVER.*;
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

// Class used to initialise the multicast server. Responsible for listening in on multicast requests
// from the RMI server and other active multicast servers
public class MulticastServer extends Thread {
    private String MULTICAST_ADDRESS; // IP address used for all multicast comunication
    private int PORT; // Multicast port
    private String TCP_ADDRESS; // Ip adress for tcp comunications
    protected BlockingQueue<String> urls_queue; // Queue of urls to process via jsoup
    protected QueueProcessor processQueue; // thread responsible for processing the urls queue
    private int myIdByTCP; // Ip port for tcp comunications
    private HashMap
            responsesTable; // table used for saving any requests, to make sure that, if a network error
    // occurs, and no acknowledge is received, the relevanta packahge is re-sent
    private FilesNamesObject
            filesManager; // shared object by all threads that need to execute database queries and such
    // operations.
    private HashMap<String, Date> arrayListMulticastOnline =
            new HashMap<>(); // array list of all multicasts currently active. Updated everytime an
    // isAlive packet is recieved

    // function used to initialize the server, getting the relevant data from the configuration file
    // and inicializing the tcp server and multicast server objects
    public static void main(String[] args) {
        String propFileName = "config.properties";
        InputStream inputStream =
                MulticastServer.class.getClassLoader().getResourceAsStream(propFileName);
        Properties prop = new Properties();
        try {
            prop.load(inputStream);

        } catch (Exception e) {
            System.out.println("Cannot read properties File");
            return;
        }
        // the tcp server is initialized first, for it is by using this server that the multicast id is
        // dicovered
        // The id for the multicast server corresponds to the concatenation of the ip address and port
        // of the tcp comunication (an unique combination)
        TCP_SERVER serverTCP =
                new TCP_SERVER(
                        Integer.parseInt(prop.getProperty("TCP_PORT_ADDRESS")),
                        prop.getProperty("TCP_IP_ADDRESS"));
        int portId = serverTCP.getServersocketPort();
        // getting the files manager object (explained above in the multicast server properties),
        // initialized in the tcp server class
        FilesNamesObject filesManager = serverTCP.getDatabase_object();
        System.out.println("Multicast Id -> " + portId);
        // starting the tcp server thread
        serverTCP.startTCPServer();
        // creating the multicast server object
        MulticastServer server =
                new MulticastServer(portId, filesManager, prop, serverTCP.getUrls_queue());
        // starting the thread
        server.start();
    }

    // multicast server constructor
    public MulticastServer(
            int id, FilesNamesObject filesManager, Properties prop, BlockingQueue<String> urls_queue) {
        // calling the super constructor to initialize this class
        super("server-" + id);
        this.filesManager = filesManager;
        this.myIdByTCP = id;
        this.urls_queue = urls_queue;
        System.out.println("Esta aqui alguma coisa?" + urls_queue);
        // these parameters are initialized by getting the relevant values from the configuration file
        MULTICAST_ADDRESS = prop.getProperty("MULTICAST_ADDRESS");
        PORT = Integer.parseInt(prop.getProperty("MULTICAST_PORT"));
        TCP_ADDRESS = prop.getProperty("TCP_IP_ADDRESS");
        // --------------------------------------------------------------------------------------------
        // Creating a SpamIsAlive object. This thread will be responsible for sending isAlive messages
        // at
        // regular intervals to other machines listening in on multicast messages
        new SpamIsAlive(MULTICAST_ADDRESS, TCP_ADDRESS, PORT, id);
    }

    public void run() {
        // creating the queue processor object and starting the thread
        processQueue =
                new QueueProcessor(this, "", responsesTable, filesManager, arrayListMulticastOnline);
        processQueue.start();
        // begin listening for multicast messages
        listenMulticast();
    }

    public void listenMulticast() {

        responsesTable = new HashMap<>();
        MulticastSocket socket = null;
        System.out.println(this.getName() + " running...");
        try {
            // creating the socket for this multicast server and  binding it
            socket = new MulticastSocket(PORT);
            // getting the multicast group by using its ip
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            // joining the multicast group to receive the multicast messages
            socket.joinGroup(group);
            while (true) {
                // defining a large buffer, to make sure the requests are not chopped off
                byte[] buffer = new byte[8000];
                // creating an udp datagram to populate with the data from the received packet
                DatagramPacket received_packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(received_packet);
                // parsing the received datagram into a string
                String message = new String(received_packet.getData(), 0, received_packet.getLength());
                // The id parameter in a protocol correlates to messages beeing sent to the RMI server via
                // multicast.
                // In that case, the multicast server does not need to process this request
                if (!message.substring(0, 3).equals("id|")) {
                    // creating the thread responsible for processing the request. Like a connection thread,
                    // in a way
                    HandleRequest handle =
                            new HandleRequest(
                                    this, message, responsesTable, filesManager, arrayListMulticastOnline);
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

    // getter methods
    public int getMyIdByTCP() {
        return myIdByTCP;
    }

    public String getTCP_ADDRESS() {
        return TCP_ADDRESS;
    }

    public int getMyPort() {
        return this.PORT;
    }

    public BlockingQueue<String> getUrlsQueue() {
        return this.urls_queue;
    }

    public String getMulticastAdress() {
        return this.MULTICAST_ADDRESS;
    }
    // ---------------------------------------------------------------------------

}

// thread responsible for web crawling.
// inherits from HandleRequest class, for it needs many of the same properties and methods, namely
// the database object
// FilesNamesObject, the urls queue, the  array list of online multicast servers, and many functions
// like the ones used
// to seach for words and indexation of urls, amoung many others
class QueueProcessor extends HandleRequest {
    public QueueProcessor(
            MulticastServer parent_thread,
            String message,
            HashMap responsesTable,
            FilesNamesObject filesManager,
            HashMap<String, Date> arrayListMulticastOnline) {
        super(parent_thread, message, responsesTable, filesManager, arrayListMulticastOnline);
        this.setName("Queue Processor-" + this.getId());
        System.out.println("URLS PROCESSING THREAD HERE!");
    }

    public void run() {

        HashMap[]
                indexes_and_references_to_send_or_add; // Array list of both hashmaps to send via tcp for
        // database synchronization, the hashmap of words and
        // relevant urls, and the other references found on
        // the pages that have already been crawled to
        while (true) {
            System.out.println("URL queue size  == " + urls_queue.size());
            // if the Blocking queue of urls to crawl to is not empty, this thread will process the next
            // urls in the queue
            if (!urls_queue.isEmpty()) {

                String url = null;
                // taking the url to process from the blocking queue. No synchronized needed because its a
                // blocking queue
                try {
                    url = urls_queue.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                indexes_and_references_to_send_or_add = this.crawl(url);

                if (indexes_and_references_to_send_or_add.length != 0) {
                    HashMap index = indexes_and_references_to_send_or_add[1]; // the hashmap of indexed words
                    HashMap references =
                            indexes_and_references_to_send_or_add[
                                    0]; // the hashmap of references to distribute over the other multicast servers
                    HashMap descriptionTitle = indexes_and_references_to_send_or_add[2];
                    Set references_set = references.keySet();
                    // adding all found references to queue ---- later a portion of these will be passed by
                    // tcp to the other server
                    for (Object ref : references_set) {
                        if (!String.valueOf(ref).isEmpty())
                            try {
                                urls_queue.put(String.valueOf(ref));
                            } catch (InterruptedException e) {
                                System.out.println("A link did not enter the queue");
                            }
                    }
                    Iterator i = arrayListMulticastOnline.entrySet().iterator();
                    // taking some of the links out of the urls queue and preparing them to send via tcp
                    while (i.hasNext()) {
                        Map.Entry pair = (Map.Entry) i.next();
                        BlockingQueue<String> aux = new LinkedBlockingQueue<>();
                        int elementsInQueue = urls_queue.size();
                        for (int j = 0; j < elementsInQueue / (arrayListMulticastOnline.size() * 2); j++) {
                            try {
                                aux.put(urls_queue.remove());
                            } catch (InterruptedException e) {
                                System.out.println("nao adicionou");
                            }
                        }
                        String[] ip_port = ((String) pair.getKey()).split(":");
                        // sending the database changes and a portion of the references to the other multicast
                        // servers
                        MessageByTCP messageToTCP = new MessageByTCP("UPDATE", aux, references, index, descriptionTitle);
                        new TCP_CLIENT(Integer.parseInt(ip_port[1]), messageToTCP, ip_port[0]);
                    }
                }
            } else {
                // if the urls queue is empty at first, the thread must wait for notification, avoiding
                // active waiting
                synchronized (urls_queue) {
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
    //  properties explained above in the multicast server class
    private String MULTICAST_ADDRESS;
    private String request;
    protected BlockingQueue<String> urls_queue;
    HashMap<String, String> responsesTable;
    FilesNamesObject filesManager;
    HashMap<String, Date> arrayListMulticastOnline;
    private String TCP_ADDRESS;
    private int myIdByTCP;
    private int myport; // multicast port
    // -----------------------------------------------------------

    public HandleRequest(
            MulticastServer parent_thread,
            String message,
            HashMap responsesTable,
            FilesNamesObject filesManager,
            HashMap<String, Date> arrayListMulticastOnline) {
        super();
        this.setName("Handler-" + this.getId());
        this.request = message;
        this.responsesTable = responsesTable;
        this.urls_queue = parent_thread.getUrlsQueue();
        this.filesManager = filesManager;
        this.arrayListMulticastOnline = arrayListMulticastOnline;
        this.TCP_ADDRESS = parent_thread.getTCP_ADDRESS();
        this.myIdByTCP = parent_thread.getMyIdByTCP();
        this.myport = parent_thread.getMyPort();
        this.MULTICAST_ADDRESS = parent_thread.getMulticastAdress();
    }

    public void run() {

        // System.out.println("I'm " + this.getName());
        String messageToRMI = null;
        try {
            // function necessary for interpreting the received multicast messages
            messageToRMI = protocolReaderMulticastSide(this.request);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (messageToRMI.equals("")) return;
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

    /*
     * Handler que trata das mensagens recebidas, resumidamente, traduz o protocolo
     * */
    private String protocolReaderMulticastSide(String sms) throws InterruptedException {
        String[] splitedsms = sms.split("\\;");
        DatabaseHandler bd;
        ArrayList<User> users;
        String messageToRMI = "";
        System.out.println("MENSAGEM - " + sms);
        HashMap<String, String> myDic = new HashMap<>();
        HashMap<String, ArrayList<String>> descriptionTitle = this.filesManager.loadDataBaseDescriptions();
        for (int i = 0; i < splitedsms.length; i++) {
            String[] splitedsplitedsms = splitedsms[i].split("\\|");
            myDic.put(splitedsplitedsms[0], splitedsplitedsms[1]);
        }
        //Caso receba um pacote que seja suposto ser enviado para o RMI, ignora-o
        if (myDic.get("id") != null) {
            return "";
        }
        if (myDic.get("ACK") != null) {
          // Quando recebe um ack, significa que a sua resposta chegou e que pode elimina-la da tabela de mensagens
            synchronized (responsesTable) {
                responsesTable.remove(myDic.get("ACK"));
            }
            return "";
        } else if (myDic.get("type").equals("MulticastIsAlive")) {
            /*
            * Recebe o spam de isAlive, se o multicast ainda nao estiver a sua lista de servidores ativos
            * este interpreta que ele e novo no sistema e faz merge da base de dados dos mesmos via TCP
            * */
            if (!myDic.get("myid").equals("" + this.myIdByTCP)) {
                String newMulticast = myDic.get("myIp") + ":" + myDic.get("myid");

                if (arrayListMulticastOnline.put(newMulticast, new Date()) == null) {
                    // Join dos ficheiros deles
                    synchronized (filesManager) {
                        MessageByTCP messageToTCP =
                                new MessageByTCP(
                                        "NEW",
                                        this.filesManager.loadDataBase("REFERENCE"),
                                        this.filesManager.loadDataBase("INDEX"),
                                        descriptionTitle,
                                        this.filesManager.loadUsersFromDataBase());
                        new TCP_CLIENT(Integer.parseInt(myDic.get("myid")), messageToTCP, myDic.get("myIp"));
                    }
                }
            }
            /*
            * Faz a verificacao se o servidor Multicast esta na sua lista de servidores ativos
            * Se estiver atualiza a data em que recebeu o isAlive
            * Este tambem verifica a data em que recebeu os restantes isAlives, e caso um servidor nao mande um IsAlive num
            * periodo de 10 segundos, este interpreta que este ja nao se encontra ativo
            * */
            Iterator i = arrayListMulticastOnline.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry pair = (Map.Entry) i.next();
                Date lastIsAlive = (Date) pair.getValue();
                System.out.println(pair.getKey() + " = " + pair.getValue());
                if ((new Date()).getTime() - lastIsAlive.getTime() > 10000) i.remove();
            }

        } else if (responsesTable.get(myDic.get("idRMI")) != null) {
            /*
            * Assim que recebe um pedido pelo RMI, verifica primeiro se ja enviou a resposta a esse pedido anteriormente, e se ainda se
            * nao recebeu a confirmacao que este foi recebido
            *
            * Caso isso se verifique, simplesmente reenvia a resposta ja anteriormente criada
            * */
            return (String) responsesTable.get(myDic.get("idRMI"));
        }
        if (myDic.get("idRMI") != null) {
            /*
             *  ------------------------------------------------------------------ PROTOCOLO UDP ------------------------------------------------------------------
             * */
            switch ((String) myDic.get("type")) {
                case "requestURLbyWord":
                    /*
                    * Responde com os urls encontrados no indice invertido e adiciona os termos ao historico do utilizador
                    * */
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals(myDic.get("user").replace("+"," "))) {
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
                    messageToRMI =
                            "id|" + myDic.get("idRMI") + ";type|responseURLbyWord;url_count|" + urls.length + ";";
                    for (int i = 0; i < urls.length; i++) {
                        ArrayList<String> description_title = descriptionTitle.get(urls[i]);
                        String description = "NO DESCRIPTION AVAILABLE";
                        String title = "NO TITLE AVAILABLE";
                        if (description_title!=null){
                            description =(String)description_title.toArray()[1];
                            description = description.replace(";", ",");
                            title = (String)description_title.toArray()[0];
                            title = title.replace(";", ",");
                        }
                        messageToRMI += "url_" + (i + 1) + "|" + urls[i] + "*oo#&"+title+"*oo#&"+description+";";
                    }
                    synchronized (responsesTable) {
                        responsesTable.put(myDic.get("idRMI"), messageToRMI);
                    }

                    return messageToRMI;

                    case "requestURLbyRef":
                    /*
                     * Responde com os urls que referenciam o URL que o utilizador submeteu
                     * */
                    HashMap<String, HashSet<String>> references = filesManager.loadDataBase("REFERENCE");
                    System.out.println(references);
                    System.out.println(myDic.get("URL"));
                    System.out.println(references.get(myDic.get("URL")));
                    HashSet<String> referencesFound = references.get(myDic.get("URL"));
                    if (referencesFound == null) {
                        messageToRMI = "id|" + myDic.get("idRMI") + ";type|responseURLbyRef;url_count|0;";
                    } else {
                        messageToRMI =
                                "id|"
                                        + myDic.get("idRMI")
                                        + ";type|responseURLbyRef;url_count|"
                                        + referencesFound.size()
                                        + ";";
                        int k = 1;
                        for (String elem : referencesFound) {

                            ArrayList<String> description_title = descriptionTitle.get(elem);
                            String description = "NO DESCRIPTION AVAILABLE";
                            String title = "NO TITLE AVAILABLE";
                            if (description_title!=null){
                                System.out.println("DESCRIPTION_TITLE==="+description_title);
                                System.out.println("DESCRIPTION_TITLE_ARRAY==="+ Arrays.toString(description_title.toArray()));
                                description =(String)description_title.toArray()[1];
                                description = description.replace(";", ",");
                                title = (String)description_title.toArray()[0];
                                title = title.replace(";", ",");
                            }
                            messageToRMI += "url_" + k + "|" + elem + "*oo#&"+title+"*oo#&"+description+";";
                            System.out.println("MESSAGE TO RMI BT REF===="+ messageToRMI);
                            k++;
                        }
                    }
                    synchronized (responsesTable) {
                        responsesTable.put(myDic.get("idRMI"), messageToRMI);
                    }

                    return messageToRMI;
                case "requestUSERhistory":
                    /*
                    * Vai a base de dados do utilizador e vai percorrer as pesquisas passadas
                    * que este fez no passado e reenvia para o servidor RMI
                    * */
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
                            synchronized (responsesTable) {
                                responsesTable.put(
                                        myDic.get("idRMI"),
                                        "id|"
                                                + (String) myDic.get("idRMI")
                                                + ";type|responsetUSERhistory;"
                                                + message2send);
                            }
                            return "id|"
                                    + (String) myDic.get("idRMI")
                                    + ";type|responsetUSERhistory;"
                                    + message2send;
                        }
                    }
                    synchronized (responsesTable) {
                        responsesTable.put(
                                myDic.get("idRMI"),
                                "id|" + (String) myDic.get("idRMI") + ";type|responsetUSERhistory;");
                    }
                    return "id|" + (String) myDic.get("idRMI") + ";type|responsetUSERhistory;";
                case "requestUSERLogin":
                    /*
                    * Faz a confirmacao do username e da password inserida pelo end-user
                    * e responde consoante tenha ou nao encontrado esse utilizador na base de dados do utilizador
                    * */
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals((String) myDic.get("user"))
                                && users.get(i).getPassword().equals((String) myDic.get("pass"))) {
                            if (users.get(i).getIsAdmin()) {
                                if (users.get(i).getNotify()) {
                                    users.get(i).setNotify(false);
                                    synchronized (responsesTable) {
                                        responsesTable.put(
                                                myDic.get("idRMI"),
                                                "id|"
                                                        + (String) myDic.get("idRMI")
                                                        + ";type|responseUSERLogin;status|logged admin;Notify|true");
                                    }
                                    return "id|"
                                            + (String) myDic.get("idRMI")
                                            + ";type|responseUSERLogin;status|logged admin;Notify|true";
                                }
                                synchronized (responsesTable) {
                                    responsesTable.put(
                                            myDic.get("idRMI"),
                                            "id|"
                                                    + (String) myDic.get("idRMI")
                                                    + ";type|responseUSERLogin;status|logged admin;Notify|false");
                                }
                                return "id|"
                                        + (String) myDic.get("idRMI")
                                        + ";type|responseUSERLogin;status|logged admin;Notify|nop";
                            }
                            synchronized (responsesTable) {
                                responsesTable.put(
                                        myDic.get("idRMI"),
                                        "id|"
                                                + (String) myDic.get("idRMI")
                                                + ";type|responseUSERLogin;status|logged on");
                            }
                            return "id|"
                                    + (String) myDic.get("idRMI")
                                    + ";type|responseUSERLogin;status|logged on;Notify|nop";
                        }
                    }
                    synchronized (responsesTable) {
                        responsesTable.put(
                                myDic.get("idRMI"),
                                "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged off;");
                    }
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERLogin;status|logged off;";
                case "requestUSERRegist":
                    /*
                    * Muito semelhante ao anterior, uma vez que percorre a base de dados a procura de um utilizador um o mesmo user name
                    * caso nao encontre, adiciona-o a base de dados
                    * se o encontra informa o servidor RMI para informar o end-user que ja existe um utilizador com esse username
                    *
                    * Confirma tambem se a base de dados esta vazia, uma vez que se isto se verificar, significa que esse utilizador
                    * ganha automaticamente privilegios de administrador
                    * */
                    users = filesManager.loadUsersFromDataBase();
                    if (users.isEmpty()) {
                        User adminUser = new User((String) myDic.get("user"), (String) myDic.get("pass"));
                        adminUser.setIsAdmin();
                        users.add(adminUser);

                        bd = new DatabaseHandler(users, filesManager);
                        bd.start();
                        synchronized (responsesTable) {
                            responsesTable.put(
                                    myDic.get("idRMI"),
                                    "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Admin");
                        }
                        return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Admin";
                    }
                    for (int i = 0; i < users.size(); i++) {
                        if (users.get(i).getUsername().equals((String) myDic.get("user"))) {
                            synchronized (responsesTable) {
                                responsesTable.put(
                                        myDic.get("idRMI"),
                                        "id|"
                                                + (String) myDic.get("idRMI")
                                                + ";type|responseUSERRegist;status|Failled");
                            }
                            return "id|"
                                    + (String) myDic.get("idRMI")
                                    + ";type|responseUSERRegist;status|Failled";
                        }
                    }
                    users.add(new User((String) myDic.get("user"), (String) myDic.get("pass")));
                    bd = new DatabaseHandler(users, filesManager);
                    bd.start();
                    synchronized (responsesTable) {
                        responsesTable.put(
                                myDic.get("idRMI"),
                                "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Success");
                    }
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseUSERRegist;status|Success";

                case "requestAllUSERSPrivileges":
                    /*
                    * Responde ao servidor RMI com todos os utilizador registados e com as suas prioridades (Administrador ou nao administrador)
                    * */
                    users = filesManager.loadUsersFromDataBase();
                    String messageToSend =
                            "id|"
                                    + (String) myDic.get("idRMI")
                                    + ";type|responseUSERSPrivileges;user_count|"
                                    + users.size()
                                    + ";";

                    for (int i = 0; i < users.size(); i++) {
                        // Um if para verificar e indicar se e Admin ou nao
                        messageToSend += "user_" + (i + 1) + "|" + users.get(i).getUsername() + " -> ";
                        if (users.get(i).getIsAdmin()) {
                            messageToSend += "Admin;";
                        } else {
                            messageToSend += "User;";
                        }
                    }
                    synchronized (responsesTable) {
                        responsesTable.put(myDic.get("idRMI"), messageToSend);
                    }
                    return messageToSend;
                case "requestSetNotify":
                    /*
                    * Esta parte do protocolo e utilizada caso se de privilegios de admin a um utilizador
                    * aqui informamos o sevidor multicast se e necessario notificar o utilizador quando este ficar online
                    * ou se este
                    * */
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {

                        if (myDic.get("user").equals(users.get(i).getUsername())) {
                            users.get(i).setNotify(true);
                            bd = new DatabaseHandler(users, filesManager);
                            bd.start();
                        }
                        synchronized (responsesTable) {
                            responsesTable.put(
                                    myDic.get("idRMI"),
                                    "id|" + (String) myDic.get("idRMI") + ";type|responseSetNotify");
                        }
                        return "id|" + (String) myDic.get("idRMI") + ";type|responseSetNotify";
                    }
                case "requestChangeUSERPrivileges":
                    /*
                    * Verifica se o user que tornaram admin ja nao e admin
                    * e caso na seja verifica na base de dados os seus novos privilegios
                    * */
                    users = filesManager.loadUsersFromDataBase();
                    for (int i = 0; i < users.size(); i++) {
                        if (myDic.get("user").equals(users.get(i).getUsername())) {
                            if (users.get(i).getIsAdmin()) {
                                synchronized (responsesTable) {
                                    responsesTable.put(
                                            myDic.get("idRMI"),
                                            "id|"
                                                    + (String) myDic.get("idRMI")
                                                    + ";type|responseChangeUSERPrivileges;"
                                                    + "status|User is already an Admin");
                                }
                                return "id|"
                                        + (String) myDic.get("idRMI")
                                        + ";type|responseChangeUSERPrivileges;"
                                        + "status|User is already an Admin";
                            }

                            users.get(i).setIsAdmin();
                            users.get(i).setNotify(true);
                            bd = new DatabaseHandler(users, filesManager);
                            bd.start();
                            synchronized (responsesTable) {
                                responsesTable.put(
                                        myDic.get("idRMI"),
                                        "id|"
                                                + (String) myDic.get("idRMI")
                                                + ";type|responseChangeUSERPrivileges;"
                                                + "status|New admin added with success");
                            }
                            return "id|"
                                    + (String) myDic.get("idRMI")
                                    + ";type|responseChangeUSERPrivileges;"
                                    + "status|New admin added with success";
                        }
                    }
                    synchronized (responsesTable) {
                        responsesTable.put(
                                myDic.get("idRMI"),
                                "id|"
                                        + (String) myDic.get("idRMI")
                                        + ";type|responseChangeUSERPrivileges;"
                                        + "status|User not Found");
                    }
                    return "id|"
                            + (String) myDic.get("idRMI")
                            + ";type|responseChangeUSERPrivileges;"
                            + "status|User not Found";

                case "getMulticastList":
                    /*
                    * Devolve uma mensagem com a lista de todos os Servidores multicasts
                    * incluindo ele mesmo
                    * */
                    String listMulticast = ";";
                    Iterator i = arrayListMulticastOnline.entrySet().iterator();
                    while (i.hasNext()) {
                        Map.Entry pair = (Map.Entry) i.next();
                        listMulticast += ((String) pair.getKey()) + ";";
                        System.out.println();
                    }
                    listMulticast += this.TCP_ADDRESS + ":" + this.myIdByTCP + ";";
                    System.out.println(listMulticast);
                    synchronized (responsesTable) {
                        responsesTable.put(
                                myDic.get("idRMI"), "id|" + (String) myDic.get("idRMI") + listMulticast);
                    }
                    return "id|" + (String) myDic.get("idRMI") + listMulticast;

                case "requestaddURLbyADMIN":
                    /*
                    * Indexacao de URL
                    * Este pacote vem com um campo que tem a informacao de que servidor foi escolhido
                    * Dai esta primeiro verificacao, deste modo asseguramos que um URL e indexado por um
                    * e so um servidor.
                    * */
                    if (myDic.get("MulticastId").equals(this.TCP_ADDRESS + ":" + this.myIdByTCP)) {
                        System.out.println("INDEXING");
                        urls_queue.put(myDic.get("URL"));
                        synchronized (urls_queue) {
                            urls_queue.notify();
                        }
                        synchronized (responsesTable) {
                            responsesTable.put(
                                    myDic.get("idRMI"),
                                    "id|"
                                            + (String) myDic.get("idRMI")
                                            + ";type|responseaddURLbyADMIN;"
                                            + "status|Success");
                        }
                        return "id|"
                                + (String) myDic.get("idRMI")
                                + ";type|responseaddURLbyADMIN;"
                                + "status|Success";
                    }
                    return "";

                case "requestSYSinfo":
                    /*
                    * Envia para o servidor RMI a informacao de:
                    * os servidores Multicast Ativos
                    * as 10 paginas com mais referencias noutras paginas
                    * o top 10 de termos mais pesquisados por todos os utilizadores registados
                    * */
                    String message2Send =
                            "activeMulticast_count|" + (arrayListMulticastOnline.size() + 1) + ";";
                    Iterator is = arrayListMulticastOnline.entrySet().iterator();
                    int ls = 1;
                    while (is.hasNext()) {
                        Map.Entry pair = (Map.Entry) is.next();
                        message2Send += "activeMulticast_" + ls + "|" + pair.getKey() + ";";
                        System.out.println();
                        ls++;
                    }
                    message2Send +=
                            "activeMulticast_" + ls + "|" + this.TCP_ADDRESS + ":" + this.myIdByTCP + ";";
                    List<String> top10ULR = getTenMostReferencedUrls();
                    message2Send += "important_pages_count|" + top10ULR.size() + ";";
                    ls = 1;
                    for (String elem : top10ULR) {
                        message2Send += "important_pages_" + ls + "|" + elem + ";";
                        ls++;
                    }
                    users = filesManager.loadUsersFromDataBase();
                    HashMap<String, Integer> topSearches = new HashMap<>();
                    for (User elem : users) {
                        for (String seach : elem.getSearchToHistory()) {
                            seach = seach.split("-")[0];
                            if (topSearches.get(seach) != null) {
                                topSearches.computeIfPresent(seach, (k, v) -> v + 1);
                            } else {
                                topSearches.put(seach, 1);
                            }
                        }
                    }

                    if (topSearches.size() > 10) {
                        message2Send += "top_search_count|10;";
                    } else {
                        message2Send += "top_search_count|" + topSearches.size() + ";";
                    }
                    topSearches = orderHashMapByIntegerValue(topSearches);
                    ls = 1;
                    for (Map.Entry pair : topSearches.entrySet()) {
                        System.out.println(pair.getKey());
                        message2Send += "top_search_" + ls + "|" + pair.getKey() + ";";
                        if (ls == 10) {
                            break;
                        }
                        ls++;
                    }

                    System.out.println("TOP SORTED -------->" + topSearches);
                    synchronized (responsesTable) {
                        responsesTable.put(
                                myDic.get("idRMI"),
                                "id|" + (String) myDic.get("idRMI") + ";type|responseSYSinfo;" + message2Send);
                    }
                    return "id|" + (String) myDic.get("idRMI") + ";type|responseSYSinfo;" + message2Send;
                default:
                    messageToRMI = "";
            }
        }
        return messageToRMI;
    }

    // method to concatenate searched words by users and putting them in the user's history
    private String returnString(String name, HashMap myDic) {
        String returnS = "";
        int arraySize = Integer.parseInt((String) myDic.get(name + "_count"));
        for (int i = 1; i < arraySize + 1; i++) {
            returnS += (String) myDic.get(name + "_" + i) + " ";
        }
        Date date = new Date();

        returnS = returnS+" - ["+date.toString()+"]";
        return returnS;
    }

    // method to get the ten most referenced urls in our database
    public List<String> getTenMostReferencedUrls() {
        HashMap<String, HashSet<String>> refereceURL;
        refereceURL = filesManager.loadDataBase("REFERENCE");
        Set all_entrys = refereceURL.entrySet();
        HashMap<String, Integer> new_map = new HashMap<>();
        // creating a new hashmap with the reference as a key and the value beeing the number of
        // references
        for (Object entry : all_entrys) {
            Map.Entry actual_entry = (Map.Entry) entry;
            HashSet aux = (HashSet) actual_entry.getValue();
            new_map.put(String.valueOf(actual_entry.getKey()), aux.size());
        }
        // sorting the created hashmap by number of references
        HashMap<String, Integer> sorted = orderHashMapByIntegerValue(new_map);
        Set key_set = sorted.keySet();
        // converting the ordered references (just the keys) to an ArrayList
        String[] array_to_send = (String[]) key_set.toArray(new String[0]);
        List<String> arrayList = Arrays.asList(array_to_send);
        // returning the fisrt ten elemnets of the array (or the existing ones if the array doesn't yet
        // have ten elements)
        if (arrayList.size() < 10) return arrayList;
        return arrayList.subList(0, 10);
    }

    // function used to search the words requested by the user in our database
    public Object[] searchWords(String[] words) {
        HashMap<String, HashSet<String>> refereceURL;
        HashMap<String, HashSet<String>> indexURL;
        HashMap<String, ArrayList<String>> descriptionTitle;
        ArrayList<HashMap<String, Integer>> aux_array = new ArrayList<>();

        // loading the database
        refereceURL = this.filesManager.loadDataBase("REFERENCE");
        indexURL = this.filesManager.loadDataBase("INDEX");

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
                // ordering the subsequent hashmap ny number of references
                HashMap<String, Integer> sorted = orderHashMapByIntegerValue(urls_to_send);

                // adding the hashmap to an arraylist ands then merging them
                aux_array.add(sorted);
            } else {
                aux_array.add(new HashMap<>());
            }
        }
        // filtering the hashsets to make sure only the common results among all sets are sent
        ordered_urls_to_send = checkRepeatedWords(aux_array);
        return ordered_urls_to_send;
    }

    // functioned to sort hashmaps in the form HashMap<String, Integer> by the value
    private HashMap<String, Integer> orderHashMapByIntegerValue(
            HashMap<String, Integer> urls_to_send) {
        return urls_to_send.entrySet().stream()
                .sorted(new ReferencesComparator())
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    // merging several hashmaps in an arraylist but retaining oly their common entries
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

    // function used for searching the given url with jsoup and adding all of those words and links to
    // the database
    public HashMap[] crawl(String url) {
        HashMap<String, HashSet<String>> refereceURL;
        HashMap<String, HashSet<String>> indexURL;
        HashMap<String, ArrayList<String>> descriptionTitle;
        HashMap[] database_changes_and_references_to_index = new HashMap[3];
        refereceURL = this.filesManager.loadDataBase("REFERENCE");
        indexURL = this.filesManager.loadDataBase("INDEX");
        descriptionTitle = this.filesManager.loadDataBaseDescriptions();
        try {

            String inputLink = url;

            Connection conn;
            Document doc;
            String title="NO TITLE AVAILABLE";
            String description="NO DESCRIPTION AVAILABLE";
            try {
                conn = (Connection) Jsoup.connect(inputLink);
                conn.timeout(10000);
                doc = conn.get();
                title = doc.title();
                if (title.trim().equals("")){
                    title="NO TITLE AVAILABLE";
                }
                try{
                    description = doc.select("meta[name=description]").get(0).attr("content");

                }catch(java.lang.IndexOutOfBoundsException e){
                    description = "NO DESCRIPTION AVAILABLE";
                }
            } catch (IllegalArgumentException e) {
                System.out.println("URL not found by Jsoup");
                return new HashMap[0];
            }

            Elements links = doc.select("a[href]");

            database_changes_and_references_to_index[0] =
                    indexURLreferences(links, inputLink, refereceURL);
            String text = doc.text();
            database_changes_and_references_to_index[1] = indexWords(text, inputLink, indexURL);
            database_changes_and_references_to_index[2] = indexDescriptionsAndTitles(description, title, inputLink, descriptionTitle);

            System.out.println("------DESCRIPTION------");
            System.out.println(database_changes_and_references_to_index[2]);
            System.out.println("ULR SAVED ---->" + url);
            DatabaseHandler handler_db = new DatabaseHandler(refereceURL, indexURL, descriptionTitle, filesManager);
            handler_db.start();

        } catch (org.jsoup.HttpStatusException | UnknownHostException | IllegalArgumentException e) {
            System.out.println(
                    "------------------------ Did not search for website ----------------------");
            return new HashMap[0];
        } catch (IOException e) {
            System.out.println(
                    "------------------------ Did not search for website -------------------------");
            // e.printStackTrace();
            return new HashMap[0];
        } catch (Exception e) {
            e.printStackTrace();
        }
        return database_changes_and_references_to_index;
    }

    // getting the url references form the page and putting them into the hashmap
    private HashMap<String, HashSet<String>> indexURLreferences(
            Elements links, String inputWebsite, HashMap refereceURL) {
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
        }
        return references_to_send;
    }

    // getting the words from the webpage and building the inverted index out of them
    private HashMap<String, HashSet<String>> indexWords(String text, String URL, HashMap indexURL) {
        HashMap<String, HashSet<String>> indexes_to_send = new HashMap<>();
        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        String line;

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


    public HashMap<String, ArrayList<String>> indexDescriptionsAndTitles(String description, String title, String inputLink, HashMap<String, ArrayList<String>> descriptionTitle){
        ArrayList<String> descTitle = new ArrayList<>();
        descTitle.add(title);
        descTitle.add(description);
        descriptionTitle.put(inputLink, descTitle);
        return descriptionTitle;
    }
}

// comparator to use when sorting the hashsets by integer value
class ReferencesComparator implements Comparator<Map.Entry<String, Integer>> {
    public int compare(Map.Entry<String, Integer> s1, Map.Entry<String, Integer> s2) {
        if (s1.getValue().equals(s2.getValue())) return 0;
        else if (s1.getValue() < s2.getValue()) return 1;
        else return -1;
    }
}
