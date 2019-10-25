import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class SearchRMIServer extends UnicastRemoteObject implements ServerLibrary {

    private AtomicInteger numberRequest;
    private ArrayList<User> listLogedUsers = new ArrayList<>();
    private String MULTICAST_ADDRESS;
    private Comunication comunication;

    private int PORTsend;
    MulticastSocket socketSend;

    public SearchRMIServer(Comunication comunication, int numberRequest,Properties prop) throws RemoteException {
        super();
        this.comunication = comunication;
        this.numberRequest = new AtomicInteger(numberRequest);
        this.MULTICAST_ADDRESS = prop.getProperty("MULTICAST_ADDRESS");
        this.PORTsend = Integer.parseInt(prop.getProperty("MULTICAST_PORT"));
        new MulticastThread(comunication,MULTICAST_ADDRESS,PORTsend);
        System.out.println("[CURRENT REQUEST NUMBER] - "+numberRequest);
    }


    private synchronized String sendToMulticast(String message,int idPack) {
        int MAXNUMBEROFTIMEOUTS = 0;
        message = "idRMI|"+idPack+";"+message;
        byte[] buffer = message.getBytes();
        int id =-1;
        String messageFromMulticast="";
        do{
            try{
                socketSend = new MulticastSocket();
            } catch (IOException e){
                return "";
            }
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORTsend);
                socketSend.send(packet);
                socketSend.close();
            } catch (IOException e){
                socketSend.close();
                return "";
            }
            messageFromMulticast = comunication.receiveAnswer();
            String[] splitedsms = messageFromMulticast.split("\\;");
            String[] splitedsplitedsms = splitedsms[0].split("\\|");
            System.out.println("Esta por auqi em loop???");
            System.out.println(splitedsplitedsms[0]);
            if (splitedsplitedsms[0].equals("id"))
            {
                id = Integer.parseInt(splitedsplitedsms[1]);
                System.out.println("id - "+id);
                System.out.println("idPack - "+idPack);
            }
            if (MAXNUMBEROFTIMEOUTS == 16){
                return "SERVERS ARE OFFLINE";
            }
            MAXNUMBEROFTIMEOUTS++;
            System.out.println("message - "+messageFromMulticast);

        }while (messageFromMulticast.equals("") || id!=idPack);
        System.out.println("BAZOUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUUU");
        return messageFromMulticast;
    }
    public String connected(ClientLibrary newUser) throws RemoteException {
        //System.out.println(newUser);
        System.out.println("[USER CONNECTED]");
        return "    --WELCOME--\n\nLogin - 1\nRegister -2\nSearch -3\nExit -4\n>>> ";
    }
    public String userRegistration(User newUser) throws RemoteException, UnknownHostException { // DONE
        String requestToMulticast =  "type|requestUSERRegist;" +
                "user|"+newUser.username+";" +
                "pass|"+newUser.password+"";
        // ifs para verificar
        System.out.println("[USER REGISTERED] - "+requestToMulticast);
        String answer  =sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    public String userLogin(User newUser) { // DONE
        String requestToMulticast =  "type|requestUSERLogin;" + "user|"+newUser.username+";" + "pass|"+newUser.password+"";
        System.out.println("[USER LOG IN] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        char aux = answer.charAt(answer.length()-1);
        if (aux== 'e'){
            System.out.println(newUser.client);
            try {
                newUser.client.notification("YOU'RE A ADMIN NOW");
            } catch (Exception e){
                System.out.println("nao perdebo este erro porque funciona");
            }

        }
        newUser.setThis((ClientLibrary)newUser.client);
        listLogedUsers.add(newUser);
        System.out.println("RESPOSTA -> "+answer);
        return answer;
    }
    public String getHistory(User thisUser) throws RemoteException{
        String requestToMulticast ="type|requestUSERhistory;" + "user|"+thisUser.username;
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    public String getReferencePages(String url) throws RemoteException{
        String requestToMulticast ="type|requestURLbyRef;" + "URL|"+url;
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        System.out.println("REturn -> "+answer);
        return "List of referenced Pages -> "+answer;
    }
    public String addURLbyADMIN(String url) throws RemoteException{
        String requestToMulticast ="type|getMulticastList;" + "URL|"+url;
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        System.out.println("LISTAAAAAAAAAAAAAAAAA -- "+answer);
        String[] chooseMulticast = answer.split("\\;");
        String choosenMulticast = chooseMulticast[1];
        System.out.println("EScolha -------> "+choosenMulticast);
        requestToMulticast ="type|requestaddURLbyADMIN;" + "URL|"+url+";MulticastId|"+choosenMulticast;
        System.out.println("Admin added ULR -> "+requestToMulticast);
        answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    public String changeUserPrivileges(String username) throws RemoteException{
        String requestToMulticast ="type|requestChangeUSERPrivileges;" +
                "user|"+username;
        System.out.println(requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        char aux = answer.charAt(answer.length()-1);
        if (aux == 's'){
            for (int i=0;i<listLogedUsers.size();i++){
                if(listLogedUsers.get(i).username.equals(username)){
                    System.out.println(" -............... e chega aqui");
                    try{
                        listLogedUsers.get(i).client.notification("YOU'RE A ADMIN NOW");
                    } catch (Exception e){
                        System.out.println(sendToMulticast("type|requestSetNotify;user|"+username,this.numberRequest.incrementAndGet()));
                    }
                    finally {
                        return answer;
                    }
                }
            }
            System.out.println(sendToMulticast("type|requestSetNotify;user|"+username,this.numberRequest.incrementAndGet()));

        }
        return answer;
    }
    public String searchWords(String[] words) throws RemoteException{
        System.out.println();
        String requestToMulticast ="type|requestURLbyWord;" +
                "user|"+words[0]+
                ";word_count|"+(words.length-1)+";";
        for (int i = 1; i <= words.length-1; i++) {
            requestToMulticast+= "word_"+ i+"|"+words[i]+";";
        }
        System.out.println("[USER SEARCH] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    public String getAllUsers() throws RemoteException{
        String requestToMulticast ="type|requestAllUSERSPrivileges";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    public String sendSystemInfo() throws RemoteException{
        String requestToMulticast ="type|requestSYSinfo";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        System.out.println(answer);
        return answer;
    }
    //CHECK MAIN SERVER FUNCIONALITY
    public int checkMe() throws RemoteException{
        return this.numberRequest.intValue();
    }
    // MAIN
    public static void main(String[] args) throws RemoteException {
        String propFileName = "config.properties";
        InputStream inputStream = MulticastServer.class.getClassLoader().getResourceAsStream(propFileName);
        Properties prop = new Properties();
        try {
            prop.load(inputStream);

        } catch (Exception e){
            System.out.println("Cannot read properties File");
            return;
        }
        connection(0,prop);
    }
    public static void connection(int numberRequest,Properties prop) throws RemoteException {
        try {
            Registry r = LocateRegistry.createRegistry(Integer.parseInt(prop.getProperty("REGISTRYPORT")));
            r.rebind(prop.getProperty("LOOKUP"), new SearchRMIServer(new Comunication(),numberRequest,prop));
            System.out.println("Im the main Server\nRunning...");

        } catch (RemoteException re) {
            System.out.println("Im the Backup Server");
            failover(numberRequest,prop);
        }
    }

    public static void failover(int numberRequest,Properties prop) throws RemoteException {
        ServerLibrary checkMainServer;
        int faultCounter = 0;
        while (true){
            try {
                Thread.sleep(2000);
            } catch(InterruptedException e) {
                System.out.println("Interrupted");
            }
            try{

                checkMainServer = (ServerLibrary) LocateRegistry.getRegistry(Integer.parseInt(prop.getProperty("REGISTRYPORT"))).lookup(prop.getProperty("LOOKUP"));
                numberRequest = checkMainServer.checkMe();
                System.out.println("[WORKIN]");
                faultCounter = 0;
            }catch (Exception e) {
                System.out.println("[FAULT]");
                faultCounter++;
            }
            if (faultCounter==5)
                break;
        }
        connection(numberRequest,prop);
    }
}
class MulticastThread extends Thread {
    private String MULTICAST_ADDRESS;
    private int PORT;
    Comunication comunication;

    public MulticastThread(Comunication comunication,String MULTICAST_ADDRESS,int PORT){
        this.start();
        this.comunication=comunication;
        this.MULTICAST_ADDRESS = MULTICAST_ADDRESS;
        this.PORT = PORT;
    }

    public void run() {
        MulticastSocket aSocket = null;
        String getSms;
        int requestId;
        try {
            aSocket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            aSocket.joinGroup(group);
            while (true) {
                byte[] buffer = new byte[5000];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(packet);
                getSms = new String(packet.getData(),0,packet.getLength());
                System.out.println("REECEBEU NO PORTO -> "+getSms);
                if (getSms.substring(0,5).equals("idRMI") ||getSms.substring(0,3).equals("ACK") )
                {
                    System.out.println("AutoEnviou-se");
                    continue;
                }
                requestId  = this.comunication.sendAnswerToRMI(getSms);
                if (requestId != -1){
                    String message = "ACK|"+requestId+";";
                    buffer = message.getBytes();
                    packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                    aSocket.send(packet);
                }
            }
        } catch (IOException e) {
            System.out.println("Socket: " + e.getMessage());
        }finally {
            System.out.println("NAOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO");
            if (aSocket != null) aSocket.close();
        }
    }

}
class Comunication {
    String sharedObj = "";
    boolean sendToTCPclient = false;

    synchronized String receiveAnswer() {

        while (!sendToTCPclient)
        {
            try {
                wait(5000);
            } catch (InterruptedException e) {
                System.out.println("interruptedException caught");
                sendToTCPclient = false;
            }

            System.out.println("LOL Soltou-se");
            String aux =  this.sharedObj.split("\\;")[0];
            String[] id = aux.split("\\|");
            if (!sendToTCPclient){
                System.out.println("[TIME OUT]");
                sendToTCPclient = false;
                return "";
            }
            if (id[0].equals("idRMI")){
                sendToTCPclient = false;
                continue;
            }


        }



        sendToTCPclient = false;
        return this.sharedObj;
    }

    synchronized int sendAnswerToRMI(String sharedObj) {
        this.sharedObj = sharedObj;
        String aux =  sharedObj.split("\\;")[0];
        String[] id = aux.split("\\|");
        try {
            int requestId = Integer.parseInt(id[1]);
            if (id[0].equals("id")){
                System.out.println("NOTIFICOU");
                sendToTCPclient = true;
                notifyAll();
                return requestId;

            }
        } catch (NumberFormatException e){
            return -1;
        }
        return -1;

    }
}
