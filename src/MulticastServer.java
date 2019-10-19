import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


public class MulticastServer extends Thread {
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private int PORT = 4321;
    private long SLEEP_TIME = 5000;

    public static void main(String[] args) {
        MulticastServer server = new MulticastServer();
        server.start();
    }

    public MulticastServer() {
        super("Server " + (long) (Math.random() * 1000));
    }

    public void run() {
        listenMulticast();
    }
    public void listenMulticast(){
        MulticastSocket socket = null;
        long counter = 0;
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
                counter++;
                String message = new String(received_packet.getData(), 0, received_packet.getLength());
                System.out.println(">>>>>>>" +message);
                if (! message.equals("ACK")){
                    HandleRequest handle = new HandleRequest(counter, message);
                    handle.start();
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

}
class HandleRequest extends Thread{
    static final String INDEXFILE = "indexURL.tmp";
    static final String REFERENCEFILE = "referenceURL.tmp";
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private String request;
    public HandleRequest(long id, String request){
        super(String.format("Thread %d", id));
        this.request = request;
    }
    public void run(){
        System.out.println("I'm " + this.getName());
        sendAcknowledge();
    }
    public int sendAcknowledge(){
        int ack_port = 4322;
        MulticastSocket socket = null;
        System.out.println(this.getName() + " ready...");
        try {
            socket = new MulticastSocket();  // create socket without binding it (only for sending)
            // might have to change acknowledge configuration later, but for now its good enough
            String acknowledge = "ACK";
            byte[] buffer = acknowledge.getBytes();
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, ack_port);
            System.out.println("Sent acknowledge");
            socket.send(packet);
            String[] args = new String[1];
            args[0] = this.request;
            crawl(args);
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        } finally {
            socket.close();
            return 1;
        }
    }
    public void crawl(String[] args) throws IOException {
        HashMap<String, HashSet<String> > refereceURL = new HashMap<>();
        HashMap<String, HashSet<String> > indexURL = new HashMap<>();

        loadDataBase(refereceURL,indexURL);
        System.out.println(indexURL);
        System.out.println(refereceURL);
        try {
            String inputLink = args[0];
            if (! inputLink.startsWith("http://") || ! inputLink.startsWith("https://"))
                inputLink = "http://".concat(inputLink);

            // Attempt to connect and get the document
            Document doc = Jsoup.connect(inputLink).get();  // Documentation: https://jsoup.org/

            // Title
            System.out.println("WEB SITE TITLE > "+doc.title() + "\n");

            // Get all links
            Elements links = doc.select("a[href]");
            indexURLreferences(links,inputLink,refereceURL);

            String text = doc.text();
            indexWords(text,inputLink,indexURL);

            System.out.println("URL REFERENCE FROM "+doc.title()+"\n"+refereceURL);
            System.out.println("WORDS FOUND IN "+doc.title()+"\n"+indexURL);
            saveDataBase(refereceURL,indexURL);
        } catch (IOException e) {
            System.out.println("Did not searched for website");
        }
    }
    private static void saveDataBase( HashMap<String, HashSet<String> >refereceURL, HashMap<String, HashSet<String> >indexURL) throws IOException {
        FileOutputStream fIndex = new FileOutputStream(INDEXFILE);
        FileOutputStream frefence = new FileOutputStream(REFERENCEFILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(fIndex))
        {
            oos.writeObject(indexURL);
            System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> INDEX");
            oos.close();
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(frefence))
        {
            oos.writeObject(refereceURL);
            System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> REFERENCE");
            oos.close();
        }catch (FileNotFoundException ex)
        {
            System.out.println("Ficheiro nao encontrado");
        } catch (IOException ex)
        {
            System.out.println("Erro a escrever para o ficheiro.");
            ex.printStackTrace();
        }


    }
    private static void runtMap(HashMap myhash,HashMap hashFile) {
        Iterator it = hashFile.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            myhash.put(pair.getKey(), pair.getValue());
            it.remove(); // avoids a ConcurrentModificationException
        }
    }

    private static void loadDataBase( HashMap<String, HashSet<String> >refereceURL, HashMap<String, HashSet<String> >indexURL){
        File fIndex = new File(INDEXFILE);
        File frefence = new File(REFERENCEFILE);
        FileInputStream fis;
        ObjectInputStream ois;
        try {
            fis = new FileInputStream(frefence);
            ois = new ObjectInputStream(fis);
            HashMap<String, HashSet<String> >refereceURLaux = (HashMap)ois.readObject();
            runtMap(refereceURL,refereceURLaux);
            fis = new FileInputStream(fIndex);
            ois = new ObjectInputStream(fis);
            HashMap<String, HashSet<String> >indexURLaux = (HashMap)ois.readObject();
            runtMap(indexURL,indexURLaux);

        }catch (FileNotFoundException ex) {
            System.out.println("Rip");
            refereceURL = new HashMap<>();
            indexURL = new HashMap<>();
        }catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void indexURLreferences(Elements links,String inputWebsite,HashMap refereceURL){
        for (Element link : links) {
            String linkfound = link.attr("abs:href");
            HashSet aux = (HashSet)refereceURL.get(linkfound);
            if (aux== null){
                aux =  new HashSet<String>();
            }
            aux.add(inputWebsite);
            refereceURL.put(linkfound,aux);
        }
    }

    private static void indexWords(String text,String URL,HashMap indexURL) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        String line;

        // Get words and respective count
        while (true) {
            try {
                if ((line = reader.readLine()) == null)
                    break;
                String[] words = line.split("[ ,;:.?!“”(){}\\[\\]<>']+");
                for (String word : words) {
                    word = word.toLowerCase();
                    if ("".equals(word)) {
                        continue;
                    }
                    HashSet aux =(HashSet)indexURL.get(word);
                    if (aux== null){
                        aux =  new HashSet<String>();
                    }
                    aux.add(URL);
                    indexURL.put(word,aux);
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
    }

}

