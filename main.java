import java.io.*;
import java.net.*;
import java.util.Hashtable;
import java.util.Random;


class Sender{
    public static void main(String[] args) throws Exception {
        // Stores the most recent packets that were sent
        DatagramPacket[] packetStorage = new DatagramPacket[Receiver.rcvwindowsize];

        // Create local socket and establish receiver address
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName("129.8.147.33"); // Change to appropriate ip address
        for(int i = 0; i < Receiver.numOfPacketsToSend; i++) {

            // Creates UDP datagram and send packet to receiver
            byte[] sendData = new byte[1000];   // Packet size of 1000 bytes
            int id = i % (Receiver.rcvwindowsize);
            sendData[0] = (byte) id;
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9000);
            packetStorage[i % (Receiver.rcvwindowsize)] = sendPacket;

            // Get random number. If number is less than lossRate, then packet is not sent
            int ranNumber = java.lang.Math.abs(new Random().nextInt()) % 101;
            Thread.sleep(((int) (Receiver.sendingRate * 1000))); // Sleep for sendingRate time to simulate processing time
            if(ranNumber > Receiver.lossRate) {
                clientSocket.send(sendPacket);
            }

            if ((i % (Receiver.rcvwindowsize) == (Receiver.rcvwindowsize -1))) {
                // Initialize some variable what will be used
                byte[] ACK = new byte[(int) Receiver.sizeOfPackets];
                Hashtable<Integer, Boolean> droppedPackets = new Hashtable<Integer, Boolean>();
                Hashtable<Integer, Integer> lostPacketsRetries = new Hashtable<Integer, Integer>();

                // Open up the socket for TCP connection
                ServerSocket welcomeSocket = new ServerSocket(9090);

                // Check to see if packets were lost, if so, retransmit lost packets
                boolean packetsLost;
				int numOfPacketsDropped = 0;
                do {
                    // Accepts TCP connection from receiver and receives packet
                    Socket connectionSocket = welcomeSocket.accept();
                    DataInputStream inFromClient = new DataInputStream(connectionSocket.getInputStream());
                    inFromClient.readFully(ACK);

                    packetsLost = false;
                    // Checks to see which packets were dropped
                    int currentPacketBlock = i - (Receiver.rcvwindowsize - 1);
                    for (int j = 0; j < Receiver.rcvwindowsize; j++) {
                        // Packet was not received successfully
                        if (ACK[j] == 0 && !droppedPackets.containsKey(j)) {
                            int triesAttempted;
                            if(lostPacketsRetries.containsKey(j)) {
                                triesAttempted = lostPacketsRetries.get(j);
                            } else {
                                triesAttempted = 0;
                            }

                            if(triesAttempted > Receiver.numberOfRetries){
                                droppedPackets.put(j, true);
								numOfPacketsDropped++;
                                continue;
                            }

                            packetsLost = true;

                            // Creates UDP datagram and sends packet to receiver
                            sendPacket = packetStorage[j];
                            ranNumber = java.lang.Math.abs(new Random().nextInt()) % 101;
                            if(ranNumber > Receiver.lossRate) {
                                clientSocket.send(sendPacket);
                            }

                            triesAttempted++;
                            lostPacketsRetries.put(j, triesAttempted);
                        }
                    }
                } while (packetsLost);

				if (numOfPacketsDropped == 0){
					System.out.println("All packets sent successfully. Loss Rate is 0%");
				} else {
					int actualLossRate = ((100*numOfPacketsDropped) / Receiver.rcvwindowsize);
					System.out.println("Some packets were dropped. Loss Rate is: " + Integer.toString(actualLossRate) + "%");
				}
                
                welcomeSocket.close();
                // Alert sender that new set of data is being sent
                sendData[1] = 1;
                sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9000);
                clientSocket.send(sendPacket);

            }
        }

        // Alert sender that no more packets will be sent
        byte[] sendData = new byte[1000];
        sendData[1] = 2;
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9000);
        clientSocket.send(sendPacket);
        clientSocket.close();
    }
}


class Receiver{
    public static int numOfPacketsToSend = 10000;
    public static int rcvwindowsize = 50; // Must be 256 or smaller. Also called the recent packet threshold
    public static int lossRate = 20; // Percentage number
    public static int numberOfRetries = 3; //Number of times the packet is attempted to be sent before being dropped
    public static double sizeOfPackets = 1000; // The size of the packets sent
    public static double bytesPerSecond = 1000000; // The number of bytes processed by the sender in 1 second
    public static double sendingRate = (sizeOfPackets/bytesPerSecond); // Number of seconds to send 1 packet

    public static void main(String[] args) throws Exception {
        // Create socket for UDP connection
        DatagramSocket serverSocket = new DatagramSocket(9000);
        InetAddress IPAddress = null;
        int totalPacketsReceived = 0;

        // Initialize the rcvwindow
        int rcvwindow = Receiver.rcvwindowsize;

        // Data storage
        byte[] receiveData = new byte[(int) sizeOfPackets];
        byte[] sendData = new byte[(int) sizeOfPackets]; // This represents the bit array

        while(true)
        {
            try {
                // Receive the UDP packet
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                IPAddress = receivePacket.getAddress();

                // Set a timeout if no data enters the socket.
                serverSocket.setSoTimeout((4000 + ((int) (sendingRate * 1000))));

                // Grab the id from the packet and check it off in the sendData bit array
                receiveData = receivePacket.getData();

                // Check to see if there are no more expected packets
                if((receiveData[1] & (0xff)) == 2){
                    System.out.println("Done!");
                    rcvwindow = rcvwindowsize;
                    sendData = new byte[(int) sizeOfPackets];
                    System.out.println("*********************");

                    serverSocket.close();   // Close connection to remove timeout
                    serverSocket = new DatagramSocket(9000);    // Open connection to allow more packets to be sent
                    totalPacketsReceived = 0;

                    continue;
                }

                // Assign the data accordingly
                int id = (receiveData[0] & (0xff));
                int localId = id % (rcvwindowsize);
                rcvwindow--;
                totalPacketsReceived++;

                // Check to see if this is a new sequence of packets.
                // If so, then this is a new set of data. Clear the sendData
                if((receiveData[1] & (0xff)) == 1) {
                    System.out.println("Reset");
                    rcvwindow = rcvwindowsize;
                    sendData = new byte[(int) sizeOfPackets];
                    System.out.println("*********************");
                    continue;
                } else{
                    sendData[localId] = 1;
                }

                // If the last expected packet is received or the rcvwindow is full
                // Then respond to the sender with a TCP packet
                if (localId == (rcvwindowsize - 1) || rcvwindow == 0 || id == (numOfPacketsToSend - 1)) {
                    // Create socket for the TCP connection
                    Socket sendersSocket = new Socket(IPAddress, 9090);

                    // Send TCP packet
                    DataOutputStream outToServer = new DataOutputStream(sendersSocket.getOutputStream());
                    outToServer.write(sendData);
                }
            } catch (SocketTimeoutException s) {
                System.out.println("Socket timed out!");

                if(totalPacketsReceived != Receiver.numOfPacketsToSend) {
                    // Create socket for the TCP connection
                    Socket sendersSocket = new Socket(IPAddress, 9090);

                    // Send TCP packet
                    DataOutputStream outToServer = new DataOutputStream(sendersSocket.getOutputStream());
                    outToServer.write(sendData);
                }
            }
        }
    }
}