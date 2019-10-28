import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/*
Class responsible for sending the isAlive messages at a regular interval of three seconds
 */
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
                System.out.println("Sem Internet");
            }
        }

    }
}
