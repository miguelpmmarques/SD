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
    private String MULTICAST_ADDRESS = "224.0.224.3";
    private Comunication comunication;

    // COMUNICACAO COM O MULTICAST (Enviar)
    private int PORTsend = 6969;
    MulticastSocket socketSend;

    public SearchRMIServer(Comunication comunication, int numberRequest) throws RemoteException {
        super();
        this.comunication = comunication;
        this.numberRequest = numberRequest;
        new MulticastThread(comunication);
        System.out.println("[CURRENT REQUEST NUMBER] - "+numberRequest);
    }


    private synchronized String sendToMulticast(String message,int idPack) {
        int MAXNUMBEROFTIMEOUTS = 0;
        message = "id|"+idPack+";"+message;
        byte[] buffer = message.getBytes();
        int id =-1;

        System.out.println(message);


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
            }
            messageFromMulticast = comunication.receiveAnswer();
            String[] splitedsms = messageFromMulticast.split("\\;");
            String[] splitedsplitedsms = splitedsms[0].split("\\|");
            if (splitedsplitedsms[0].equals("id"))
            {
                id = Integer.parseInt(splitedsplitedsms[1]);
            }
            System.out.println("---------> "+messageFromMulticast);
            if (MAXNUMBEROFTIMEOUTS == 16){
                return "SERVERS ARE OFFLINE";
            }
            MAXNUMBEROFTIMEOUTS++;
        }while (messageFromMulticast.equals("") || id!=idPack);

        return messageFromMulticast;

    }
    public String connected(ClientLibrary newUser) throws RemoteException {
        //System.out.println(newUser);
        System.out.println("[USER CONNECTED]");
        return "    --WELCOME--\n\nLogin - 1\nRegister -2\nSearch -3\nExit -4\n>>> ";
    }
    public String userRegistration(User newUser) throws RemoteException, UnknownHostException { // DONE
        String requestToMulticast =  "id|"+this.numberRequest+";type|requestUSERRegist;" +
                "user|"+newUser.username+";" +
                "pass|"+newUser.password+"";
        // ifs para verificar
        System.out.println("[USER REGISTERED] - "+requestToMulticast);
        String answer  =sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;

        return answer;
    }
    public String userLogin(User newUser) { // DONE
        String requestToMulticast =  "type|requestUSERLogin;" + "user|"+newUser.username+";" + "pass|"+newUser.password+"";
        System.out.println("[USER LOG IN] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        char aux = answer.charAt(answer.length()-1);
        System.out.println("[LETRA] -> "+aux);
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
        this.numberRequest++;
        System.out.println("RESPOSTA -> "+answer);
        return answer;
    }
    public String getHistory(User thisUser) throws RemoteException{
        String requestToMulticast ="id|"+this.numberRequest+";type|requestUSERhistory;" + "user|"+thisUser.username;
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;
        return answer;
    }
    public String getReferencePages(String url) throws RemoteException{
        String requestToMulticast ="id|"+this.numberRequest+";type|requestURLbyRef;" + "URL|"+url;
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;
        return "List of referenced Pages -> "+requestToMulticast;
    }
    public String addURLbyADMIN(String url) throws RemoteException{
        String requestToMulticast ="id|"+this.numberRequest+";type|requestaddURLbyADMIN;" + "URL|"+url;
        System.out.println("Admin added ULR -> "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;
        return answer;
    }
    public String changeUserPrivileges(String username) throws RemoteException{
        String requestToMulticast ="id|"+this.numberRequest+";type|requestChangeUSERPrivileges;" +
                "user|"+username;
        System.out.println(requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;
        char aux = answer.charAt(answer.length()-1);
        if (aux == 's'){
            System.out.println("Esta parte ao menos funciona");
            for (int i=0;i<listLogedUsers.size();i++){
                System.out.println(">>>>><>>>"+listLogedUsers.get(i).username);
                if(listLogedUsers.get(i).username.equals(username)){
                    System.out.println(" -............... e chega aqui");
                    try{
                        listLogedUsers.get(i).client.notification("YOU'RE A ADMIN NOW");
                    } catch (Exception e){
                        this.numberRequest++;
                        System.out.println(sendToMulticast("type|requestSetNotify;user|"+username,this.numberRequest));
                    }
                    finally {
                        return answer;
                    }
                }
            }
            this.numberRequest++;
            System.out.println(sendToMulticast("type|requestSetNotify;user|"+username,this.numberRequest));

        }
        return answer;
    }
    public String searchWords(String[] words) throws RemoteException{
        System.out.println();
        String requestToMulticast ="id|"+this.numberRequest+";type|requestURLbyWord;" +
                "user|"+words[0]+
                ";word_count|"+(words.length-1)+";";
        for (int i = 1; i <= words.length-1; i++) {
            requestToMulticast+= "word_"+ i+"|"+words[i]+";";
        }
        System.out.println("[USER SEARCH] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;
        return answer;
    }
    public String getAllUsers() throws RemoteException{
        String requestToMulticast ="id|"+this.numberRequest+";type|requestAllUSERSPrivileges";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        this.numberRequest++;
        return answer;
    }
    public String sendSystemInfo() throws RemoteException{
        String requestToMulticast ="id|"+this.numberRequest+";type|requestSYSinfo";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest);
        System.out.println(answer);
        this.numberRequest++;
        return answer;
    }
    //CHECK MAIN SERVER FUNCIONALITY
    public int checkMe() throws RemoteException{
        return this.numberRequest;
    }
    // MAIN
    public static void main(String[] args) throws RemoteException {
        connection(0);


    }
    public static void connection(int numberRequest) throws RemoteException {
        try {
            Registry r = LocateRegistry.createRegistry(1401);
            r.rebind("ucBusca", new SearchRMIServer(new Comunication(),numberRequest));
            System.out.println("Im the main Server\nRunning...");

        } catch (RemoteException re) {
            System.out.println("Im the Backup Server");
            failover(numberRequest);
        }
    }

    public static void failover(int numberRequest) throws RemoteException {
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
    private String MULTICAST_ADDRESS = "224.0.224.3";
    private int PORT = 9696;
    private int PORTsend = 6969;
    Comunication comunication;

    public MulticastThread(Comunication comunication){
        this.start();
        this.comunication=comunication;
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
                requestId  = this.comunication.sendAnswerToRMI(getSms);
                String message = "ACK|"+requestId+";";
                buffer = message.getBytes();
                packet = new DatagramPacket(buffer, buffer.length, group, PORTsend);
                aSocket.send(packet);
            }
        } catch (SocketException e) {
            System.out.println("Socket: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("ALL: " + e.getMessage());
        } finally {
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
            try {
                wait(5000);

                if (!sendToTCPclient){
                    System.out.println("[TIME OUT]");
                    return "";
                }

                System.out.println("TIMEOUT");
            } catch (InterruptedException e) {
                System.out.println("interruptedException caught");
            }


        sendToTCPclient = false;
        return this.sharedObj;
    }

    synchronized int sendAnswerToRMI(String sharedObj) {
        sendToTCPclient = true;
        this.sharedObj = sharedObj;
        String aux =  sharedObj.split("\\;")[0];
        System.out.println(aux);
        int requestId = Integer.parseInt(aux.split("\\|")[1]);
        System.out.println("NOTIFICOU");
        notify();
        return requestId;
    }
}
