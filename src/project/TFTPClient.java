package project;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class TFTPClient {
	private static final int default_port = 69;
	DatagramSocket socket;
	private Mode currentMode; // verbose or quite
	private InetAddress serverAddress;
	private int serverPort;
	private int responsePort;
	private String folder = System.getProperty("user.dir") + File.separator + "client_files" + File.separator;

	TFTPClient() throws UnknownHostException {
		this(InetAddress.getLocalHost(), default_port);
	}

	TFTPClient(InetAddress server, int port) {
		// default mode is quite
		this.currentMode = Mode.QUITE;
		this.serverAddress = server;
		this.serverPort = port;
	}

	private void printInformation(TFTPRequestPacket packet) throws IOException {
		switch (this.currentMode) {
		case QUITE: // don't print detailed information in QUITE mode
			return;
		case VERBOSE:
			System.out.println("Packet type: " + packet.type());
			System.out.println("Destination: ");
			System.out.println("IP address: " + packet.getAddress());
			System.out.println("Port: " + packet.getPort());
			System.out.println("Information in this packet: ");
			System.out.println("Filename: " + packet.getFilename());
			System.out.println("Mode: " + packet.getMode() + "\n");
			return;
		}
	}
	
	private void printInformation(TFTPDataPacket packet) throws IOException {
		switch (this.currentMode) {
		case QUITE: // don't print detailed information in QUITE mode
			return;
		case VERBOSE:
			System.out.println("Packet type: " + packet.type());
			System.out.println("Destination: ");
			System.out.println("IP address: " + packet.getAddress());
			System.out.println("Port: " + packet.getPort());
			System.out.println("Information in this packet: ");
			System.out.println("Block number: " + packet.getBlockNumber());
			System.out.println("Data length: " + packet.getLength() + "\n");
			return;
		}
	}
	
	private void printInformation(TFTPAckPacket packet) throws IOException {
		switch (this.currentMode) {
		case QUITE: // don't print detailed information in QUITE mode
			return;
		case VERBOSE:
			System.out.println("Packet type: " + packet.type());
			System.out.println("Destination: ");
			System.out.println("IP address: " + packet.getAddress());
			System.out.println("Port: " + packet.getPort());
			System.out.println("Information in this packet: ");
			System.out.println("Block number: " + packet.getBlockNumber() + "\n");
			return;
		}
	}

	private static void printMenu() {
		System.out.println("Available commands:");
		System.out.println("1. menu - show the menu");
		System.out.println("2. exit - stop the client");
		System.out.println("3. mode - show current mode");
		System.out.println("4. switch - switch print mode(verbose or quite)");
		System.out.println("5. read <filename> - send RRQ(i.e. read text.txt)");
		System.out.println("6. write <filename> - send WRQ(i.e. write text.txt)\n");
	}

	private void printMode() {
		System.out.println("Current mode is: " + currentMode.mode());
	}

	private void switchMode() {
		this.currentMode = currentMode.switchMode();
		System.out.println("The mode has been switched to " + this.currentMode + "\n");
	}

	private void stopClient() {
		System.out.println("Terminating client.");
	}

	private void waitForCommand() {
		Scanner s = new Scanner(System.in);

		printMenu();
		while (true) {
			System.out.print("Command: ");
			String cmdLine = s.nextLine().toLowerCase(); // convert all command into lower case
			String[] commands = cmdLine.split("\\s+"); // split all command into array of command
			if (commands.length == 0)
				continue;

			switch (commands[0]) {
			case "menu":
				printMenu();
				continue;
			case "exit":
				s.close(); // close the scanner
				stopClient();
				return;
			case "mode":
				printMode();
				continue;
			case "switch":
				switchMode();
				continue;
			case "read":
				if (commands.length != 2)
					System.out.println("Invalid request! Please enter a valid filename for "
							+ "read request(e.g. read text.txt)\n");
				readFileFromServer(commands[1]);
				continue;
			case "write":
				if (commands.length != 2)
					System.out.println("Invalid request! Please enter a valid filename for "
							+ "write request(e.g. write text.txt)\n");
				writeFileToServer(commands[1]);
				continue;
			default:
				System.out.println("Invalid command, please try again!\n");
			}
		}
	}

	private boolean createConnection() {
		try {
			socket = new DatagramSocket();
			return true;
		} catch (SocketException e) {
			return false;
		}
	}
	
	private String getFolder() {
		return folder;
	}
	
	private String getFilePath(String filename) {
		return getFolder() + filename;
	}
	
	private void sendRequest(DatagramPacket packet) throws IOException {
		socket.send(packet);
	}
	
	private TFTPDataPacket receiveDataPacket(int blockNumber) {
		try {
			DatagramPacket receivePacket = new DatagramPacket(new byte[TFTPDataPacket.MAX_LENGTH], TFTPDataPacket.MAX_LENGTH);
			socket.receive(receivePacket);
			this.responsePort = receivePacket.getPort();
			return TFTPDataPacket.createFromPacket(receivePacket);
		} catch (IOException e) {
			System.out.println("Client failed to receive the response. Please try again.\n");
			return null;
		}
	}
	
	private TFTPAckPacket receiveAckPacket(int blockNumber) {
		try {
			DatagramPacket receivePacket = new DatagramPacket(new byte[TFTPAckPacket.PACKET_LENGTH], TFTPAckPacket.PACKET_LENGTH);
			socket.receive(receivePacket);
			this.responsePort = receivePacket.getPort();
			return TFTPAckPacket.createFromPacket(receivePacket);
		} catch (IOException e) {
			System.out.println("Client failed to receive the response. Please try again.\n");
			return null;
		}
	}

	public void readFileFromServer(String filename) { // RRQ
		String filePath = getFilePath(filename);
		File file = null;
		try {
			file = new File(filePath);
			if (file.exists() && !file.canWrite()) {
				System.out.println("Client don't have permission to write " + filename + ". Please try again.\n");
				return;
			} else if (!file.exists()) { // create the file
				if (!file.createNewFile())
					throw new IOException("Failed to create " + filename);
			}

			FileOutputStream fs = new FileOutputStream(file);
			if (!createConnection()) { // socket create failed
				System.out.println("Client failed to create the socket, please check your network status and try again.\n");
				fs.close();
				return;
			}
			
			TFTPRequestPacket RRQPacket = TFTPRequestPacket.createReadRequest(filename, serverAddress, serverPort);
			sendRequest(RRQPacket.createDatagram());
			System.out.println("Client have sent the RRQ.");
			printInformation(RRQPacket);
			
			TFTPDataPacket DATAPacket;
			int blockNumber = 1;
			do {
				DATAPacket = receiveDataPacket(blockNumber);
				System.out.println("Client have received the data packet.");
				printInformation(DATAPacket);
				fs.write(DATAPacket.getFileData());
				TFTPAckPacket AckPacket = new TFTPAckPacket(blockNumber, serverAddress, responsePort);
				sendRequest(AckPacket.createDatagram());
				System.out.println("Client have sent the ack packet.");
				printInformation(AckPacket);
				++blockNumber;
			} while (!DATAPacket.isLastDataPacket());
			fs.close();
		} catch (IOException e) {
			file.delete();
			System.out.println("Client failed to send the request. Please try again.\n");
			return;
		}
	}

	public void writeFileToServer(String filename) { // WRQ
		String filePath = getFilePath(filename);
		File file = null;
		try {
			file = new File(filePath);
			if (!file.exists() || !file.canRead()) {
				System.out.println("Client don't have permission to read " + filename + ". Please try again.\n");
				return;
			}

			FileInputStream fs = new FileInputStream(filePath);
			if (!createConnection()) { // socket create failed
				System.out.println("Client failed to create the socket, please check your network status and try again.\n");
				fs.close();
				return;
			}
			
			TFTPRequestPacket WRQPacket = TFTPRequestPacket.createWriteRequest(filename, serverAddress, serverPort);
			sendRequest(WRQPacket.createDatagram());
			System.out.println("Client have sent the WRQ.");
			printInformation(WRQPacket);

			byte[] data = new byte[TFTPDataPacket.MAX_DATA_LENGTH];
			int byteUsed = 0;
			int blockNumber = 0;
			
			do {
				TFTPAckPacket AckPacket = receiveAckPacket(blockNumber++);
				System.out.println("Client have received the ack packet.");
				printInformation(AckPacket);
				byteUsed = fs.read(data);
				if (byteUsed == -1) {
					byteUsed = 0;
					data = new byte[0];
				}
				TFTPDataPacket DATAPacket = new TFTPDataPacket(blockNumber, Arrays.copyOfRange(data, 0, byteUsed), byteUsed, serverAddress, responsePort);
				sendRequest(DATAPacket.createDatagram());
				System.out.println("Client have sent the data packet.");
				printInformation(DATAPacket);

			} while (byteUsed == TFTPDataPacket.MAX_DATA_LENGTH);
			receiveAckPacket(blockNumber);
			fs.close();
		} catch (FileNotFoundException e) {
			System.out.println("Client failed to read " + filename + ". Please try again.\n");
			return;
		} catch (IOException e) {
			System.out.println("Client failed to send the request. Please try again.\n");
			return;
		}
	}

	public static void main(String[] args) throws UnknownHostException {
		TFTPClient client = new TFTPClient();
		client.waitForCommand();
	}
}
