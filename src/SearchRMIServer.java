import java.io.IOException;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;


public class SearchRMIServer extends UnicastRemoteObject implements ServerLibrary {
    private int numberRequest = 0;
    private ArrayList<User> listLogedUsers = new ArrayList<>();
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private Comunication comunication;

    // COMUNICACAO COM O MULTICAST (Enviar)
    private int PORTsend = 4321;
    MulticastSocket socketSend;

    public SearchRMIServer(Comunication comunication, int numberRequest) throws RemoteException {
        super();
        this.comunication = comunication;
        this.numberRequest = numberRequest;
        new MulticastThread(comunication);
        System.out.println("[CURRENT REQUEST NUMBER] - "+numberRequest);
    }

    private synchronized String sendToMulticast(String message){
        message = "id|"+this.numberRequest+";"+message;
        byte[] buffer = message.getBytes();

        try{
            socketSend = new MulticastSocket();
        } catch (IOException e){
            return "";
        }
        // VER A VARIAVEL DE PARAGEM


        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORTsend);
            socketSend.send(packet);
            socketSend.close();
            return  comunication.receiveAnswer();
        } catch (UnknownHostException e){
            return "";
        }catch (IOException e){
            return "";
        }
    }

    public String connected(ClientLibrary newUser) throws RemoteException {
        //System.out.println(newUser);
        System.out.println("[USER CONNECTED]");
        return "    --WELCOME--\n\nLogin - 1\nRegister -2\nSearch -3\nExit -4\n>>> ";
    }
    public boolean userRegistration(User newUser) throws RemoteException, UnknownHostException {
        String requestToMulticast =  "type|requestUSERRegist;" +
                "user|"+newUser.username+";" +
                "pass|"+newUser.password+"";
        // ifs para verificar
        System.out.println("[USER REGISTERED] - "+requestToMulticast);
        String answer  =sendToMulticast(requestToMulticast);

        return false;   //Um ifzinho para verificar se o username esta livre
    }
    public String userLogin(User newUser) throws RemoteException, InterruptedException {
        //Thread.sleep(5000);
        String requestToMulticast =  "type|requestUSERLogin;" +
                "user|"+newUser.username+";" +
                "pass|"+newUser.password+"";
        this.numberRequest++;

        System.out.println("[USER LOG IN] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast);

        return answer;
    }
    public String changeUserPrivileges(String username) throws RemoteException{
        String requestToMulticast ="type|requestChangeUSERPrivileges;" +
                "user|"+username;
        System.out.println(requestToMulticast);
        String answer = sendToMulticast(requestToMulticast);
        return username+"'s Privileges changed Successfully";//Ou user not found
    }
    public String getHistory(User thisUser) throws RemoteException{
        String requestToMulticast ="type|requestUSERhistory;" +
                "user|"+thisUser.username;
        System.out.println(requestToMulticast);
        String answer = sendToMulticast(requestToMulticast);
        return "Empty";
    }
    public String getReferencePages(String url) throws RemoteException{
        String requestToMulticast ="type|requestURLbyRef;" +
                "URL|"+url;
        String answer = sendToMulticast(requestToMulticast);
        return "List of referenced Pages -> "+requestToMulticast;
    }
    public void addURLbyADMIN(String url) throws RemoteException{
        String requestToMulticast ="type|requestaddURLbyADMIN;" +
                "URL|"+url;
        System.out.println("Admin added ULR -> "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast);
    }
    public String searchWords(String[] words) throws RemoteException{
        String requestToMulticast ="type|requestURLbyWord;" +
                "word_count|"+words.length;
        for (int i = 1; i <= words.length; i++) {
            requestToMulticast+= "word_"+ i+"|"+words[i-1]+";";
        }
        System.out.println("[USER SEARCH] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast);
        return "BAL BLA BLA";
    }
    public String getAllUsers() throws RemoteException{
        String requestToMulticast ="type|requestURLbyWord";
        System.out.println(requestToMulticast);
        String answer = sendToMulticast(requestToMulticast);
        return "Empty";
    }

    public String sendSystemInfo() throws RemoteException{
        String requestToMulticast ="type|requestSYSinfo";
        String answer = sendToMulticast(requestToMulticast);
        return "Not Done Yet";
    }
    //CHECK MAIN SERVER FUNCIONALITY
    public int checkMe() throws RemoteException{
        return this.numberRequest;
    }
    // MAIN
    public static void main(String[] args) throws RemoteException, NotBoundException {
        connection(0);


    }
    public static void connection(int numberRequest) throws RemoteException, NotBoundException {
        try {
            Registry r = LocateRegistry.createRegistry(1401);
            r.rebind("ucBusca", new SearchRMIServer(new Comunication(),numberRequest));
            System.out.println("Im the main Server\nRunning...");

        } catch (RemoteException re) {
            System.out.println("Im the Backup Server");
            failover(numberRequest);
        }
    }

    public static void failover(int numberRequest) throws RemoteException, NotBoundException {
        ServerLibrary checkMainServer;
        int faultCounter = 0;
        while (true){
            try {
                Thread.sleep(2000);
            } catch(InterruptedException e) {
                System.out.println("Interrupted");
            }
            try{
                checkMainServer = (ServerLibrary) LocateRegistry.getRegistry(1401).lookup("ucBusca");
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
        connection(numberRequest);
    }
}
class MulticastThread extends Thread {
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private int PORT = 4322;
    Comunication comunication;

    public MulticastThread(Comunication comunication){
        this.start();
        this.comunication=comunication;
    }

    public void run() {
        MulticastSocket aSocket = null;
        try {
            aSocket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            aSocket.joinGroup(group);
            while (true) {
                byte[] buffer = new byte[1000];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(packet);
                this.comunication.sendAnswerToRMI(new String(packet.getData(),0,packet.getLength()));
                System.out.println(
                        "Received packet from "
                                + packet.getAddress().getHostAddress()
                                + ":"
                                + packet.getPort()
                                + " with message:");
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println(message);
            } // while
        } catch (SocketException e) {
            System.out.println("Socket: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO: " + e.getMessage());
        } finally {
            if (aSocket != null) aSocket.close();
        }
    }

}
class Comunication {
    String sharedObj = "";
    boolean sendToTCPclient = false;

    synchronized String receiveAnswer() {

        while (!sendToTCPclient)
            try {
                wait();
            } catch (InterruptedException e) {
                System.out.println("interruptedException caught");
            }

        sendToTCPclient = false;
        notify();
        return this.sharedObj;
    }
    synchronized void sendAnswerToRMI(String sharedObj) {

        while (sendToTCPclient)
            try {
                wait();
            } catch (InterruptedException e) {
                System.out.println("interruptedException caught");
            }
        sendToTCPclient = true;
        this.sharedObj = sharedObj;
        notify();
    }
}
